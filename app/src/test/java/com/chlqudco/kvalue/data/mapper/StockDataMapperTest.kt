package com.chlqudco.kvalue.data.mapper

import com.chlqudco.kvalue.data.remote.DartCompanyDto
import com.chlqudco.kvalue.data.remote.KisChartDto
import com.chlqudco.kvalue.data.remote.KisChartPointDto
import com.chlqudco.kvalue.data.remote.KisFinancialRatioDto
import com.chlqudco.kvalue.data.remote.KisIncomeStatementDto
import com.chlqudco.kvalue.data.remote.KisPriceDto
import com.chlqudco.kvalue.domain.model.MissingDataSection
import com.chlqudco.kvalue.domain.model.SupportStatus
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StockDataMapperTest {
    @Test
    fun mapsSortsAndNormalizesApiData() {
        val analysis = StockDataMapper.map(
            stockCode = "005930",
            price = priceDto(),
            chart = KisChartDto(
                companyName = "삼성전자",
                points = listOf(
                    KisChartPointDto("20260814", "82000"),
                    KisChartPointDto("20260812", "80000"),
                    KisChartPointDto("20260813", "81000"),
                    KisChartPointDto("20260813", "81000")
                )
            ),
            financialRatios = listOf(
                KisFinancialRatioDto("202312", "4000", "50000", "8.0"),
                KisFinancialRatioDto("202412", "5000", "60000", "9.5"),
                KisFinancialRatioDto("202603", "9000", "90000", "20.0")
            ),
            incomeStatements = listOf(
                KisIncomeStatementDto("202212", "1", "0.2", "0.1"),
                KisIncomeStatementDto("202312", "2", "0.4", "0.3"),
                KisIncomeStatementDto("202412", "3.5", "0.8", "0.6"),
                KisIncomeStatementDto("202112", "9", "9", "9"),
                KisIncomeStatementDto("202603", "20", "10", "8")
            ),
            dartCompany = DartCompanyDto(
                corpCode = "00126380",
                corpName = "삼성전자",
                stockCode = "005930",
                modifiedDate = "20260801"
            ),
            priceAsOf = LocalDateTime.of(2026, 8, 14, 15, 30)
        )

        assertNotNull(analysis)
        analysis ?: return
        assertEquals(82_000L, analysis.price.currentPrice)
        assertEquals(1.25, analysis.price.changeRate!!, 0.0)
        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 12),
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 14)
            ),
            analysis.priceHistory.map { it.date }
        )
        assertEquals(5_000.0, analysis.ratios.eps!!, 0.0)
        assertEquals(60_000.0, analysis.ratios.bps!!, 0.0)
        assertEquals(9.5, analysis.ratios.roe!!, 0.0)
        assertEquals("2024-12", analysis.ratios.reportingPeriod)
        assertEquals(listOf(2022, 2023, 2024), analysis.annualFinancials.map { it.fiscalYear })
        assertEquals(350_000_000L, analysis.annualFinancials.last().revenue)
        assertSame(SupportStatus.Supported, analysis.support)
        assertEquals(emptySet<MissingDataSection>(), analysis.missingData)
    }

    @Test
    fun marksOptionalSectionsAsMissing() {
        val analysis = StockDataMapper.map(
            stockCode = "123456",
            price = priceDto(),
            chart = null,
            financialRatios = null,
            incomeStatements = null,
            dartCompany = null,
            priceAsOf = LocalDateTime.of(2026, 8, 14, 15, 30)
        )

        assertEquals(
            setOf(
                MissingDataSection.PRICE_HISTORY,
                MissingDataSection.FINANCIAL_RATIOS,
                MissingDataSection.ANNUAL_FINANCIALS,
                MissingDataSection.DART
            ),
            analysis?.missingData
        )
    }

    @Test
    fun rejectsMissingCurrentPrice() {
        val result = StockDataMapper.map(
            stockCode = "005930",
            price = priceDto().copy(currentPrice = ""),
            chart = null,
            financialRatios = null,
            incomeStatements = null,
            dartCompany = null,
            priceAsOf = LocalDateTime.of(2026, 8, 14, 15, 30)
        )

        assertNull(result)
    }

    private fun priceDto() = KisPriceDto(
        stockCode = "005930",
        sectorName = "전기전자",
        marketName = "KOSPI",
        fiscalClosingMonth = "12",
        currentPrice = "82000",
        changeRate = "1.25",
        eps = "4500",
        per = "16.4",
        pbr = "1.4",
        bps = "58000"
    )
}
