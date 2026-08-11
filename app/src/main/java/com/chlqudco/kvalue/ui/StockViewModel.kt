package com.chlqudco.kvalue.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.common.StockCodeValidation
import com.chlqudco.kvalue.common.StockCodeValidator
import com.chlqudco.kvalue.data.StockAnalysisResult
import com.chlqudco.kvalue.data.StockRepository
import com.chlqudco.kvalue.domain.FairValueCalculator
import com.chlqudco.kvalue.domain.PerAssumptionsValidator
import com.chlqudco.kvalue.domain.PerValidationResult
import com.chlqudco.kvalue.domain.model.PerAssumptions
import com.chlqudco.kvalue.domain.model.SupportReason
import com.chlqudco.kvalue.domain.model.SupportStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private var latestRequestId = 0L

    fun onQueryChanged(value: String) {
        _uiState.update { it.copy(query = value, queryError = null) }
    }

    fun search() {
        when (val validation = StockCodeValidator.validate(_uiState.value.query)) {
            StockCodeValidation.Empty -> {
                _uiState.update { it.copy(queryError = QueryInputError.EMPTY) }
            }
            StockCodeValidation.InvalidFormat -> {
                _uiState.update { it.copy(queryError = QueryInputError.INVALID_FORMAT) }
            }
            is StockCodeValidation.Valid -> startSearch(validation.code, forceRefresh = false)
        }
    }

    fun refresh() {
        val content = _uiState.value.content
        val stockCode = when (content) {
            is StockContentState.Success -> content.analysis.stockCode
            is StockContentState.Unsupported -> content.analysis.stockCode
            is StockContentState.Error -> content.stockCode
            else -> return
        }
        startSearch(stockCode, forceRefresh = true)
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
                _uiState.update {
                    it.copy(perInputs = inputs, perInputError = PerInputError.INVALID_VALUE)
                }
            }
            PerValidationResult.InvalidOrder -> {
                _uiState.update {
                    it.copy(perInputs = inputs, perInputError = PerInputError.INVALID_ORDER)
                }
            }
            is PerValidationResult.Valid -> {
                val eps = content.analysis.ratios.eps ?: return
                val fairValue = FairValueCalculator.calculate(
                    eps = eps,
                    assumptions = validation.assumptions,
                    currentPrice = content.analysis.price.currentPrice
                ) ?: return
                _uiState.update {
                    it.copy(
                        perInputs = inputs,
                        perInputError = null,
                        fairValue = fairValue
                    )
                }
            }
        }
    }

    private fun startSearch(stockCode: String, forceRefresh: Boolean) {
        val loading = _uiState.value.content as? StockContentState.Loading
        if (loading?.stockCode == stockCode) return
        searchJob?.cancel()
        val requestId = ++latestRequestId
        _uiState.update {
            it.copy(
                query = stockCode,
                queryError = null,
                content = StockContentState.Loading(stockCode),
                perInputs = PerInputFields(),
                perInputError = null,
                fairValue = null
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
        val fairValue = analysis.ratios.eps?.let {
            FairValueCalculator.calculate(
                eps = it,
                assumptions = assumptions,
                currentPrice = analysis.price.currentPrice
            )
        }
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
        _uiState.update {
            it.copy(
                content = content,
                fairValue = fairValue.takeIf { content is StockContentState.Success }
            )
        }
    }

    companion object {
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
