package com.chlqudco.kvalue.data

import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.domain.model.AnnualFinancial
import com.chlqudco.kvalue.domain.model.DataSourceInfo
import com.chlqudco.kvalue.domain.model.FinancialRatios
import com.chlqudco.kvalue.domain.model.MarketType
import com.chlqudco.kvalue.domain.model.PricePoint
import com.chlqudco.kvalue.domain.model.PriceSummary
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.domain.model.SupportStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlinx.coroutines.delay

class SampleStockRepository : StockRepository {
    private val cache = mutableMapOf<String, StockAnalysis>()

    override suspend fun getStockAnalysis(
        stockCode: String,
        forceRefresh: Boolean
    ): StockAnalysisResult {
        if (!forceRefresh) {
            cache[stockCode]?.let { return StockAnalysisResult.Success(it) }
        }
        delay(300)
        if (stockCode != "005930") {
            return StockAnalysisResult.Failure(AppError.StockNotFound)
        }
        val analysis = SampleStockData.samsungElectronics()
        cache[stockCode] = analysis
        return StockAnalysisResult.Success(analysis)
    }
}

object SampleStockData {
    fun samsungElectronics(): StockAnalysis {
        val tradingDays = generateSequence(LocalDate.of(2026, 8, 11)) { it.minusDays(1) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .take(100)
            .toList()
            .sorted()
        val history = tradingDays.mapIndexed { index, date ->
            val trend = 68_000L + index * 145L
            val wave = (sin(index / 5.2) * 3_100.0).roundToLong()
            PricePoint(date = date, close = trend + wave)
        }.toMutableList()
        history[history.lastIndex] = history.last().copy(close = 82_300L)

        return StockAnalysis(
            stockCode = "005930",
            companyName = "삼성전자",
            market = MarketType.KRX,
            price = PriceSummary(
                currentPrice = 82_300L,
                changeRate = 1.23,
                asOf = LocalDateTime.of(2026, 8, 11, 15, 30)
            ),
            priceHistory = history,
            ratios = FinancialRatios(
                eps = 5_800.0,
                per = 14.2,
                pbr = 1.4,
                bps = 58_786.0,
                roe = 9.8,
                reportingPeriod = "2025년 연간"
            ),
            annualFinancials = listOf(
                AnnualFinancial(
                    fiscalYear = 2023,
                    revenue = 258_935_000_000_000L,
                    operatingIncome = 6_567_000_000_000L,
                    netIncome = 15_487_000_000_000L
                ),
                AnnualFinancial(
                    fiscalYear = 2024,
                    revenue = 300_871_000_000_000L,
                    operatingIncome = 32_726_000_000_000L,
                    netIncome = 28_171_000_000_000L
                ),
                AnnualFinancial(
                    fiscalYear = 2025,
                    revenue = 320_400_000_000_000L,
                    operatingIncome = 39_100_000_000_000L,
                    netIncome = 31_200_000_000_000L
                )
            ),
            support = SupportStatus.Supported,
            sources = listOf(
                DataSourceInfo("샘플 데이터", "가격·차트", "2026-08-11 15:30"),
                DataSourceInfo("샘플 데이터", "재무", "2025년 연간")
            ),
            dartUrl = "https://dart.fss.or.kr/dsab007/main.do?option=corp&textCrpNm=00126380"
        )
    }
}
