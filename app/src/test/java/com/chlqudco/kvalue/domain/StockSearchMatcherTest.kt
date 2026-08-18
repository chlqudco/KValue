package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockSearchMatcherTest {
    private val stocks = listOf(
        StockSearchSuggestion("005930", "삼성전자"),
        StockSearchSuggestion("005935", "삼성전자우"),
        StockSearchSuggestion("000660", "SK하이닉스"),
        StockSearchSuggestion("176750", "듀켐바이오"),
        StockSearchSuggestion("123456", "테스트삼성")
    )

    @Test
    fun ranksExactNameBeforePrefixAndContainsMatches() {
        val result = StockSearchMatcher.find(stocks, "삼성전자", 8)

        assertEquals(
            listOf("005930", "005935"),
            result.map(StockSearchSuggestion::stockCode)
        )
    }

    @Test
    fun searchesByStockCodePrefix() {
        val result = StockSearchMatcher.find(stocks, "00593", 8)

        assertEquals(
            listOf("005930", "005935"),
            result.map(StockSearchSuggestion::stockCode)
        )
    }

    @Test
    fun ignoresSpacesAndCaseForNameMatching() {
        val result = StockSearchMatcher.find(stocks, "sk 하이닉스", 8)

        assertEquals(listOf("000660"), result.map(StockSearchSuggestion::stockCode))
        assertTrue(StockSearchMatcher.isExactName(result.single(), "SK 하이닉스"))
    }

    @Test
    fun respectsLimitAndRejectsBlankQuery() {
        assertEquals(1, StockSearchMatcher.find(stocks, "삼성", 1).size)
        assertTrue(StockSearchMatcher.find(stocks, "  ", 8).isEmpty())
        assertTrue(StockSearchMatcher.find(stocks, "삼성", 0).isEmpty())
    }
}
