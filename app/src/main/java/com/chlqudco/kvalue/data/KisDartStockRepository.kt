/*
 * KIS 시세·재무 데이터와 OpenDART 회사 정보를 하나의 StockAnalysis로 합치는 실데이터 Repository다.
 * 현재가는 필수로 조회하고 차트·재무·DART는 optional로 감싸 부분 실패가 전체 실패가 되지 않게 한다.
 * 성공 결과는 종목별로 60초간 메모리에 보관하며 forceRefresh 요청은 이 캐시를 건너뛴다.
 * 취소 예외는 다시 던져 코루틴 취소 의미를 보존하고, 나머지 공급자 오류는 명시적 결과 타입으로 변환한다.
 */
package com.chlqudco.kvalue.data

import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.common.AppLogger
import com.chlqudco.kvalue.data.mapper.StockDataMapper
import com.chlqudco.kvalue.data.remote.ApiCallException
import com.chlqudco.kvalue.data.remote.DartCorpCodeDataSource
import com.chlqudco.kvalue.data.remote.KisApiClient
import com.chlqudco.kvalue.data.remote.KisChartDto
import com.chlqudco.kvalue.data.remote.KisChartPointDto
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

internal class KisDartStockRepository(
    private val kisApiClient: KisApiClient,
    private val dartCorpCodeDataSource: DartCorpCodeDataSource,
    private val clock: Clock = Clock.system(SEOUL_ZONE_ID)
) : StockRepository {
    private val cache = mutableMapOf<String, CacheEntry>()

    override suspend fun getStockAnalysis(
        stockCode: String,
        forceRefresh: Boolean
    ): StockAnalysisResult {
        // elapsedRealtime 기반 진단 시작시각과 별도로 Clock의 epoch 값은 캐시 만료 판단에 사용한다.
        val startedAtMillis = AppLogger.analysisStarted(stockCode, forceRefresh)
        val now = clock.millis()
        if (!forceRefresh) {
            cache[stockCode]
                ?.takeIf { now - it.savedAtEpochMillis < CACHE_LIFETIME_MILLIS }
                ?.let {
                    AppLogger.cacheHit(
                        provider = "APP",
                        operation = "stock_analysis",
                        source = "memory",
                        stockCode = stockCode
                    )
                    AppLogger.analysisSucceeded(
                        stockCode = stockCode,
                        missingSectionCount = it.analysis.missingData.size,
                        startedAtMillis = startedAtMillis
                    )
                    return StockAnalysisResult.Success(it.analysis)
                }
        }
        // 공급자 예외가 호출자에게 직접 노출되지 않도록 Repository 결과 타입으로 닫는다.
        return try {
            val analysis = loadAnalysis(stockCode)
            if (analysis == null) {
                AppLogger.analysisFailed(
                    stockCode,
                    AppError.StockNotFound,
                    startedAtMillis
                )
                return StockAnalysisResult.Failure(AppError.StockNotFound)
            }
            cache[stockCode] = CacheEntry(analysis, clock.millis())
            AppLogger.analysisSucceeded(
                stockCode = stockCode,
                missingSectionCount = analysis.missingData.size,
                startedAtMillis = startedAtMillis
            )
            StockAnalysisResult.Success(analysis)
        } catch (cancellation: CancellationException) {
            AppLogger.analysisCancelled(stockCode, startedAtMillis)
            throw cancellation
        } catch (error: ApiCallException) {
            AppLogger.analysisFailed(stockCode, error.error, startedAtMillis)
            StockAnalysisResult.Failure(error.error)
        } catch (_: Exception) {
            AppLogger.analysisFailed(stockCode, AppError.Unknown, startedAtMillis)
            StockAnalysisResult.Failure(AppError.Unknown)
        }
    }

    // OpenDART 회사 DTO를 자동완성에 필요한 두 필드의 도메인 모델로 축소한다.
    override suspend fun searchStocks(query: String, limit: Int): StockSearchResult {
        val trace = AppLogger.requestStarted("APP", "stock_search")
        return try {
            val suggestions = dartCorpCodeDataSource.searchCompanies(query, limit).map {
                StockSearchSuggestion(
                    stockCode = it.stockCode,
                    companyName = it.corpName
                )
            }
            AppLogger.requestSucceeded(trace, suggestions.size)
            StockSearchResult.Success(suggestions)
        } catch (cancellation: CancellationException) {
            AppLogger.requestCancelled(trace)
            throw cancellation
        } catch (error: ApiCallException) {
            AppLogger.requestFailed(trace, error.error)
            StockSearchResult.Failure(error.error)
        } catch (_: Exception) {
            AppLogger.requestFailed(trace, AppError.Unknown)
            StockSearchResult.Failure(AppError.Unknown)
        }
    }

    // 앱 시작 시 카탈로그를 미리 로드하고 UI에는 준비된 상장 종목 수만 공개한다.
    override suspend fun preloadStockCatalog(): StockCatalogResult {
        val trace = AppLogger.requestStarted("APP", "stock_catalog_preload")
        return try {
            val stockCount = dartCorpCodeDataSource.preloadCompanies()
            AppLogger.requestSucceeded(trace, stockCount)
            StockCatalogResult.Success(stockCount)
        } catch (cancellation: CancellationException) {
            AppLogger.requestCancelled(trace)
            throw cancellation
        } catch (error: ApiCallException) {
            AppLogger.requestFailed(trace, error.error)
            StockCatalogResult.Failure(error.error)
        } catch (_: Exception) {
            AppLogger.requestFailed(trace, AppError.Unknown)
            StockCatalogResult.Failure(AppError.Unknown)
        }
    }

    /*
     * OpenDART 조회는 KIS 현재가와 동시에 시작한다. KIS 호출은 공급자 제한을 고려해 100ms 간격으로 순차 실행한다.
     * 마지막에 Mapper가 네 응답의 단위·기간을 정리하고 부분 누락 정보를 포함한 StockAnalysis를 만든다.
     */
    private suspend fun loadAnalysis(stockCode: String) = coroutineScope {
        val dartCompany = async {
            optional { dartCorpCodeDataSource.findCompany(stockCode) }
        }
        val today = LocalDate.now(clock)
        val price = kisApiClient.getCurrentPrice(stockCode)
        delay(REQUEST_SPACING_MILLIS)
        val chart = optional { loadPriceHistory(stockCode, today) }
        delay(REQUEST_SPACING_MILLIS)
        val ratios = optional { kisApiClient.getFinancialRatios(stockCode) }
        delay(REQUEST_SPACING_MILLIS)
        val incomeStatements = optional { kisApiClient.getIncomeStatements(stockCode) }
        StockDataMapper.map(
            stockCode = stockCode,
            price = price,
            chart = chart,
            financialRatios = ratios,
            incomeStatements = incomeStatements,
            dartCompany = dartCompany.await(),
            priceAsOf = LocalDateTime.now(clock)
        )
    }

    private suspend fun loadPriceHistory(
        stockCode: String,
        today: LocalDate
    ): KisChartDto {
        val earliestDate = today.minusYears(FORECAST_LOOKBACK_YEARS)
        val points = mutableListOf<KisChartPointDto>()
        var companyName: String? = null
        var endDate = today
        while (!endDate.isBefore(earliestDate)) {
            val startDate = maxOf(
                earliestDate,
                endDate.minusDays(HISTORY_CHUNK_DAYS - 1L)
            )
            val chart = try {
                kisApiClient.getDailyChart(
                    stockCode = stockCode,
                    startDate = startDate.format(API_DATE_FORMAT),
                    endDate = endDate.format(API_DATE_FORMAT)
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: ApiCallException) {
                if (points.isEmpty()) throw error
                break
            }
            companyName = companyName ?: chart.companyName
            points += chart.points
            if (startDate == earliestDate) break
            endDate = startDate.minusDays(1L)
            delay(REQUEST_SPACING_MILLIS)
        }
        return KisChartDto(companyName = companyName, points = points)
    }

    // 선택 데이터 API 실패는 null로 낮추지만 취소는 사용자 새 요청의 의미이므로 삼키지 않는다.
    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: ApiCallException) {
        null
    }

    private data class CacheEntry(
        val analysis: com.chlqudco.kvalue.domain.model.StockAnalysis,
        val savedAtEpochMillis: Long
    )

    private companion object {
        val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
        val API_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        const val FORECAST_LOOKBACK_YEARS = 3L
        const val HISTORY_CHUNK_DAYS = 120L
        const val REQUEST_SPACING_MILLIS = 100L
        const val CACHE_LIFETIME_MILLIS = 60_000L
    }
}
