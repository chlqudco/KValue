/*
 * 한국투자증권 Open API의 인증과 국내주식 조회 요청을 담당하는 저수준 클라이언트다.
 * 현재가, 수정주가 일봉, 재무비율, 손익계산서 응답을 필요한 DTO로 변환한다.
 * 접근 토큰은 Mutex와 메모리·SharedPreferences 캐시로 중복 발급을 막고 인증 실패 시 한 번 갱신한다.
 * 서버·네트워크 오류는 제한적으로 재시도하며 HTTP/공급자 오류를 사용자 독립적인 AppError로 매핑한다.
 */
package com.chlqudco.kvalue.data.remote

import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.common.AppLogger
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl

internal const val KIS_ADJUSTED_PRICE_CODE = "0"

internal class KisApiClient(
    private val appKey: String,
    private val appSecret: String,
    private val tokenStore: KisTokenStore,
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    private val json = Json { isLenient = true }
    private val baseUrl = "https://openapi.koreainvestment.com:9443".toHttpUrl()
    private val tokenMutex = Mutex()

    @Volatile
    private var memoryToken: StoredKisToken? = null

    // 현재가 응답의 output 객체에서 가격과 화면에 바로 필요한 기본 재무 필드를 추출한다.
    suspend fun getCurrentPrice(stockCode: String): KisPriceDto = traced(
        operation = "current_price",
        stockCode = stockCode
    ) {
        val root = authorizedGet(
            path = "/uapi/domestic-stock/v1/quotations/inquire-price",
            transactionId = "FHKST01010100",
            operation = "current_price",
            parameters = linkedMapOf(
                "FID_COND_MRKT_DIV_CODE" to "J",
                "FID_INPUT_ISCD" to stockCode
            )
        )
        val output = root.objectValue("output")
            ?: throw ApiCallException(AppError.StockNotFound)
        KisPriceDto(
            stockCode = output.string("stck_shrn_iscd"),
            sectorName = output.string("bstp_kor_isnm"),
            marketName = output.string("rprs_mrkt_kor_name"),
            fiscalClosingMonth = output.string("stac_month"),
            currentPrice = output.string("stck_prpr"),
            changeRate = output.string("prdy_ctrt"),
            eps = output.string("eps"),
            per = output.string("per"),
            pbr = output.string("pbr"),
            bps = output.string("bps")
        )
    }

    // 지정 기간의 일봉 배열을 OHLCV 문자열 DTO로 옮긴다. 수정주가 요청 여부는 FID_ORG_ADJ_PRC로 전달한다.
    suspend fun getDailyChart(
        stockCode: String,
        startDate: String,
        endDate: String
    ): KisChartDto = traced(
        operation = "daily_chart",
        stockCode = stockCode,
        itemCount = { it.points.size }
    ) {
        val root = authorizedGet(
            path = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice",
            transactionId = "FHKST03010100",
            operation = "daily_chart",
            parameters = linkedMapOf(
                "FID_COND_MRKT_DIV_CODE" to "J",
                "FID_INPUT_ISCD" to stockCode,
                "FID_INPUT_DATE_1" to startDate,
                "FID_INPUT_DATE_2" to endDate,
                "FID_PERIOD_DIV_CODE" to "D",
                "FID_ORG_ADJ_PRC" to KIS_ADJUSTED_PRICE_CODE
            )
        )
        val summary = root.objectValue("output1")
        val points = root.arrayValue("output2").mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            KisChartPointDto(
                date = row.string("stck_bsop_date"),
                close = row.string("stck_clpr"),
                open = row.string("stck_oprc"),
                high = row.string("stck_hgpr"),
                low = row.string("stck_lwpr"),
                volume = row.string("acml_vol")
            )
        }
        KisChartDto(
            companyName = summary?.string("hts_kor_isnm"),
            points = points
        )
    }

    // 여러 결산기간의 EPS·BPS·ROE를 반환하며 최신 연간 행 선택은 Mapper에 맡긴다.
    suspend fun getFinancialRatios(stockCode: String): List<KisFinancialRatioDto> = traced(
        operation = "financial_ratios",
        stockCode = stockCode,
        itemCount = { it.size }
    ) {
        val root = authorizedGet(
            path = "/uapi/domestic-stock/v1/finance/financial-ratio",
            transactionId = "FHKST66430300",
            operation = "financial_ratios",
            parameters = linkedMapOf(
                "FID_DIV_CLS_CODE" to "0",
                "fid_cond_mrkt_div_code" to "J",
                "fid_input_iscd" to stockCode
            )
        )
        root.arrayValue("output").mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            KisFinancialRatioDto(
                reportingPeriod = row.string("stac_yymm"),
                eps = row.string("eps"),
                bps = row.string("bps"),
                roe = row.string("roe_val")
            )
        }
    }

    // 공급자 손익 값은 억원 단위 문자열이므로 여기서는 변환하지 않고 DTO에 그대로 보존한다.
    suspend fun getIncomeStatements(stockCode: String): List<KisIncomeStatementDto> = traced(
        operation = "income_statements",
        stockCode = stockCode,
        itemCount = { it.size }
    ) {
        val root = authorizedGet(
            path = "/uapi/domestic-stock/v1/finance/income-statement",
            transactionId = "FHKST66430200",
            operation = "income_statements",
            parameters = linkedMapOf(
                "FID_DIV_CLS_CODE" to "0",
                "fid_cond_mrkt_div_code" to "J",
                "fid_input_iscd" to stockCode
            )
        )
        root.arrayValue("output").mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            KisIncomeStatementDto(
                reportingPeriod = row.string("stac_yymm"),
                revenue = row.string("sale_account"),
                operatingIncome = row.string("bsop_prti"),
                netIncome = row.string("thtr_ntin")
            )
        }
    }

    /*
     * 인증 헤더가 필요한 GET 요청의 공통 경로다.
     * 첫 요청이 인증 오류면 저장된 토큰을 지우고 새 토큰으로 정확히 한 번 더 시도한다.
     */
    private suspend fun authorizedGet(
        path: String,
        transactionId: String,
        operation: String,
        parameters: Map<String, String>
    ): JsonObject {
        repeat(2) { attempt ->
            val token = accessToken(forceRefresh = attempt > 0)
            val request = Request.Builder()
                .url(buildUrl(path, parameters))
                .header("authorization", "Bearer ${token.value}")
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", transactionId)
                .header("custtype", "P")
                .header("Accept", "application/json")
                .get()
                .build()
            try {
                return executeJson(
                    request = request,
                    retryServerErrors = true,
                    operation = operation
                )
            } catch (error: ApiCallException) {
                if (error.error != AppError.Unauthorized || attempt == 1) throw error
                AppLogger.requestRetry(
                    provider = "KIS",
                    operation = operation,
                    attempt = attempt + 2,
                    error = error.error
                )
                invalidateToken()
            }
        }
        throw ApiCallException(AppError.Unauthorized)
    }

    /*
     * 동시에 여러 API가 토큰을 요구해도 Mutex 안에서 메모리 → 영속 저장소 → 신규 발급 순으로 한 번만 결정한다.
     * forceRefresh는 인증 실패 뒤 기존 두 캐시를 모두 무효화하는 명시적인 복구 경로다.
     */
    private suspend fun accessToken(forceRefresh: Boolean): StoredKisToken = tokenMutex.withLock {
        if (forceRefresh) {
            memoryToken = null
            tokenStore.clear()
        }
        val now = System.currentTimeMillis()
        memoryToken?.takeIf { it.isUsable(now) }?.let {
            AppLogger.cacheHit("KIS", "access_token", "memory")
            return@withLock it
        }
        tokenStore.read()?.takeIf { it.isUsable(now) }?.let {
            memoryToken = it
            AppLogger.cacheHit("KIS", "access_token", "private_storage")
            return@withLock it
        }
        requestToken(now).also {
            memoryToken = it
            tokenStore.write(it)
        }
    }

    // client_credentials 요청으로 토큰을 발급하고 만료 60초 전까지만 사용 가능한 시각으로 저장한다.
    private suspend fun requestToken(now: Long): StoredKisToken = traced(
        operation = "access_token",
        stockCode = null
    ) {
        val body = buildJsonObject {
            put("grant_type", JsonPrimitive("client_credentials"))
            put("appkey", JsonPrimitive(appKey))
            put("appsecret", JsonPrimitive(appSecret))
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments("oauth2/tokenP").build())
            .header("Accept", "application/json")
            .post(body)
            .build()
        val root = executeJson(
            request = request,
            retryServerErrors = false,
            operation = "access_token"
        )
        val value = root.string("access_token")
            ?.takeIf(String::isNotBlank)
            ?: throw ApiCallException(AppError.Unauthorized)
        val lifetimeSeconds = root["expires_in"]?.jsonPrimitive?.longOrNull ?: 86_400L
        val usableLifetime = (lifetimeSeconds - TOKEN_EXPIRY_BUFFER_SECONDS).coerceAtLeast(60L)
        StoredKisToken(
            value = value,
            expiresAtEpochMillis = now + TimeUnit.SECONDS.toMillis(usableLifetime)
        )
    }

    private fun invalidateToken() {
        memoryToken = null
        tokenStore.clear()
    }

    private fun buildUrl(path: String, parameters: Map<String, String>): HttpUrl {
        val builder = baseUrl.newBuilder().addPathSegments(path.trimStart('/'))
        parameters.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    // 원시 HTTP 응답을 JSON 객체로 파싱하고 HTTP 상태와 KIS rt_cd를 모두 성공 조건으로 확인한다.
    private suspend fun executeJson(
        request: Request,
        retryServerErrors: Boolean,
        operation: String
    ): JsonObject {
        val response = executeRaw(request, retryServerErrors, operation)
        val root = runCatching {
            json.parseToJsonElement(response.body) as? JsonObject
        }.getOrNull()
        if (response.statusCode !in 200..299) {
            throw ApiCallException(mapError(response.statusCode, root))
        }
        root ?: throw ApiCallException(AppError.Unknown)
        if (root.string("rt_cd")?.let { it != "0" } == true) {
            throw ApiCallException(mapError(response.statusCode, root))
        }
        return root
    }

    /*
     * IOException과 5xx 응답만 지수형에 가까운 짧은 지연 후 최대 세 번 시도한다.
     * 인증·입력·호출 제한 같은 재시도로 해결되지 않는 오류는 상위 계층이 즉시 처리하게 한다.
     */
    private suspend fun executeRaw(
        request: Request,
        retryServerErrors: Boolean,
        operation: String
    ): RawHttpResponse {
        val attempts = if (retryServerErrors) 3 else 1
        repeat(attempts) { attempt ->
            val response = try {
                httpClient.newCall(request).await()
            } catch (error: IOException) {
                if (attempt + 1 < attempts) {
                    AppLogger.requestRetry(
                        provider = "KIS",
                        operation = operation,
                        attempt = attempt + 2,
                        error = error.toAppError()
                    )
                    delay(RETRY_DELAYS[attempt])
                    return@repeat
                }
                throw ApiCallException(error.toAppError())
            }
            if (response.statusCode >= 500 && attempt + 1 < attempts) {
                AppLogger.requestRetry(
                    provider = "KIS",
                    operation = operation,
                    attempt = attempt + 2,
                    error = AppError.ServiceUnavailable
                )
                delay(RETRY_DELAYS[attempt])
            } else {
                return response
            }
        }
        throw ApiCallException(AppError.ServiceUnavailable)
    }

    // 각 공급자 요청에 동일한 시작·성공·취소·실패 로그 형식을 적용하는 고차 함수다.
    private suspend fun <T> traced(
        operation: String,
        stockCode: String?,
        itemCount: (T) -> Int? = { null },
        block: suspend () -> T
    ): T {
        val trace = AppLogger.requestStarted("KIS", operation, stockCode)
        return try {
            block().also { AppLogger.requestSucceeded(trace, itemCount(it)) }
        } catch (cancellation: CancellationException) {
            AppLogger.requestCancelled(trace)
            throw cancellation
        } catch (error: ApiCallException) {
            AppLogger.requestFailed(trace, error.error)
            throw error
        } catch (error: Exception) {
            AppLogger.requestFailed(trace, AppError.Unknown)
            throw error
        }
    }

    // KIS 오류 코드·한국어 설명·HTTP 상태를 우선순위에 따라 앱의 안정적인 오류 범주로 축소한다.
    private fun mapError(statusCode: Int, root: JsonObject?): AppError {
        val code = root?.string("msg_cd") ?: root?.string("error_code").orEmpty()
        val description = root?.string("msg1") ?: root?.string("error_description").orEmpty()
        return when {
            code == "EGW00133" || code == "EGW00201" -> AppError.RateLimited
            description.contains("초당") || description.contains("거래건수") ||
                description.contains("1분당") -> AppError.RateLimited
            description.contains("토큰") || description.contains("credentials") ||
                description.contains("인증") -> AppError.Unauthorized
            description.contains("조회할 자료") || description.contains("종목코드") -> {
                AppError.StockNotFound
            }
            statusCode == 401 || statusCode == 403 -> AppError.Unauthorized
            statusCode == 408 -> AppError.Timeout
            statusCode == 429 -> AppError.RateLimited
            statusCode == 404 -> AppError.StockNotFound
            statusCode >= 500 -> AppError.ServiceUnavailable
            else -> AppError.Unknown
        }
    }

    /*
     * OkHttp Callback API를 일시 중단 함수로 연결한다.
     * 코루틴이 취소되면 진행 중인 Call도 취소해 네트워크 작업과 화면 생명주기가 함께 끝난다.
     */
    private suspend fun Call.await(): RawHttpResponse = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        RawHttpResponse(it.code, it.body.string())
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private fun StoredKisToken.isUsable(now: Long): Boolean =
        value.isNotBlank() && expiresAtEpochMillis > now

    private fun IOException.toAppError(): AppError = when (this) {
        is SocketTimeoutException -> AppError.Timeout
        is UnknownHostException,
        is ConnectException,
        is NoRouteToHostException,
        is SSLException -> AppError.NetworkUnavailable
        else -> AppError.NetworkUnavailable
    }

    private data class RawHttpResponse(
        val statusCode: Int,
        val body: String
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val RETRY_DELAYS = longArrayOf(400L, 900L)
        const val TOKEN_EXPIRY_BUFFER_SECONDS = 60L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.objectValue(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.arrayValue(name: String): JsonArray =
    this[name] as? JsonArray ?: JsonArray(emptyList())
