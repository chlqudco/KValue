/*
 * 사용자 입력, 자동완성, 종목 조회를 조정하는 화면 상태 관리자다.
 * Repository 결과를 단일 StateFlow<StockUiState>로 외부에 제공한다.
 * Job 취소와 증가하는 요청 ID를 함께 사용해 느리게 도착한 이전 응답이 최신 화면을 덮지 못하게 한다.
 */
package com.chlqudco.kvalue.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.common.StockCodeValidation
import com.chlqudco.kvalue.common.StockCodeValidator
import com.chlqudco.kvalue.data.StockAnalysisResult
import com.chlqudco.kvalue.data.StockCatalogResult
import com.chlqudco.kvalue.data.StockRepository
import com.chlqudco.kvalue.data.StockSearchResult
import com.chlqudco.kvalue.domain.HistoricalForecastCalculator
import com.chlqudco.kvalue.domain.StockSearchMatcher
import com.chlqudco.kvalue.domain.SupportResistanceCalculator
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StockViewModel(
    private val repository: StockRepository
) : ViewModel() {
    // MutableStateFlow는 ViewModel 내부에서만 수정하고, 화면에는 읽기 전용 StateFlow만 노출한다.
    private val _uiState = MutableStateFlow(StockUiState())
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()

    // Job은 실제 코루틴을 취소하고, 요청 ID는 취소 직전에 이미 도착한 오래된 결과까지 걸러내는 이중 안전장치다.
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var latestRequestId = 0L
    private var latestSuggestionRequestId = 0L

    // 화면이 만들어지면 검색 전에 OpenDART 상장 종목 목록을 백그라운드에서 준비한다.
    init {
        viewModelScope.launch {
            val result = try {
                repository.preloadStockCatalog()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                StockCatalogResult.Failure(AppError.Unknown)
            }
            _uiState.update {
                it.copy(
                    catalog = when (result) {
                        is StockCatalogResult.Success -> {
                            StockCatalogState.Ready(result.stockCount)
                        }
                        is StockCatalogResult.Failure -> StockCatalogState.Error(result.error)
                    }
                )
            }
        }
    }

    /*
     * 글자가 바뀔 때마다 이전 자동완성 작업을 취소하고 250ms 뒤 새 검색을 실행한다.
     * 사용자가 계속 입력하는 동안은 네트워크·캐시 검색을 반복하지 않고 마지막 문자열만 처리한다.
     */
    fun onQueryChanged(value: String) {
        suggestionJob?.cancel()
        val requestId = ++latestSuggestionRequestId
        val query = value.trim()
        _uiState.update {
            it.copy(
                query = value,
                queryError = null,
                suggestions = if (query.isEmpty()) {
                    StockSuggestionState.Hidden
                } else {
                    StockSuggestionState.Loading
                }
            )
        }
        if (query.isEmpty()) return
        suggestionJob = viewModelScope.launch {
            delay(SUGGESTION_DEBOUNCE_MILLIS)
            val result = loadSuggestions(query)
            if (requestId != latestSuggestionRequestId) return@launch
            if (_uiState.value.query.trim() != query) return@launch
            applySuggestionResult(result)
        }
    }

    /*
     * 조회 버튼 이벤트다. 6자리 숫자는 즉시 조회하고 이름은 현재 후보의 정확 일치 또는 추가 검색으로 해석한다.
     * 이름을 임의의 첫 후보로 선택하지 않아 비슷한 회사명의 잘못된 종목 조회를 막는다.
     */
    fun search() {
        val query = _uiState.value.query.trim()
        when (val validation = StockCodeValidator.validate(query)) {
            StockCodeValidation.Empty -> {
                _uiState.update { it.copy(queryError = QueryInputError.EMPTY) }
            }
            StockCodeValidation.InvalidFormat -> {
                val exactMatch = currentSuggestions().firstOrNull {
                    StockSearchMatcher.isExactName(it, query)
                }
                if (exactMatch == null) {
                    resolveNameAndSearch(query)
                } else {
                    onSuggestionSelected(exactMatch)
                }
            }
            is StockCodeValidation.Valid -> {
                suggestionJob?.cancel()
                latestSuggestionRequestId++
                startSearch(
                    stockCode = validation.code,
                    submittedQuery = query,
                    forceRefresh = false
                )
            }
        }
    }

    // 사용자가 자동완성 행을 직접 선택했으므로 회사명과 연결된 정확한 종목코드로 조회한다.
    fun onSuggestionSelected(suggestion: StockSearchSuggestion) {
        suggestionJob?.cancel()
        latestSuggestionRequestId++
        startSearch(
            stockCode = suggestion.stockCode,
            submittedQuery = suggestion.companyName,
            forceRefresh = false
        )
    }

    // 현재 성공·오류 상태의 종목코드를 재사용하고 forceRefresh로 Repository 캐시를 우회한다.
    fun refresh() {
        val content = _uiState.value.content
        val stockCode = when (content) {
            is StockContentState.Success -> content.analysis.stockCode
            is StockContentState.Error -> content.stockCode
            else -> return
        }
        startSearch(
            stockCode = stockCode,
            submittedQuery = _uiState.value.query,
            forceRefresh = true
        )
    }

    /*
     * 모든 조회 진입점이 공유하는 핵심 상태 전환이다.
     * 동일 종목 로딩 중복을 막고 이전 Job을 취소한 뒤 입력·계산 결과를 초기화하고 Loading을 먼저 발행한다.
     */
    private fun startSearch(
        stockCode: String,
        submittedQuery: String,
        forceRefresh: Boolean
    ) {
        val loading = _uiState.value.content as? StockContentState.Loading
        if (loading?.stockCode == stockCode) {
            _uiState.update {
                it.copy(
                    query = submittedQuery,
                    queryError = null,
                    suggestions = StockSuggestionState.Hidden
                )
            }
            return
        }
        searchJob?.cancel()
        val requestId = ++latestRequestId
        _uiState.update {
            it.copy(
                query = submittedQuery,
                queryError = null,
                suggestions = StockSuggestionState.Hidden,
                content = StockContentState.Loading(stockCode, submittedQuery)
            )
        }
        searchJob = viewModelScope.launch {
            val result = try {
                repository.getStockAnalysis(stockCode, forceRefresh)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                StockAnalysisResult.Failure(AppError.Unknown)
            }
            // 취소 여부와 별개로 최신 요청 번호가 아니면 응답을 폐기한다.
            if (requestId != latestRequestId) return@launch
            when (result) {
                is StockAnalysisResult.Failure -> {
                    _uiState.update {
                        it.copy(content = StockContentState.Error(stockCode, result.error))
                    }
                }
                is StockAnalysisResult.Success -> applyAnalysis(result)
            }
        }
    }

    // Repository 성공 결과를 한 번의 update로 발행한다.
    private suspend fun applyAnalysis(result: StockAnalysisResult.Success) {
        val calculations = withContext(Dispatchers.Default) {
            Pair(
                HistoricalForecastCalculator.calculate(
                    history = result.analysis.forecastHistory,
                    currentPrice = result.analysis.price.currentPrice
                ),
                SupportResistanceCalculator.calculate(
                    priceHistory = result.analysis.priceHistory,
                    currentPrice = result.analysis.price.currentPrice
                )
            )
        }
        _uiState.update {
            it.copy(
                content = StockContentState.Success(
                    analysis = result.analysis,
                    forecast = calculations.first,
                    supportResistance = calculations.second
                )
            )
        }
    }

    /*
     * 입력한 회사명을 최신 종목 카탈로그에서 다시 찾는다.
     * 정확히 하나의 이름 일치가 없으면 사용자가 추천 목록에서 명시적으로 고르도록 오류 상태를 남긴다.
     */
    private fun resolveNameAndSearch(query: String) {
        if (query.isEmpty()) {
            _uiState.update { it.copy(queryError = QueryInputError.EMPTY) }
            return
        }
        suggestionJob?.cancel()
        val requestId = ++latestSuggestionRequestId
        _uiState.update {
            it.copy(queryError = null, suggestions = StockSuggestionState.Loading)
        }
        suggestionJob = viewModelScope.launch {
            val result = loadSuggestions(query)
            if (requestId != latestSuggestionRequestId) return@launch
            when (result) {
                is StockSearchResult.Failure -> applySuggestionResult(result)
                is StockSearchResult.Success -> {
                    val exactMatch = result.suggestions.firstOrNull {
                        StockSearchMatcher.isExactName(it, query)
                    }
                    if (exactMatch != null) {
                        onSuggestionSelected(exactMatch)
                    } else {
                        _uiState.update {
                            it.copy(
                                suggestions = result.toSuggestionState(),
                                queryError = if (result.suggestions.isEmpty()) {
                                    QueryInputError.NO_MATCH
                                } else {
                                    QueryInputError.SELECT_SUGGESTION
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Repository 구현이 예외를 던져도 ViewModel 바깥으로 전파하지 않되 코루틴 취소만은 보존한다.
    private suspend fun loadSuggestions(query: String): StockSearchResult = try {
        repository.searchStocks(query, SUGGESTION_LIMIT)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        StockSearchResult.Failure(AppError.Unknown)
    }

    private fun applySuggestionResult(result: StockSearchResult) {
        _uiState.update {
            it.copy(
                suggestions = when (result) {
                    is StockSearchResult.Failure -> StockSuggestionState.Error(result.error)
                    is StockSearchResult.Success -> result.toSuggestionState()
                }
            )
        }
    }

    private fun StockSearchResult.Success.toSuggestionState(): StockSuggestionState =
        if (suggestions.isEmpty()) {
            StockSuggestionState.NoResults
        } else {
            StockSuggestionState.Results(suggestions)
        }

    private fun currentSuggestions(): List<StockSearchSuggestion> =
        (_uiState.value.suggestions as? StockSuggestionState.Results)
            ?.suggestions
            .orEmpty()

    companion object {
        private const val SUGGESTION_LIMIT = 8
        private const val SUGGESTION_DEBOUNCE_MILLIS = 250L

        // 기본 생성자가 아닌 Repository를 주입하면서 Android ViewModel 생성 규약을 만족시키는 팩토리다.
        fun factory(repository: StockRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(StockViewModel::class.java))
                    return StockViewModel(repository) as T
                }
            }
    }
}
