package com.chlqudco.kvalue.data.mapper

import com.chlqudco.kvalue.data.remote.DartCompanyDto
import com.chlqudco.kvalue.data.remote.KisChartDto
import com.chlqudco.kvalue.data.remote.KisFinancialRatioDto
import com.chlqudco.kvalue.data.remote.KisIncomeStatementDto
import com.chlqudco.kvalue.data.remote.KisPriceDto
import com.chlqudco.kvalue.domain.StockSupportClassifier
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
        val support = StockSupportClassifier.classify(
            companyName = companyName,
            sectorName = price.sectorName,
            eps = eps,
            hasDartCompany = dartCompany != null
        )
        val missingData = buildSet {
            if (points.size < 2) add(MissingDataSection.PRICE_HISTORY)
            if (latestRatio == null) add(MissingDataSection.FINANCIAL_RATIOS)
            if (annualFinancials.isEmpty()) add(MissingDataSection.ANNUAL_FINANCIALS)
            if (dartCompany == null) add(MissingDataSection.DART)
        }
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
            priceHistory = points,
            ratios = FinancialRatios(
                eps = eps,
                per = price.per.toPositiveFiniteDouble(),
                pbr = price.pbr.toPositiveFiniteDouble(),
                bps = bps,
                roe = latestRatio?.roe.toFiniteDouble(),
                reportingPeriod = reportingPeriod
            ),
            annualFinancials = annualFinancials,
            support = support,
            sources = sources,
            dartUrl = dartCompany?.let {
                "https://dart.fss.or.kr/dsab007/main.do?option=corp&textCrpNm=${it.corpCode}"
            } ?: "https://dart.fss.or.kr/",
            missingData = missingData
        )
    }

    private fun normalizePricePoints(chart: KisChartDto?): List<PricePoint> =
        chart?.points.orEmpty()
            .mapNotNull { point ->
                val date = runCatching {
                    LocalDate.parse(point.date, DateTimeFormatter.BASIC_ISO_DATE)
                }.getOrNull()
                val close = point.close.toLongValue()?.takeIf { it > 0L }
                if (date == null || close == null) null else PricePoint(date, close)
            }
            .distinctBy(PricePoint::date)
            .sortedBy(PricePoint::date)
            .takeLast(MAX_CHART_POINTS)

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
    private const val MAX_FINANCIAL_YEARS = 3
}
