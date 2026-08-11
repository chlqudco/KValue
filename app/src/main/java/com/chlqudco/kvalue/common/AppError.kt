package com.chlqudco.kvalue.common

import com.chlqudco.kvalue.domain.model.SupportReason

sealed interface AppError {
    data object InvalidInput : AppError
    data object StockNotFound : AppError
    data object NetworkUnavailable : AppError
    data object Timeout : AppError
    data object Unauthorized : AppError
    data object RateLimited : AppError
    data object ServiceUnavailable : AppError
    data class PartialData(val missing: Set<String>) : AppError
    data class UnsupportedStock(val reason: SupportReason) : AppError
    data object Unknown : AppError
}
