package com.chlqudco.kvalue.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class MarketType {
    KRX
}

data class PriceSummary(
    val currentPrice: Long,
    val changeRate: Double,
    val asOf: LocalDateTime
)

data class PricePoint(
    val date: LocalDate,
    val close: Long
)

data class FinancialRatios(
    val eps: Double?,
    val per: Double?,
    val pbr: Double?,
    val bps: Double?,
    val roe: Double?,
    val reportingPeriod: String?
)

data class AnnualFinancial(
    val fiscalYear: Int,
    val revenue: Long?,
    val operatingIncome: Long?,
    val netIncome: Long?
)

enum class SupportReason {
    NON_POSITIVE_EPS,
    ETF_OR_ETN,
    PREFERRED_STOCK,
    SPAC,
    REIT,
    FINANCIAL_COMPANY,
    UNKNOWN_TYPE
}

sealed interface SupportStatus {
    data object Supported : SupportStatus
    data class Unsupported(val reason: SupportReason) : SupportStatus
}

data class DataSourceInfo(
    val provider: String,
    val dataType: String,
    val asOf: String
)

data class StockAnalysis(
    val stockCode: String,
    val companyName: String,
    val market: MarketType,
    val price: PriceSummary,
    val priceHistory: List<PricePoint>,
    val ratios: FinancialRatios,
    val annualFinancials: List<AnnualFinancial>,
    val support: SupportStatus,
    val sources: List<DataSourceInfo>,
    val dartUrl: String
)
