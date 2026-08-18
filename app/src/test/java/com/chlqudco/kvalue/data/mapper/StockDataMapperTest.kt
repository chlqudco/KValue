/*
 * 외부 DTO가 StockAnalysis로 정규화되는 규칙을 검증하는 데이터 계층 단위 테스트다.
 * 문자열 숫자와 억원 단위 변환, 날짜 정렬·중복 제거와 최신 재무기간 선택을 확인한다.
 * 선택 데이터 누락은 missingData로 남고 필수 현재가 누락은 전체 매핑 실패가 되는 경계도 검증한다.
 */
package com.chlqudco.kvalue.data.mapper

import com.chlqudco.kvalue.data.remote.DartCompanyDto
import com.chlqudco.kvalue.data.remote.KisChartDto
import com.chlqudco.kvalue.data.remote.KisChartPointDto
import com.chlqudco.kvalue.data.remote.KisFinancialRatioDto
import com.chlqudco.kvalue.data.remote.KisIncomeStatementDto
import com.chlqudco.kvalue.data.remote.KisPriceDto
import com.chlqudco.kvalue.domain.model.MissingDataSection
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
                    KisChartPointDto(
                        date = "20260814",
                        close = "82000",
                        open = "81000",
                        high = "82500",
                        low = "80500",
                        volume = "12345678"
                    ),
                    KisChartPointDto("20260812", "80000"),
                    KisChartPointDto("20260813", "81000"),
                    KisChartPointDto("20260813", "81000")
                )
            ),
            financialRatios = listOf(
                KisFinancialRatioDto("202212", "3000", "45000", "7.0"),
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
        assertEquals(81_000L, analysis.priceHistory.last().open)
        assertEquals(82_500L, analysis.priceHistory.last().high)
        assertEquals(80_500L, analysis.priceHistory.last().low)
        assertEquals(12_345_678L, analysis.priceHistory.last().volume)
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
        assertEquals(4_500.0, analysis?.ratios?.eps ?: Double.NaN, 0.0)
        assertEquals(58_000.0, analysis?.ratios?.bps ?: Double.NaN, 0.0)
        assertNull(analysis?.ratios?.roe)
    }

    @Test
    fun keepsLongForecastHistoryAndLimitsChartToOneHundredPoints() {
        val startDate = LocalDate.of(2025, 1, 1)
        val chartPoints = (0 until 220).map { index ->
            KisChartPointDto(
                date = startDate.plusDays(index.toLong()).format(
                    java.time.format.DateTimeFormatter.BASIC_ISO_DATE
                ),
                close = (70_000L + index).toString()
            )
        }

        val analysis = StockDataMapper.map(
            stockCode = "005930",
            price = priceDto(),
            chart = KisChartDto("삼성전자", chartPoints),
            financialRatios = null,
            incomeStatements = null,
            dartCompany = null,
            priceAsOf = LocalDateTime.of(2026, 8, 14, 15, 30)
        )

        assertEquals(100, analysis?.priceHistory?.size)
        assertEquals(220, analysis?.forecastHistory?.size)
        assertEquals(
            analysis?.forecastHistory?.takeLast(100),
            analysis?.priceHistory
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
