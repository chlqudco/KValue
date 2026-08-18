/*
 * API 키와 네트워크 없이 전체 화면을 실행하기 위한 StockRepository 구현과 고정 샘플 데이터다.
 * 검색 가능한 종목은 삼성전자 한 개이며 조회 지연과 메모리 캐시를 넣어 실제 로딩 흐름도 재현한다.
 * 장기 OHLCV와 최근 100거래일 차트, 재무비율, 3개 연도 실적을 제공해 전체 조회 화면을 확인할 수 있다.
 * 샘플 값은 투자 데이터가 아니라 개발·테스트용이므로 실제 데이터와 명확히 구분해 표시된다.
 */
package com.chlqudco.kvalue.data

import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.common.AppLogger
import com.chlqudco.kvalue.domain.model.AnnualFinancial
import com.chlqudco.kvalue.domain.model.DataSourceInfo
import com.chlqudco.kvalue.domain.model.DataProvider
import com.chlqudco.kvalue.domain.model.DataType
import com.chlqudco.kvalue.domain.model.FinancialRatios
import com.chlqudco.kvalue.domain.model.MarketType
import com.chlqudco.kvalue.domain.model.PricePoint
import com.chlqudco.kvalue.domain.model.PriceSummary
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.domain.StockSearchMatcher
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
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
        // forceRefresh가 아니면 실제 Repository와 같은 방식으로 메모리 결과를 재사용한다.
        val startedAtMillis = AppLogger.analysisStarted(stockCode, forceRefresh)
        if (!forceRefresh) {
            cache[stockCode]?.let {
                AppLogger.cacheHit("SAMPLE", "stock_analysis", "memory", stockCode)
                AppLogger.analysisSucceeded(
                    stockCode = stockCode,
                    missingSectionCount = it.missingData.size,
                    startedAtMillis = startedAtMillis
                )
                return StockAnalysisResult.Success(it)
            }
        }
        delay(300)
        if (stockCode != "005930") {
            AppLogger.analysisFailed(stockCode, AppError.StockNotFound, startedAtMillis)
            return StockAnalysisResult.Failure(AppError.StockNotFound)
        }
        val analysis = SampleStockData.samsungElectronics()
        cache[stockCode] = analysis
        AppLogger.analysisSucceeded(
            stockCode = stockCode,
            missingSectionCount = analysis.missingData.size,
            startedAtMillis = startedAtMillis
        )
        return StockAnalysisResult.Success(analysis)
    }

    override suspend fun searchStocks(query: String, limit: Int): StockSearchResult {
        val trace = AppLogger.requestStarted("SAMPLE", "stock_search")
        val suggestions = StockSearchMatcher.find(SAMPLE_STOCKS, query, limit)
        AppLogger.requestSucceeded(trace, suggestions.size)
        return StockSearchResult.Success(suggestions)
    }

    override suspend fun preloadStockCatalog(): StockCatalogResult {
        val trace = AppLogger.requestStarted("SAMPLE", "stock_catalog_preload")
        AppLogger.requestSucceeded(trace, SAMPLE_STOCKS.size)
        return StockCatalogResult.Success(SAMPLE_STOCKS.size)
    }

    private companion object {
        val SAMPLE_STOCKS = listOf(
            StockSearchSuggestion("005930", "삼성전자")
        )
    }
}

object SampleStockData {
    fun samsungElectronics(): StockAnalysis {
        // 주말을 제외한 장기 날짜에 완만한 추세와 사인파 변동을 더해 전망 검증용 시계열을 만든다.
        val tradingDays = generateSequence(LocalDate.of(2026, 8, 11)) { it.minusDays(1) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .take(640)
            .toList()
            .sorted()
        val rawHistory = tradingDays.mapIndexed { index, date ->
            val trend = 53_500L + index * 45L
            val wave = (
                sin(index / 9.0) * 1_400.0 +
                    sin(index / 33.0) * 900.0
                ).roundToLong()
            val close = trend + wave
            val open = close + ((index % 7) - 3) * 120L
            PricePoint(
                date = date,
                close = close,
                open = open,
                high = maxOf(open, close) + 650L + index % 4 * 80L,
                low = minOf(open, close) - 620L - index % 3 * 70L,
                volume = 8_000_000L + index % 11 * 620_000L
            )
        }
        val adjustment = 82_300L - rawHistory.last().close
        val history = rawHistory.map { point ->
            point.copy(
                close = point.close + adjustment,
                open = point.open?.plus(adjustment),
                high = point.high?.plus(adjustment),
                low = point.low?.plus(adjustment)
            )
        }.toMutableList()
        // 마지막 행은 상단 가격 카드의 현재가와 같은 종가·거래량으로 맞춘다.
        history[history.lastIndex] = history.last().copy(
            close = 82_300L,
            open = 81_700L,
            high = 83_000L,
            low = 81_200L,
            volume = 16_500_000L
        )

        return StockAnalysis(
            stockCode = "005930",
            companyName = "삼성전자",
            market = MarketType.KRX,
            price = PriceSummary(
                currentPrice = 82_300L,
                changeRate = 1.23,
                asOf = LocalDateTime.of(2026, 8, 11, 15, 30)
            ),
            priceHistory = history.takeLast(100),
            forecastHistory = history,
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
            sources = listOf(
                DataSourceInfo(
                    DataProvider.SAMPLE,
                    DataType.PRICE,
                    "2026-08-11 15:30"
                ),
                DataSourceInfo(
                    DataProvider.SAMPLE,
                    DataType.FINANCIAL_RATIOS,
                    "2025-12"
                )
            ),
            dartUrl = "https://dart.fss.or.kr/dsab007/main.do?option=corp&textCrpNm=00126380"
        )
    }
}
