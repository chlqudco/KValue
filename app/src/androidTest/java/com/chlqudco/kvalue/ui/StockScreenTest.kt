/*
 * StockScreen에 완성된 상태를 직접 주입해 핵심 사용자 흐름을 검증하는 Compose UI 테스트다.
 * 자동완성 후보 선택 콜백과 조회 결과의 핵심 카드가 실제 semantics 트리에 나타나는지 확인한다.
 * 네트워크와 ViewModel 없이 UI만 격리하므로 실패 원인을 화면 렌더링과 이벤트 연결 범위로 좁힐 수 있다.
 */
package com.chlqudco.kvalue.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.chlqudco.kvalue.MainActivity
import com.chlqudco.kvalue.data.SampleStockData
import com.chlqudco.kvalue.domain.HistoricalForecastCalculator
import com.chlqudco.kvalue.domain.SupportResistanceCalculator
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
import com.chlqudco.kvalue.ui.theme.KValueTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StockScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun displaysAndSelectsStockNameSuggestion() {
        val suggestion = StockSearchSuggestion("005930", "삼성전자")
        var selected: StockSearchSuggestion? = null
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                KValueTheme {
                    StockScreen(
                        state = StockUiState(
                            query = "삼성",
                            catalog = StockCatalogState.Ready(3927),
                            suggestions = StockSuggestionState.Results(listOf(suggestion))
                        ),
                        onQueryChanged = {},
                        onSearch = {},
                        onSuggestionSelected = { selected = it },
                        onRefresh = {},
                        onOpenDart = { true }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("stock_search_suggestions").assertIsDisplayed()
        composeRule.onNodeWithTag("stock_catalog_status").assertIsDisplayed()
        composeRule.onNodeWithTag("stock_suggestion_005930").performClick()
        composeRule.runOnIdle { assertEquals(suggestion, selected) }
    }

    @Test
    fun displaysCoreStockInformationAndHistoricalForecast() {
        val analysis = SampleStockData.samsungElectronics()
        val forecast = HistoricalForecastCalculator.calculate(
            history = analysis.forecastHistory,
            currentPrice = analysis.price.currentPrice
        )
        val supportResistance = SupportResistanceCalculator.calculate(
            priceHistory = analysis.priceHistory,
            currentPrice = analysis.price.currentPrice
        )
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                KValueTheme {
                    StockScreen(
                        state = StockUiState(
                            query = analysis.companyName,
                            catalog = StockCatalogState.Ready(3927),
                            content = StockContentState.Success(
                                analysis = analysis,
                                forecast = forecast,
                                supportResistance = supportResistance
                            )
                        ),
                        onQueryChanged = {},
                        onSearch = {},
                        onSuggestionSelected = {},
                        onRefresh = {},
                        onOpenDart = { true }
                    )
                }
            }
        }

        composeRule.onNodeWithText("최근 100거래일").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("support_resistance_indicator")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("support_level_1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("resistance_level_1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("historical_forecast_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("forecast_price_range").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("forecast_direction_status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("핵심 재무지표").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dart_open_button").performScrollTo().assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("종합 참고 분석").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("단순 PER 참고가").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("S-RIM 참고가").fetchSemanticsNodes().isEmpty()
        )
    }
}
