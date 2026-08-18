/*
 * StockScreen을 그리는 데 필요한 모든 상태를 불변 값으로 정의한다.
 * 입력 오류, 자동완성, 종목 목록 준비, 본문 상태를 별도 sealed interface로 나눠 각 상태의 필수 데이터를 보존한다.
 * StockContentState의 Idle·Loading·Success·Error가 화면의 큰 분기를 명확히 만든다.
 * ViewModel은 copy로 새 StockUiState를 발행하고 Compose는 변경된 값만 관찰해 다시 그린다.
 */
package com.chlqudco.kvalue.ui

import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.domain.model.HistoricalForecastResult
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
import com.chlqudco.kvalue.domain.model.SupportResistanceResult

// TextField의 원문과 분리된 입력 오류 종류다. UI는 enum에 맞는 문자열 리소스를 선택한다.
enum class QueryInputError {
    EMPTY,
    NO_MATCH,
    SELECT_SUGGESTION
}

// 자동완성 영역만 독립적으로 로딩·결과·빈 결과·오류를 가질 수 있다.
sealed interface StockSuggestionState {
    data object Hidden : StockSuggestionState
    data object Loading : StockSuggestionState
    data class Results(val suggestions: List<StockSearchSuggestion>) : StockSuggestionState
    data object NoResults : StockSuggestionState
    data class Error(val error: AppError) : StockSuggestionState
}

// 앱 시작 프리로드 상태는 종목 본문 조회 상태와 별개로 진행된다.
sealed interface StockCatalogState {
    data object Loading : StockCatalogState
    data class Ready(val stockCount: Int) : StockCatalogState
    data class Error(val error: AppError) : StockCatalogState
}

// 각 본문 상태가 렌더링에 꼭 필요한 값만 포함해 잘못된 상태 조합을 만들기 어렵게 한다.
sealed interface StockContentState {
    data object Idle : StockContentState
    data class Loading(val stockCode: String, val submittedQuery: String) : StockContentState
    data class Success(
        val analysis: StockAnalysis,
        val forecast: HistoricalForecastResult,
        val supportResistance: SupportResistanceResult
    ) : StockContentState
    data class Error(val stockCode: String, val error: AppError) : StockContentState
}

// 화면 전체의 단일 스냅샷이며 ViewModel은 copy로 필요한 필드만 바꾼 새 값을 발행한다.
data class StockUiState(
    val query: String = "",
    val queryError: QueryInputError? = null,
    val catalog: StockCatalogState = StockCatalogState.Loading,
    val suggestions: StockSuggestionState = StockSuggestionState.Hidden,
    val content: StockContentState = StockContentState.Idle
)
