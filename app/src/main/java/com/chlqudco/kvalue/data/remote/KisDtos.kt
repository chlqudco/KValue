/*
 * 한국투자증권 Open API 응답에서 필요한 필드만 담는 외부 데이터 전송 객체 모음이다.
 * 공급자 응답은 숫자도 문자열과 null로 올 수 있으므로 DTO 단계에서는 원문 형태를 그대로 보존한다.
 * 값 검증, 단위 변환, 날짜 정렬은 이 타입에서 하지 않고 StockDataMapper가 담당한다.
 * internal 가시성으로 외부 DTO가 data 계층 밖이나 UI까지 퍼지는 것을 막는다.
 */
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
    val close: String?,
    val open: String? = null,
    val high: String? = null,
    val low: String? = null,
    val volume: String? = null
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
