package com.chlqudco.kvalue.ui

import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.domain.model.ComprehensiveAnalysisResult
import com.chlqudco.kvalue.domain.model.PerReferenceResult
import com.chlqudco.kvalue.domain.model.SrimValueResult
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion

enum class QueryInputError {
    EMPTY,
    NO_MATCH,
    SELECT_SUGGESTION
}

enum class PerInputError {
    INVALID_VALUE,
    INVALID_ORDER
}

enum class PerScenario {
    CONSERVATIVE,
    BASE,
    OPTIMISTIC
}

enum class SrimInputError {
    INVALID_ROE,
    INVALID_REQUIRED_RETURN
}

enum class SrimInputField {
    RETURN_ON_EQUITY,
    REQUIRED_RETURN
}

data class PerInputFields(
    val conservative: String = "10",
    val base: String = "15",
    val optimistic: String = "20"
)

data class SrimInputFields(
    val returnOnEquity: String = "",
    val requiredReturn: String = "10"
)

sealed interface StockSuggestionState {
    data object Hidden : StockSuggestionState
    data object Loading : StockSuggestionState
    data class Results(val suggestions: List<StockSearchSuggestion>) : StockSuggestionState
    data object NoResults : StockSuggestionState
    data class Error(val error: AppError) : StockSuggestionState
}

sealed interface StockCatalogState {
    data object Loading : StockCatalogState
    data class Ready(val stockCount: Int) : StockCatalogState
    data class Error(val error: AppError) : StockCatalogState
}

sealed interface StockContentState {
    data object Idle : StockContentState
    data class Loading(val stockCode: String, val submittedQuery: String) : StockContentState
    data class Success(val analysis: StockAnalysis) : StockContentState
    data class Unsupported(val analysis: StockAnalysis) : StockContentState
    data class Error(val stockCode: String, val error: AppError) : StockContentState
}

data class StockUiState(
    val query: String = "",
    val queryError: QueryInputError? = null,
    val catalog: StockCatalogState = StockCatalogState.Loading,
    val suggestions: StockSuggestionState = StockSuggestionState.Hidden,
    val content: StockContentState = StockContentState.Idle,
    val perInputs: PerInputFields = PerInputFields(),
    val perInputError: PerInputError? = null,
    val perReference: PerReferenceResult? = null,
    val srimInputs: SrimInputFields = SrimInputFields(),
    val srimInputError: SrimInputError? = null,
    val srimValue: SrimValueResult? = null,
    val comprehensiveAnalysis: ComprehensiveAnalysisResult? = null
)
