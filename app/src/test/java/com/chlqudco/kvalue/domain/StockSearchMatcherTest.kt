/*
 * 종목 자동완성 후보의 검색·정렬 규칙을 검증한다.
 * 정확 일치, 접두사, 포함 검색 순위와 종목코드 접두사 검색을 실제 후보 목록으로 확인한다.
 * 공백·대소문자 정규화, 결과 개수 제한, 빈 검색어 처리도 함께 고정한다.
 */
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
