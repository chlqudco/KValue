/*
 * 여러 KIS DTO와 OpenDART 회사 정보를 화면이 사용할 StockAnalysis 도메인 모델로 정규화한다.
 * 문자열 숫자·날짜·억원 단위를 변환하고 일봉과 최근 연간 실적을 정규화한다.
 * 현재가처럼 필수인 값이 없으면 null을 반환하지만 선택 영역 누락은 MissingDataSection으로 보존한다.
 * 데이터별 기준기간과 DART URL도 여기서 확정해 외부 형식이 UI로 새지 않게 한다.
 */
package com.chlqudco.kvalue.data.mapper

import com.chlqudco.kvalue.data.remote.DartCompanyDto
import com.chlqudco.kvalue.data.remote.KisChartDto
import com.chlqudco.kvalue.data.remote.KisFinancialRatioDto
import com.chlqudco.kvalue.data.remote.KisIncomeStatementDto
import com.chlqudco.kvalue.data.remote.KisPriceDto
import com.chlqudco.kvalue.domain.model.AnnualFinancial
import com.chlqudco.kvalue.domain.model.DataSourceInfo
import com.chlqudco.kvalue.domain.model.DataProvider
import com.chlqudco.kvalue.domain.model.DataType
import com.chlqudco.kvalue.domain.model.FinancialRatios
import com.chlqudco.kvalue.domain.model.MarketType
import com.chlqudco.kvalue.domain.model.MissingDataSection
import com.chlqudco.kvalue.domain.model.PricePoint
import com.chlqudco.kvalue.domain.model.PriceSummary
import com.chlqudco.kvalue.domain.model.StockAnalysis
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object StockDataMapper {
    /*
     * 현재가가 정상일 때만 완성된 StockAnalysis를 반환한다.
     * 각 선택 데이터는 독립적으로 정규화한 뒤 출처·기준일·누락 섹션과 함께 하나의 값으로 조립한다.
     */
    fun map(
        stockCode: String,
        price: KisPriceDto,
        chart: KisChartDto?,
        financialRatios: List<KisFinancialRatioDto>?,
        incomeStatements: List<KisIncomeStatementDto>?,
        dartCompany: DartCompanyDto?,
        priceAsOf: LocalDateTime
    ): StockAnalysis? {
        val currentPrice = price.currentPrice.toLongValue()?.takeIf { it > 0L } ?: return null
        // 결산월과 같은 월의 연간 재무 행만 남겨 분기 값이 최근 연간 값으로 오인되는 것을 막는다.
        val fiscalClosingMonth = price.fiscalClosingMonth.toMonth()
        val annualRatioRows = financialRatios.orEmpty().filter {
            fiscalClosingMonth == null || it.reportingPeriod.periodMonth() == fiscalClosingMonth
        }
        val latestRatio = annualRatioRows.maxByOrNull {
            it.reportingPeriod.periodKey()
        }
        val eps = latestRatio?.eps.toFiniteDouble() ?: price.eps.toFiniteDouble()
        val bps = latestRatio?.bps.toFiniteDouble() ?: price.bps.toFiniteDouble()
        val points = normalizePricePoints(chart)
        val annualFinancials = normalizeIncomeStatements(
            statements = incomeStatements,
            fiscalClosingMonth = fiscalClosingMonth
        )
        val companyName = chart?.companyName?.takeIf(String::isNotBlank)
            ?: dartCompany?.corpName?.takeIf(String::isNotBlank)
            ?: stockCode
        val reportingPeriod = latestRatio?.reportingPeriod.toDisplayPeriod()
        // 누락을 0으로 채우지 않고 UI가 영역별 안내를 할 수 있도록 정확한 섹션을 기록한다.
        val missingData = buildSet {
            if (points.size < 2) add(MissingDataSection.PRICE_HISTORY)
            if (latestRatio == null) add(MissingDataSection.FINANCIAL_RATIOS)
            if (annualFinancials.isEmpty()) add(MissingDataSection.ANNUAL_FINANCIALS)
            if (dartCompany == null) add(MissingDataSection.DART)
        }
        // 데이터 종류마다 실제로 확보된 마지막 시점이 다르므로 출처 행도 독립적으로 만든다.
        val sources = buildList {
            add(
                DataSourceInfo(
                    provider = DataProvider.KIS,
                    dataType = DataType.PRICE,
                    asOf = priceAsOf.format(SOURCE_DATE_TIME_FORMAT)
                )
            )
            points.lastOrNull()?.let {
                add(
                    DataSourceInfo(
                        provider = DataProvider.KIS,
                        dataType = DataType.ADJUSTED_DAILY_PRICE,
                        asOf = it.date.toString()
                    )
                )
            }
            reportingPeriod?.let {
                add(
                    DataSourceInfo(
                        provider = DataProvider.KIS,
                        dataType = DataType.FINANCIAL_RATIOS,
                        asOf = it
                    )
                )
            }
            annualFinancials.maxByOrNull(AnnualFinancial::fiscalYear)?.let {
                add(
                    DataSourceInfo(
                        provider = DataProvider.KIS,
                        dataType = DataType.INCOME_STATEMENT,
                        asOf = it.fiscalYear.toString()
                    )
                )
            }
            dartCompany?.let {
                add(
                    DataSourceInfo(
                        provider = DataProvider.OPEN_DART,
                        dataType = DataType.DISCLOSURE_LINK,
                        asOf = it.modifiedDate.toDisplayDate()
                    )
                )
            }
        }
        return StockAnalysis(
            stockCode = stockCode,
            companyName = companyName,
            market = MarketType.KRX,
            price = PriceSummary(
                currentPrice = currentPrice,
                changeRate = price.changeRate.toFiniteDouble(),
                asOf = priceAsOf
            ),
            priceHistory = points.takeLast(MAX_CHART_POINTS),
            forecastHistory = points,
            ratios = FinancialRatios(
                eps = eps,
                per = price.per.toPositiveFiniteDouble(),
                pbr = price.pbr.toPositiveFiniteDouble(),
                bps = bps,
                roe = latestRatio?.roe.toFiniteDouble(),
                reportingPeriod = reportingPeriod
            ),
            annualFinancials = annualFinancials,
            sources = sources,
            dartUrl = dartCompany?.let {
                "https://dart.fss.or.kr/dsab007/main.do?option=corp&textCrpNm=${it.corpCode}"
            } ?: "https://dart.fss.or.kr/",
            missingData = missingData
        )
    }

    /*
     * 파싱 가능한 양수 종가만 남긴 뒤 날짜 중복 제거, 오름차순 정렬, 전망 검증용 최대 개수 제한을 적용한다.
     * open/high/low/volume은 선택 필드라 잘못된 값만 null로 두고 정상 종가 행 자체는 유지한다.
     */
    private fun normalizePricePoints(chart: KisChartDto?): List<PricePoint> =
        chart?.points.orEmpty()
            .mapNotNull { point ->
                val date = runCatching {
                    LocalDate.parse(point.date, DateTimeFormatter.BASIC_ISO_DATE)
                }.getOrNull()
                val close = point.close.toLongValue()?.takeIf { it > 0L }
                if (date == null || close == null) {
                    null
                } else {
                    PricePoint(
                        date = date,
                        close = close,
                        open = point.open.toLongValue()?.takeIf { it > 0L },
                        high = point.high.toLongValue()?.takeIf { it > 0L },
                        low = point.low.toLongValue()?.takeIf { it > 0L },
                        volume = point.volume.toLongValue()?.takeIf { it >= 0L }
                    )
                }
            }
            .distinctBy(PricePoint::date)
            .sortedBy(PricePoint::date)
            .takeLast(MAX_FORECAST_POINTS)

    /*
     * 손익계산서의 억원 단위를 원 단위 Long으로 바꾸고 결산월 기준 연간 행을 연도당 하나만 선택한다.
     * 가장 최근 세 연도를 고른 뒤 화면과 추세 계산이 자연스럽도록 다시 연도 오름차순으로 정렬한다.
     */
    private fun normalizeIncomeStatements(
        statements: List<KisIncomeStatementDto>?,
        fiscalClosingMonth: Int?
    ): List<AnnualFinancial> = statements.orEmpty()
        .filter {
            fiscalClosingMonth == null || it.reportingPeriod.periodMonth() == fiscalClosingMonth
        }
        .mapNotNull { statement ->
            val period = statement.reportingPeriod.periodKey()
            val year = period.take(4).toIntOrNull() ?: return@mapNotNull null
            val revenue = statement.revenue.toWonFromHundredMillion()
            val operatingIncome = statement.operatingIncome.toWonFromHundredMillion()
            val netIncome = statement.netIncome.toWonFromHundredMillion()
            if (revenue == null && operatingIncome == null && netIncome == null) {
                return@mapNotNull null
            }
            period to AnnualFinancial(
                fiscalYear = year,
                revenue = revenue,
                operatingIncome = operatingIncome,
                netIncome = netIncome
            )
        }
        .groupBy { it.second.fiscalYear }
        .mapNotNull { (_, values) -> values.maxByOrNull { it.first }?.second }
        .sortedByDescending(AnnualFinancial::fiscalYear)
        .take(MAX_FINANCIAL_YEARS)
        .sortedBy(AnnualFinancial::fiscalYear)

    // KIS 문자열 숫자를 HALF_UP 정수로 바꾸며 Long 범위를 넘거나 파싱할 수 없으면 null을 반환한다.
    private fun String?.toLongValue(): Long? = this
        ?.trim()
        ?.replace(",", "")
        ?.toBigDecimalOrNull()
        ?.let { runCatching { it.setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull() }

    private fun String?.toFiniteDouble(): Double? = this
        ?.trim()
        ?.replace(",", "")
        ?.toDoubleOrNull()
        ?.takeIf(Double::isFinite)

    private fun String?.toPositiveFiniteDouble(): Double? =
        toFiniteDouble()?.takeIf { it > 0.0 }

    // 손익 API의 억원 단위 문자열을 100,000,000과 곱해 원 단위로 정규화한다.
    private fun String?.toWonFromHundredMillion(): Long? = this
        ?.trim()
        ?.replace(",", "")
        ?.toBigDecimalOrNull()
        ?.multiply(HUNDRED_MILLION)
        ?.let { runCatching { it.setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull() }

    private fun String?.periodKey(): String = this.orEmpty().filter(Char::isDigit).take(6)

    private fun String?.periodMonth(): Int? = periodKey().takeLast(2).toIntOrNull()

    private fun String?.toMonth(): Int? = this
        ?.filter(Char::isDigit)
        ?.toIntOrNull()
        ?.takeIf { it in 1..12 }

    private fun String?.toDisplayPeriod(): String? {
        val value = periodKey()
        return when (value.length) {
            6 -> "${value.take(4)}-${value.takeLast(2)}"
            4 -> value
            else -> null
        }
    }

    private fun String.toDisplayDate(): String = when (length) {
        8 -> "${take(4)}-${substring(4, 6)}-${takeLast(2)}"
        else -> this
    }

    private val HUNDRED_MILLION = BigDecimal("100000000")
    private val SOURCE_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private const val MAX_CHART_POINTS = 100
    private const val MAX_FORECAST_POINTS = 800
    private const val MAX_FINANCIAL_YEARS = 3
}
