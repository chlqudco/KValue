package com.chlqudco.kvalue.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.common.AppLogger
import com.chlqudco.kvalue.common.StockCodeValidation
import com.chlqudco.kvalue.common.StockCodeValidator
import com.chlqudco.kvalue.data.StockAnalysisResult
import com.chlqudco.kvalue.data.StockCatalogResult
import com.chlqudco.kvalue.data.StockRepository
import com.chlqudco.kvalue.data.StockSearchResult
import com.chlqudco.kvalue.domain.ComprehensiveAnalysisCalculator
import com.chlqudco.kvalue.domain.PerReferenceCalculator
import com.chlqudco.kvalue.domain.PerAssumptionsValidator
import com.chlqudco.kvalue.domain.PerValidationResult
import com.chlqudco.kvalue.domain.SrimValueCalculator
import com.chlqudco.kvalue.domain.StockSearchMatcher
import com.chlqudco.kvalue.domain.model.PerAssumptions
import com.chlqudco.kvalue.domain.model.SrimAssumptions
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
import com.chlqudco.kvalue.domain.model.SupportReason
import com.chlqudco.kvalue.domain.model.SupportStatus
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StockViewModel(
    private val repository: StockRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(StockUiState())
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var latestRequestId = 0L
    private var latestSuggestionRequestId = 0L

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

    fun onSuggestionSelected(suggestion: StockSearchSuggestion) {
        suggestionJob?.cancel()
        latestSuggestionRequestId++
        startSearch(
            stockCode = suggestion.stockCode,
            submittedQuery = suggestion.companyName,
            forceRefresh = false
        )
    }

    fun refresh() {
        val content = _uiState.value.content
        val stockCode = when (content) {
            is StockContentState.Success -> content.analysis.stockCode
            is StockContentState.Unsupported -> content.analysis.stockCode
            is StockContentState.Error -> content.stockCode
            else -> return
        }
        startSearch(
            stockCode = stockCode,
            submittedQuery = _uiState.value.query,
            forceRefresh = true
        )
    }

    fun onPerChanged(scenario: PerScenario, value: String) {
        val current = _uiState.value
        val content = current.content as? StockContentState.Success ?: return
        val inputs = when (scenario) {
            PerScenario.CONSERVATIVE -> current.perInputs.copy(conservative = value)
            PerScenario.BASE -> current.perInputs.copy(base = value)
            PerScenario.OPTIMISTIC -> current.perInputs.copy(optimistic = value)
        }
        when (
            val validation = PerAssumptionsValidator.validate(
                inputs.conservative,
                inputs.base,
                inputs.optimistic
            )
        ) {
            PerValidationResult.InvalidValue -> {
                AppLogger.referenceValueInputRejected("per", "value")
                _uiState.update {
                    it.copy(perInputs = inputs, perInputError = PerInputError.INVALID_VALUE)
                }
            }
            PerValidationResult.InvalidOrder -> {
                AppLogger.referenceValueInputRejected("per", "order")
                _uiState.update {
                    it.copy(perInputs = inputs, perInputError = PerInputError.INVALID_ORDER)
                }
            }
            is PerValidationResult.Valid -> {
                val eps = content.analysis.ratios.eps ?: return
                val perReference = PerReferenceCalculator.calculate(
                    eps = eps,
                    assumptions = validation.assumptions,
                    currentPrice = content.analysis.price.currentPrice
                ) ?: return
                AppLogger.referenceValueCalculated(
                    stockCode = content.analysis.stockCode,
                    model = "per",
                    available = true
                )
                val comprehensiveAnalysis = ComprehensiveAnalysisCalculator.calculate(
                    analysis = content.analysis,
                    perReference = perReference,
                    srimReference = current.srimValue
                )
                AppLogger.comprehensiveAnalysisCalculated(
                    stockCode = content.analysis.stockCode,
                    result = comprehensiveAnalysis
                )
                _uiState.update {
                    it.copy(
                        perInputs = inputs,
                        perInputError = null,
                        perReference = perReference,
                        comprehensiveAnalysis = comprehensiveAnalysis
                    )
                }
            }
        }
    }

    fun onSrimChanged(field: SrimInputField, value: String) {
        val current = _uiState.value
        val content = current.content as? StockContentState.Success ?: return
        val inputs = when (field) {
            SrimInputField.RETURN_ON_EQUITY -> {
                current.srimInputs.copy(returnOnEquity = value)
            }
            SrimInputField.REQUIRED_RETURN -> {
                current.srimInputs.copy(requiredReturn = value)
            }
        }
        val roe = inputs.returnOnEquity.toPositiveFiniteDouble()
        if (roe == null) {
            AppLogger.referenceValueInputRejected("srim", "roe")
            _uiState.update {
                it.copy(
                    srimInputs = inputs,
                    srimInputError = SrimInputError.INVALID_ROE
                )
            }
            return
        }
        val requiredReturn = inputs.requiredReturn.toPositiveFiniteDouble()
        if (requiredReturn == null || requiredReturn > 100.0) {
            AppLogger.referenceValueInputRejected("srim", "required_return")
            _uiState.update {
                it.copy(
                    srimInputs = inputs,
                    srimInputError = SrimInputError.INVALID_REQUIRED_RETURN
                )
            }
            return
        }
        val bps = content.analysis.ratios.bps ?: return
        val result = SrimValueCalculator.calculate(
            bps = bps,
            assumptions = SrimAssumptions(roe, requiredReturn),
            currentPrice = content.analysis.price.currentPrice
        ) ?: return
        AppLogger.referenceValueCalculated(
            stockCode = content.analysis.stockCode,
            model = "srim",
            available = true
        )
        val comprehensiveAnalysis = ComprehensiveAnalysisCalculator.calculate(
            analysis = content.analysis,
            perReference = current.perReference,
            srimReference = result
        )
        AppLogger.comprehensiveAnalysisCalculated(
            stockCode = content.analysis.stockCode,
            result = comprehensiveAnalysis
        )
        _uiState.update {
            it.copy(
                srimInputs = inputs,
                srimInputError = null,
                srimValue = result,
                comprehensiveAnalysis = comprehensiveAnalysis
            )
        }
    }

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
                content = StockContentState.Loading(stockCode, submittedQuery),
                perInputs = PerInputFields(),
                perInputError = null,
                perReference = null,
                srimInputs = SrimInputFields(),
                srimInputError = null,
                srimValue = null,
                comprehensiveAnalysis = null
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

    private fun applyAnalysis(result: StockAnalysisResult.Success) {
        val analysis = result.analysis
        val assumptions = PerAssumptions()
        val perReference = analysis.ratios.eps?.let {
            PerReferenceCalculator.calculate(
                eps = it,
                assumptions = assumptions,
                currentPrice = analysis.price.currentPrice
            )
        }
        val srimInputs = SrimInputFields(
            returnOnEquity = analysis.ratios.roe?.toInputValue().orEmpty()
        )
        val srimValue = calculateSrim(analysis, srimInputs)
        AppLogger.referenceValueCalculated(
            stockCode = analysis.stockCode,
            model = "per",
            available = perReference != null
        )
        AppLogger.referenceValueCalculated(
            stockCode = analysis.stockCode,
            model = "srim",
            available = srimValue != null
        )
        val content = when {
            analysis.support is SupportStatus.Unsupported -> {
                StockContentState.Unsupported(analysis)
            }
            analysis.ratios.eps != null && analysis.ratios.eps <= 0.0 -> {
                StockContentState.Unsupported(
                    analysis.copy(
                        support = SupportStatus.Unsupported(SupportReason.NON_POSITIVE_EPS)
                    )
                )
            }
            else -> StockContentState.Success(analysis)
        }
        val comprehensiveAnalysis = if (content is StockContentState.Success) {
            ComprehensiveAnalysisCalculator.calculate(
                analysis = analysis,
                perReference = perReference,
                srimReference = srimValue
            )
        } else {
            null
        }
        comprehensiveAnalysis?.let {
            AppLogger.comprehensiveAnalysisCalculated(
                stockCode = analysis.stockCode,
                result = it
            )
        }
        _uiState.update {
            it.copy(
                content = content,
                perReference = perReference.takeIf { content is StockContentState.Success },
                srimInputs = srimInputs,
                srimInputError = null,
                srimValue = srimValue.takeIf { content is StockContentState.Success },
                comprehensiveAnalysis = comprehensiveAnalysis
            )
        }
    }

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

    private fun calculateSrim(
        analysis: StockAnalysis,
        inputs: SrimInputFields
    ) = analysis.ratios.bps?.let { bps ->
        val roe = inputs.returnOnEquity.toPositiveFiniteDouble() ?: return@let null
        val requiredReturn = inputs.requiredReturn.toPositiveFiniteDouble() ?: return@let null
        SrimValueCalculator.calculate(
            bps = bps,
            assumptions = SrimAssumptions(roe, requiredReturn),
            currentPrice = analysis.price.currentPrice
        )
    }

    private fun String.toPositiveFiniteDouble(): Double? = trim()
        .replace(",", "")
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 }

    private fun Double.toInputValue(): String? = takeIf { isFinite() && this > 0.0 }
        ?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }

    companion object {
        private const val SUGGESTION_LIMIT = 8
        private const val SUGGESTION_DEBOUNCE_MILLIS = 250L

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
