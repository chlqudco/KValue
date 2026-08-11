package com.chlqudco.kvalue.ui

import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.domain.model.FairValueResult
import com.chlqudco.kvalue.domain.model.StockAnalysis

enum class QueryInputError {
    EMPTY,
    INVALID_FORMAT
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

data class PerInputFields(
    val conservative: String = "10",
    val base: String = "15",
    val optimistic: String = "20"
)

sealed interface StockContentState {
    data object Idle : StockContentState
    data class Loading(val stockCode: String) : StockContentState
    data class Success(val analysis: StockAnalysis) : StockContentState
    data class Unsupported(val analysis: StockAnalysis) : StockContentState
    data class Error(val stockCode: String, val error: AppError) : StockContentState
}

data class StockUiState(
    val query: String = "",
    val queryError: QueryInputError? = null,
    val content: StockContentState = StockContentState.Idle,
    val perInputs: PerInputFields = PerInputFields(),
    val perInputError: PerInputError? = null,
    val fairValue: FairValueResult? = null
)
