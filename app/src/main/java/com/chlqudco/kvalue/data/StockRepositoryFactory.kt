package com.chlqudco.kvalue.data

import android.content.Context
import com.chlqudco.kvalue.BuildConfig
import com.chlqudco.kvalue.common.AppLogger
import com.chlqudco.kvalue.data.remote.DartCorpCodeDataSource
import com.chlqudco.kvalue.data.remote.KisApiClient
import com.chlqudco.kvalue.data.remote.KisTokenStore
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object StockRepositoryFactory {
    fun create(context: Context): StockRepository {
        val hasApiConfiguration = hasApiConfiguration()
        AppLogger.repositorySelected(realApi = hasApiConfiguration)
        if (!hasApiConfiguration) return SampleStockRepository()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        val tokenStore = KisTokenStore(
            context.getSharedPreferences("kis_token_store", Context.MODE_PRIVATE)
        )
        val kisApiClient = KisApiClient(
            appKey = BuildConfig.KIS_APP_KEY,
            appSecret = BuildConfig.KIS_APP_SECRET,
            tokenStore = tokenStore,
            httpClient = client
        )
        val dartDataSource = DartCorpCodeDataSource(
            apiKey = BuildConfig.OPEN_DART_API_KEY,
            cacheDirectory = File(context.cacheDir, "opendart"),
            httpClient = client
        )
        return KisDartStockRepository(kisApiClient, dartDataSource)
    }

    private fun hasApiConfiguration(): Boolean =
        BuildConfig.KIS_APP_KEY.isNotBlank() &&
            BuildConfig.KIS_APP_SECRET.isNotBlank() &&
            BuildConfig.OPEN_DART_API_KEY.isNotBlank()
}
