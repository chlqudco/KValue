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
