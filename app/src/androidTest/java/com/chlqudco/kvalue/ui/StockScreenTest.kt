package com.chlqudco.kvalue.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.chlqudco.kvalue.MainActivity
import com.chlqudco.kvalue.data.SampleStockData
import com.chlqudco.kvalue.domain.ComprehensiveAnalysisCalculator
import com.chlqudco.kvalue.domain.PerReferenceCalculator
import com.chlqudco.kvalue.domain.SrimValueCalculator
import com.chlqudco.kvalue.domain.model.PerAssumptions
import com.chlqudco.kvalue.domain.model.SrimAssumptions
import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
import com.chlqudco.kvalue.ui.theme.KValueTheme
import org.junit.Assert.assertEquals
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
                        onPerChanged = { _, _ -> },
                        onSrimChanged = { _, _ -> },
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
    fun displaysSrimReferenceValueCard() {
        val analysis = SampleStockData.samsungElectronics()
        val result = SrimValueCalculator.calculate(
            bps = requireNotNull(analysis.ratios.bps),
            assumptions = SrimAssumptions(
                returnOnEquityPercent = requireNotNull(analysis.ratios.roe),
                requiredReturnPercent = 10.0
            ),
            currentPrice = analysis.price.currentPrice
        )
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                KValueTheme {
                    StockScreen(
                        state = StockUiState(
                            query = analysis.companyName,
                            content = StockContentState.Success(analysis),
                            srimInputs = SrimInputFields("9.8", "10"),
                            srimValue = result
                        ),
                        onQueryChanged = {},
                        onSearch = {},
                        onSuggestionSelected = {},
                        onRefresh = {},
                        onPerChanged = { _, _ -> },
                        onSrimChanged = { _, _ -> },
                        onOpenDart = { true }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("srim_value_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("S-RIM 참고가").assertIsDisplayed()
    }

    @Test
    fun displaysComprehensiveReferenceAndTechnicalZones() {
        val analysis = SampleStockData.samsungElectronics()
        val per = PerReferenceCalculator.calculate(
            eps = requireNotNull(analysis.ratios.eps),
            assumptions = PerAssumptions(),
            currentPrice = analysis.price.currentPrice
        )
        val srim = SrimValueCalculator.calculate(
            bps = requireNotNull(analysis.ratios.bps),
            assumptions = SrimAssumptions(
                returnOnEquityPercent = requireNotNull(analysis.ratios.roe)
            ),
            currentPrice = analysis.price.currentPrice
        )
        val comprehensive = ComprehensiveAnalysisCalculator.calculate(analysis, per, srim)
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                KValueTheme {
                    StockScreen(
                        state = StockUiState(
                            query = analysis.companyName,
                            catalog = StockCatalogState.Ready(3927),
                            content = StockContentState.Success(analysis),
                            comprehensiveAnalysis = comprehensive
                        ),
                        onQueryChanged = {},
                        onSearch = {},
                        onSuggestionSelected = {},
                        onRefresh = {},
                        onPerChanged = { _, _ -> },
                        onSrimChanged = { _, _ -> },
                        onOpenDart = { true }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("comprehensive_analysis_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("종합 참고가").assertIsDisplayed()
        composeRule.onNodeWithText("지지 구간").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("저항 구간").performScrollTo().assertIsDisplayed()
    }
}
