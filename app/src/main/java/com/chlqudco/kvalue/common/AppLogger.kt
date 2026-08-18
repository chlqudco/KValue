/*
 * 외부 API 호출과 분석 흐름을 구조화된 한 줄 로그로 남기는 진단 도구다.
 * 시작 시각을 LogTrace에 보관했다가 성공·실패·취소 시 지연시간과 오류 범주를 기록한다.
 * 키, 토큰, 사용자 입력 원문, API 응답 본문은 받지 않으며 값도 허용 문자와 최대 길이로 제한한다.
 * release 빌드에서는 emit가 즉시 반환하므로 현재 구현의 진단 로그는 debug 빌드에만 출력된다.
 */
package com.chlqudco.kvalue.common

import android.os.SystemClock
import android.util.Log
import com.chlqudco.kvalue.BuildConfig

internal data class LogTrace(
    val provider: String,
    val operation: String,
    val stockCode: String?,
    val startedAtMillis: Long
)

internal object AppLogger {
    fun requestStarted(
        provider: String,
        operation: String,
        stockCode: String? = null
    ): LogTrace {
        val trace = LogTrace(
            provider = provider,
            operation = operation,
            stockCode = stockCode,
            startedAtMillis = SystemClock.elapsedRealtime()
        )
        emit(
            priority = Log.INFO,
            event = "request_start",
            fields = listOf(
                "provider" to provider,
                "operation" to operation,
                "stock" to stockCode
            )
        )
        return trace
    }

    fun requestSucceeded(trace: LogTrace, itemCount: Int? = null) {
        emit(
            priority = Log.INFO,
            event = "request_success",
            fields = listOf(
                "provider" to trace.provider,
                "operation" to trace.operation,
                "stock" to trace.stockCode,
                "duration_ms" to duration(trace),
                "item_count" to itemCount
            )
        )
    }

    fun requestFailed(trace: LogTrace, error: AppError) {
        emit(
            priority = Log.WARN,
            event = "request_failure",
            fields = listOf(
                "provider" to trace.provider,
                "operation" to trace.operation,
                "stock" to trace.stockCode,
                "duration_ms" to duration(trace),
                "error" to error.code()
            )
        )
    }

    fun requestCancelled(trace: LogTrace) {
        emit(
            priority = Log.INFO,
            event = "request_cancelled",
            fields = listOf(
                "provider" to trace.provider,
                "operation" to trace.operation,
                "stock" to trace.stockCode,
                "duration_ms" to duration(trace)
            )
        )
    }

    fun requestRetry(
        provider: String,
        operation: String,
        attempt: Int,
        error: AppError
    ) {
        emit(
            priority = Log.WARN,
            event = "request_retry",
            fields = listOf(
                "provider" to provider,
                "operation" to operation,
                "attempt" to attempt,
                "error" to error.code()
            )
        )
    }

    fun cacheHit(
        provider: String,
        operation: String,
        source: String,
        stockCode: String? = null
    ) {
        emit(
            priority = Log.DEBUG,
            event = "cache_hit",
            fields = listOf(
                "provider" to provider,
                "operation" to operation,
                "source" to source,
                "stock" to stockCode
            )
        )
    }

    fun repositorySelected(realApi: Boolean) {
        emit(
            priority = Log.INFO,
            event = "repository_selected",
            fields = listOf("mode" to if (realApi) "real_api" else "sample")
        )
    }

    fun analysisStarted(stockCode: String, forceRefresh: Boolean): Long {
        val startedAtMillis = SystemClock.elapsedRealtime()
        emit(
            priority = Log.INFO,
            event = "analysis_start",
            fields = listOf(
                "stock" to stockCode,
                "force_refresh" to forceRefresh
            )
        )
        return startedAtMillis
    }

    fun analysisSucceeded(
        stockCode: String,
        missingSectionCount: Int,
        startedAtMillis: Long
    ) {
        emit(
            priority = Log.INFO,
            event = "analysis_success",
            fields = listOf(
                "stock" to stockCode,
                "duration_ms" to elapsedSince(startedAtMillis),
                "missing_sections" to missingSectionCount
            )
        )
    }

    fun analysisFailed(stockCode: String, error: AppError, startedAtMillis: Long) {
        emit(
            priority = Log.WARN,
            event = "analysis_failure",
            fields = listOf(
                "stock" to stockCode,
                "duration_ms" to elapsedSince(startedAtMillis),
                "error" to error.code()
            )
        )
    }

    fun analysisCancelled(stockCode: String, startedAtMillis: Long) {
        emit(
            priority = Log.INFO,
            event = "analysis_cancelled",
            fields = listOf(
                "stock" to stockCode,
                "duration_ms" to elapsedSince(startedAtMillis)
            )
        )
    }

    private fun duration(trace: LogTrace): Long =
        elapsedSince(trace.startedAtMillis)

    private fun elapsedSince(startedAtMillis: Long): Long =
        (SystemClock.elapsedRealtime() - startedAtMillis).coerceAtLeast(0L)

    /*
     * release에서는 즉시 종료하고 debug에서만 key=value 형식의 한 줄 로그를 만든다.
     * safeValue가 공백·개행·특수문자를 치환하고 64자로 잘라 로그 주입과 과도한 원문 노출을 줄인다.
     */
    private fun emit(
        priority: Int,
        event: String,
        fields: List<Pair<String, Any?>>
    ) {
        if (!BuildConfig.DEBUG) return
        val message = buildString {
            append("event=")
            append(event.safeValue())
            fields.forEach { (name, value) ->
                if (value != null) {
                    append(' ')
                    append(name.safeValue())
                    append('=')
                    append(value.toString().safeValue())
                }
            }
        }
        Log.println(priority, TAG, message)
    }

    private fun String.safeValue(): String =
        replace(UNSAFE_VALUE, "_").take(MAX_VALUE_LENGTH)

    private fun AppError.code(): String = when (this) {
        AppError.InvalidInput -> "invalid_input"
        AppError.StockNotFound -> "stock_not_found"
        AppError.NetworkUnavailable -> "network_unavailable"
        AppError.Timeout -> "timeout"
        AppError.Unauthorized -> "unauthorized"
        AppError.RateLimited -> "rate_limited"
        AppError.ServiceUnavailable -> "service_unavailable"
        is AppError.PartialData -> "partial_data"
        AppError.Unknown -> "unknown"
    }

    private const val TAG = "KValue"
    private const val MAX_VALUE_LENGTH = 64
    private val UNSAFE_VALUE = Regex("[^A-Za-z0-9_.-]")
}
