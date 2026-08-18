/*
 * 앱의 단일 진입점이다.
 * Compose 화면을 만들고 Repository와 ViewModel을 연결한 뒤 StateFlow의 상태를 화면에 전달한다.
 * Activity는 데이터 조회나 계산을 직접 하지 않고, Android 시스템이 필요한 외부 브라우저 실행만 맡는다.
 * 이 파일을 보면 의존성이 Repository → ViewModel → StockScreen 순서로 조립되는 과정을 이해할 수 있다.
 */
package com.chlqudco.kvalue

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chlqudco.kvalue.data.StockRepositoryFactory
import com.chlqudco.kvalue.ui.StockScreen
import com.chlqudco.kvalue.ui.StockViewModel
import com.chlqudco.kvalue.ui.theme.KValueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KValueTheme {
                // recomposition마다 Repository를 새로 만들지 않도록 Composition 생명주기 동안 기억한다.
                val repository = remember {
                    StockRepositoryFactory.create(applicationContext)
                }
                val stockViewModel: StockViewModel = viewModel(
                    factory = StockViewModel.factory(repository)
                )
                val state by stockViewModel.uiState.collectAsStateWithLifecycle()
                StockScreen(
                    state = state,
                    onQueryChanged = stockViewModel::onQueryChanged,
                    onSearch = stockViewModel::search,
                    onSuggestionSelected = stockViewModel::onSuggestionSelected,
                    onRefresh = stockViewModel::refresh,
                    onOpenDart = ::openExternalUrl
                )
            }
        }
    }

    // WebView를 앱 안에 두지 않고 ACTION_VIEW를 처리할 외부 브라우저에 위임하며 실패 여부만 UI에 돌려준다.
    private fun openExternalUrl(url: String): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
