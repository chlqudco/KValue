package com.chlqudco.kvalue.data.remote

import android.util.Xml
import com.chlqudco.kvalue.common.AppError
import com.chlqudco.kvalue.common.AppLogger
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
                withContext(Dispatchers.IO) { parseCompanies(source) }
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
        val CACHE_LIFETIME_MILLIS = TimeUnit.DAYS.toMillis(7)
    }
}
