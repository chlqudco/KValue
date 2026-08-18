/*
 * 앱 실행 시 사용할 StockRepository 구현을 조립하는 팩토리다.
 * 세 API 키가 모두 있으면 KIS·OpenDART 실데이터 구현을 만들고, 하나라도 없으면 샘플 구현을 선택한다.
 * HTTP 타임아웃, 토큰 저장소, OpenDART 캐시 디렉터리 같은 Android 의존 객체도 이 경계에서 생성한다.
 * 화면과 ViewModel은 구체 구현이나 비밀값의 존재 여부를 알 필요 없이 StockRepository만 사용한다.
 */
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
        // 모든 키가 갖춰진 경우에만 실제 모드를 선택해 불완전한 인증 설정으로 요청하지 않는다.
        val hasApiConfiguration = hasApiConfiguration()
        AppLogger.repositorySelected(realApi = hasApiConfiguration)
        if (!hasApiConfiguration) return SampleStockRepository()
        // 공급자 지연이 화면을 무기한 붙잡지 않도록 연결·읽기·전체 호출 시간을 각각 제한한다.
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
