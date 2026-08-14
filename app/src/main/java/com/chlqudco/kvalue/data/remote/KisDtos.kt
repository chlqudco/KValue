package com.chlqudco.kvalue.data.remote

internal data class KisPriceDto(
    val stockCode: String?,
    val sectorName: String?,
    val marketName: String?,
    val fiscalClosingMonth: String?,
    val currentPrice: String?,
    val changeRate: String?,
    val eps: String?,
    val per: String?,
    val pbr: String?,
    val bps: String?
)

internal data class KisChartDto(
    val companyName: String?,
    val points: List<KisChartPointDto>
)

internal data class KisChartPointDto(
    val date: String?,
    val close: String?
)

internal data class KisFinancialRatioDto(
    val reportingPeriod: String?,
    val eps: String?,
    val bps: String?,
    val roe: String?
)

internal data class KisIncomeStatementDto(
    val reportingPeriod: String?,
    val revenue: String?,
    val operatingIncome: String?,
    val netIncome: String?
)
