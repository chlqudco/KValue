/*
 * 한 종목 조회 결과를 구성하는 핵심 도메인 모델과 관련 열거형을 모은 파일이다.
 * 가격과 일봉은 원 단위, 재무지표는 원본 숫자, 시각은 java.time 타입으로 보존해 표시 문자열과 분리한다.
 * MissingDataSection은 선택 데이터의 부분 누락을 표현한다.
 * DataSourceInfo는 가격·차트·재무·공시마다 서로 다른 공급자와 기준시점을 화면에 공개하기 위한 모델이다.
 */
package com.chlqudco.kvalue.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class MarketType {
    KRX
}

// 현재가와 등락률의 기준시각은 차트·재무 기준기간과 독립적으로 보존한다.
data class PriceSummary(
    val currentPrice: Long,
    val changeRate: Double?,
    val asOf: LocalDateTime
)

// 종가만 필수이고 OHLCV 나머지는 공급자 부분 누락을 표현할 수 있도록 nullable이다.
data class PricePoint(
    val date: LocalDate,
    val close: Long,
    val open: Long? = null,
    val high: Long? = null,
    val low: Long? = null,
    val volume: Long? = null
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

enum class DataProvider {
    SAMPLE,
    KIS,
    OPEN_DART
}

enum class DataType {
    PRICE,
    ADJUSTED_DAILY_PRICE,
    FINANCIAL_RATIOS,
    INCOME_STATEMENT,
    DISCLOSURE_LINK
}

// 화면 하단의 출처 행 하나에 대응하며 각 데이터 종류의 독립 기준일을 담는다.
data class DataSourceInfo(
    val provider: DataProvider,
    val dataType: DataType,
    val asOf: String
)

enum class MissingDataSection {
    PRICE_HISTORY,
    FINANCIAL_RATIOS,
    ANNUAL_FINANCIALS,
    DART
}

// Repository가 UI 계층에 전달하는 완성된 단일 종목 분석 원본이다.
data class StockAnalysis(
    val stockCode: String,
    val companyName: String,
    val market: MarketType,
    val price: PriceSummary,
    val priceHistory: List<PricePoint>,
    val forecastHistory: List<PricePoint>,
    val ratios: FinancialRatios,
    val annualFinancials: List<AnnualFinancial>,
    val sources: List<DataSourceInfo>,
    val dartUrl: String,
    val missingData: Set<MissingDataSection> = emptySet()
)
