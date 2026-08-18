package com.chlqudco.kvalue.data

import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.common.AppLogger
import com.chlqudco.kvalue.data.mapper.StockDataMapper
import com.chlqudco.kvalue.data.remote.ApiCallException
import com.chlqudco.kvalue.data.remote.DartCorpCodeDataSource
import com.chlqudco.kvalue.data.remote.KisApiClient
import com.chlqudco.kvalue.domain.model.SupportStatus
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
                        supported = it.analysis.support is SupportStatus.Supported,
                        startedAtMillis = startedAtMillis
                    )
                    return StockAnalysisResult.Success(it.analysis)
                }
        }
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
                supported = analysis.support is SupportStatus.Supported,
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

    private suspend fun loadAnalysis(stockCode: String) = coroutineScope {
        val dartCompany = async {
            optional { dartCorpCodeDataSource.findCompany(stockCode) }
        }
        val today = LocalDate.now(clock)
        val price = kisApiClient.getCurrentPrice(stockCode)
        delay(REQUEST_SPACING_MILLIS)
        val chart = optional {
            kisApiClient.getDailyChart(
                stockCode = stockCode,
                startDate = today.minusDays(CHART_LOOKBACK_DAYS).format(API_DATE_FORMAT),
                endDate = today.format(API_DATE_FORMAT)
            )
        }
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
        const val CHART_LOOKBACK_DAYS = 220L
        const val REQUEST_SPACING_MILLIS = 100L
        const val CACHE_LIFETIME_MILLIS = 60_000L
    }
}
