/*
 * OpenDART 고유번호 ZIP을 내려받아 상장 종목 검색과 종목별 DART 연결 정보를 제공한다.
 * 원본 ZIP은 7일간 앱 캐시에 보존하고, 상장사만 추린 TSV 인덱스를 만들어 다음 실행의 파싱 비용을 줄인다.
 * Mutex로 동시 초기화를 한 번만 수행하며 새 다운로드가 실패하면 사용 가능한 이전 캐시를 재사용한다.
 * 네트워크·HTTP·OpenDART 상태 코드는 AppError로 정규화하고 코루틴 취소는 끝까지 전파한다.
 */
package com.chlqudco.kvalue.data.remote

import android.util.Xml
import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.common.AppLogger
import com.chlqudco.kvalue.domain.StockSearchMatcher
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser

internal data class DartCompanyDto(
    val corpCode: String,
    val corpName: String,
    val stockCode: String,
    val modifiedDate: String
)

internal class DartCorpCodeDataSource(
    private val apiKey: String,
    private val cacheDirectory: File,
    private val httpClient: OkHttpClient
) {
    private val loadMutex = Mutex()

    @Volatile
    private var companies: Map<String, DartCompanyDto>? = null

    // 이미 준비된 상장사 Map에서 6자리 종목코드를 키로 회사 정보를 찾는다.
    suspend fun findCompany(stockCode: String): DartCompanyDto? = traced(
        operation = "corp_code_lookup",
        stockCode = stockCode,
        itemCount = { if (it == null) 0 else 1 }
    ) {
        try {
            loadCompanies()[stockCode]
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: ApiCallException) {
            throw error
        } catch (_: Exception) {
            throw ApiCallException(AppError.ServiceUnavailable)
        }
    }

    // 공통 StockSearchMatcher로 순위를 계산한 뒤 다시 DART 회사 DTO에 연결한다.
    suspend fun searchCompanies(query: String, limit: Int): List<DartCompanyDto> = traced(
        operation = "corp_code_search",
        stockCode = null,
        itemCount = List<DartCompanyDto>::size
    ) {
        val companiesByCode = loadCompanies()
        val suggestions = companiesByCode.values.map {
            StockSearchSuggestion(
                stockCode = it.stockCode,
                companyName = it.corpName
            )
        }
        val matches = StockSearchMatcher.find(suggestions, query, limit)
        matches.mapNotNull { companiesByCode[it.stockCode] }
    }

    // 앱 시작 프리로드는 전체 목록을 메모리에 올리고 준비된 상장사 개수만 반환한다.
    suspend fun preloadCompanies(): Int = loadCompanies().size

    /*
     * 프로세스 메모리, 디스크 인덱스, ZIP 파싱 순으로 가장 비용이 낮은 경로를 먼저 선택한다.
     * Mutex 덕분에 프리로드와 사용자 검색이 동시에 시작돼도 다운로드·파싱은 한 번만 수행된다.
     */
    private suspend fun loadCompanies(): Map<String, DartCompanyDto> = loadMutex.withLock {
        companies?.let {
            AppLogger.cacheHit("OpenDART", "corp_code_list", "memory")
            return@withLock it
        }
        val source = resolveSourceFile()
        traced(
            operation = "corp_code_parse",
            stockCode = null,
            itemCount = { it.size }
        ) {
            try {
                withContext(Dispatchers.IO) {
                    val indexFile = File(cacheDirectory, INDEX_FILE_NAME)
                    readCompanyIndex(indexFile, source) ?: parseCompanies(source).also {
                        writeCompanyIndex(indexFile, it)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: ApiCallException) {
                throw error
            } catch (_: Exception) {
                throw ApiCallException(AppError.ServiceUnavailable)
            }
        }.also {
            companies = it
        }
    }

    /*
     * 7일 이내 ZIP은 바로 사용하고 만료됐으면 새 원본을 임시 파일에 쓴 후 최종 파일로 교체한다.
     * 갱신 실패 시 기존 파일이 있으면 stale 캐시로 폴백해 종목 검색 기능을 유지한다.
     */
    private suspend fun resolveSourceFile(): File {
        val cacheFile = File(cacheDirectory, CACHE_FILE_NAME)
        val now = System.currentTimeMillis()
        if (cacheFile.isFile && now - cacheFile.lastModified() < CACHE_LIFETIME_MILLIS) {
            AppLogger.cacheHit("OpenDART", "corp_code_file", "private_storage")
            return cacheFile
        }
        return try {
            val bytes = downloadCorpCodes()
            withContext(Dispatchers.IO) {
                cacheDirectory.mkdirs()
                val temporaryFile = File(cacheDirectory, "$CACHE_FILE_NAME.tmp")
                temporaryFile.outputStream().use { it.write(bytes) }
                temporaryFile.copyTo(cacheFile, overwrite = true)
                temporaryFile.delete()
                cacheFile
            }
        } catch (error: ApiCallException) {
            if (cacheFile.isFile) {
                AppLogger.cacheHit("OpenDART", "corp_code_file", "stale_private_storage")
                cacheFile
            } else {
                throw error
            }
        }
    }

    // 응답 본문이 실제 ZIP 서명을 갖는지 확인하고 XML 오류 본문이면 OpenDART 상태 코드를 AppError로 바꾼다.
    private suspend fun downloadCorpCodes(): ByteArray = traced(
        operation = "corp_code_download",
        stockCode = null
    ) {
        val url = DART_BASE_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/corpCode.xml")
            .addQueryParameter("crtfc_key", apiKey)
            .build()
        val request = Request.Builder().url(url).get().build()
        val response = try {
            httpClient.newCall(request).awaitBytes()
        } catch (error: IOException) {
            throw ApiCallException(error.toAppError())
        }
        if (response.statusCode !in 200..299) {
            throw ApiCallException(
                when (response.statusCode) {
                    401, 403 -> AppError.Unauthorized
                    408 -> AppError.Timeout
                    429 -> AppError.RateLimited
                    in 500..599 -> AppError.ServiceUnavailable
                    else -> AppError.Unknown
                }
            )
        }
        if (!response.body.isZip()) {
            val status = response.body.toString(Charsets.UTF_8)
            throw ApiCallException(
                when {
                    "<status>020</status>" in status -> AppError.RateLimited
                    "<status>010</status>" in status || "<status>011</status>" in status -> {
                        AppError.Unauthorized
                    }
                    else -> AppError.ServiceUnavailable
                }
            )
        }
        response.body
    }

    private suspend fun <T> traced(
        operation: String,
        stockCode: String?,
        itemCount: (T) -> Int? = { null },
        block: suspend () -> T
    ): T {
        val trace = AppLogger.requestStarted("OpenDART", operation, stockCode)
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

    /*
     * ZIP 스트림 안의 XML을 전체 DOM으로 올리지 않고 PullParser로 한 항목씩 읽어 메모리 사용을 제한한다.
     * stock_code가 정확한 숫자 6자리인 상장사만 Map에 넣어 비상장 법인은 자동완성에서 제외한다.
     */
    private fun parseCompanies(file: File): Map<String, DartCompanyDto> {
        val result = mutableMapOf<String, DartCompanyDto>()
        ZipInputStream(BufferedInputStream(file.inputStream())).use { zipStream ->
            zipStream.nextEntry ?: throw ApiCallException(AppError.ServiceUnavailable)
            val parser = Xml.newPullParser().apply {
                setInput(zipStream, Charsets.UTF_8.name())
            }
            var corpCode = ""
            var corpName = ""
            var stockCode = ""
            var modifiedDate = ""
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "list" -> {
                            corpCode = ""
                            corpName = ""
                            stockCode = ""
                            modifiedDate = ""
                        }
                        "corp_code" -> corpCode = parser.nextText().trim()
                        "corp_name" -> corpName = parser.nextText().trim()
                        "stock_code" -> stockCode = parser.nextText().trim()
                        "modify_date" -> modifiedDate = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (
                        parser.name == "list" && stockCode.length == 6 &&
                        stockCode.all(Char::isDigit)
                    ) {
                        result[stockCode] = DartCompanyDto(
                            corpCode = corpCode,
                            corpName = corpName,
                            stockCode = stockCode,
                            modifiedDate = modifiedDate
                        )
                    }
                }
                event = parser.next()
            }
        }
        return result
    }

    // 원본 ZIP보다 최신인 TSV만 신뢰하고 각 행의 네 필드와 종목코드 형식을 다시 검증한다.
    private fun readCompanyIndex(
        indexFile: File,
        sourceFile: File
    ): Map<String, DartCompanyDto>? {
        if (!indexFile.isFile || indexFile.lastModified() < sourceFile.lastModified()) return null
        val result = linkedMapOf<String, DartCompanyDto>()
        indexFile.useLines(Charsets.UTF_8) { lines ->
            lines.forEach { line ->
                val values = line.split('\t', limit = 4)
                if (values.size != 4) return@forEach
                val stockCode = values[0]
                if (stockCode.length != 6 || !stockCode.all(Char::isDigit)) return@forEach
                result[stockCode] = DartCompanyDto(
                    corpCode = values[1],
                    modifiedDate = values[2],
                    corpName = values[3],
                    stockCode = stockCode
                )
            }
        }
        if (result.isEmpty()) return null
        AppLogger.cacheHit("OpenDART", "corp_code_index", "private_storage")
        return result
    }

    // 정렬된 경량 인덱스를 임시 파일에 완성한 뒤 교체해 중간 쓰기 실패로 기존 파일이 깨지는 위험을 줄인다.
    private fun writeCompanyIndex(
        indexFile: File,
        values: Map<String, DartCompanyDto>
    ) {
        runCatching {
            cacheDirectory.mkdirs()
            val temporaryFile = File(cacheDirectory, "$INDEX_FILE_NAME.tmp")
            temporaryFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                values.values.sortedBy(DartCompanyDto::stockCode).forEach { company ->
                    writer.append(company.stockCode)
                    writer.append('\t')
                    writer.append(company.corpCode)
                    writer.append('\t')
                    writer.append(company.modifiedDate)
                    writer.append('\t')
                    writer.append(company.corpName.replace('\t', ' ').replace('\n', ' '))
                    writer.newLine()
                }
            }
            temporaryFile.copyTo(indexFile, overwrite = true)
            temporaryFile.delete()
        }
    }

    // OkHttp의 비동기 바이트 응답을 취소 가능한 suspend 함수로 변환한다.
    private suspend fun Call.awaitBytes(): RawByteResponse =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            RawByteResponse(it.code, it.body.bytes())
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }

    private fun IOException.toAppError(): AppError = when (this) {
        is SocketTimeoutException -> AppError.Timeout
        is UnknownHostException,
        is ConnectException,
        is NoRouteToHostException,
        is SSLException -> AppError.NetworkUnavailable
        else -> AppError.NetworkUnavailable
    }

    private fun ByteArray.isZip(): Boolean =
        size >= 4 && this[0] == 0x50.toByte() && this[1] == 0x4b.toByte()

    private data class RawByteResponse(
        val statusCode: Int,
        val body: ByteArray
    )

    private companion object {
        const val DART_BASE_URL = "https://opendart.fss.or.kr"
        const val CACHE_FILE_NAME = "dart_corp_codes.zip"
        const val INDEX_FILE_NAME = "dart_listed_companies.tsv"
        val CACHE_LIFETIME_MILLIS = TimeUnit.DAYS.toMillis(7)
    }
}
