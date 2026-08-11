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
import com.chlqudco.kvalue.data.SampleStockRepository
import com.chlqudco.kvalue.ui.StockScreen
import com.chlqudco.kvalue.ui.StockViewModel
import com.chlqudco.kvalue.ui.theme.KValueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KValueTheme {
                val repository = remember { SampleStockRepository() }
                val stockViewModel: StockViewModel = viewModel(
                    factory = StockViewModel.factory(repository)
                )
                val state by stockViewModel.uiState.collectAsStateWithLifecycle()
                StockScreen(
                    state = state,
                    onQueryChanged = stockViewModel::onQueryChanged,
                    onSearch = stockViewModel::search,
                    onRefresh = stockViewModel::refresh,
                    onPerChanged = stockViewModel::onPerChanged,
                    onOpenDart = ::openExternalUrl
                )
            }
        }
    }

    private fun openExternalUrl(url: String): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
