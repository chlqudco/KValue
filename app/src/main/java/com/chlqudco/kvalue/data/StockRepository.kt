package com.chlqudco.kvalue.data

import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.domain.model.StockAnalysis

sealed interface StockAnalysisResult {
    data class Success(val analysis: StockAnalysis) : StockAnalysisResult
    data class Failure(val error: AppError) : StockAnalysisResult
}

interface StockRepository {
    suspend fun getStockAnalysis(
        stockCode: String,
        forceRefresh: Boolean = false
    ): StockAnalysisResult
}
