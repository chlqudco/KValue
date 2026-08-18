/*
 * UI와 실제 데이터 공급자를 분리하는 Repository 계약과 결과 타입을 정의한다.
 * 성공과 실패를 sealed interface로 표현해 예외가 ViewModel까지 새어나가지 않게 한다.
 * 동일한 계약을 SampleStockRepository와 KisDartStockRepository가 구현하므로 실행 모드를 쉽게 교체할 수 있다.
 * forceRefresh는 새로고침 때 캐시를 우회한다는 의도를 호출부가 명시하도록 만든 매개변수다.
 */
package com.chlqudco.kvalue.data

import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion

sealed interface StockAnalysisResult {
    data class Success(val analysis: StockAnalysis) : StockAnalysisResult
    data class Failure(val error: AppError) : StockAnalysisResult
}

sealed interface StockSearchResult {
    data class Success(val suggestions: List<StockSearchSuggestion>) : StockSearchResult
    data class Failure(val error: AppError) : StockSearchResult
}

sealed interface StockCatalogResult {
    data class Success(val stockCount: Int) : StockCatalogResult
    data class Failure(val error: AppError) : StockCatalogResult
}

interface StockRepository {
    suspend fun getStockAnalysis(
        stockCode: String,
        forceRefresh: Boolean = false
    ): StockAnalysisResult

    suspend fun searchStocks(
        query: String,
        limit: Int = 8
    ): StockSearchResult

    suspend fun preloadStockCatalog(): StockCatalogResult
}
