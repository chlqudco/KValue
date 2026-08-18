/*
 * 메모리에 준비된 상장 종목에서 회사명 또는 종목코드와 일치하는 자동완성 후보를 찾는다.
 * 공백·기호·대소문자를 정규화한 뒤 정확한 코드, 정확한 이름, 접두사, 포함 순으로 순위를 매긴다.
 * 같은 순위에서는 짧은 이름과 사전순을 사용해 결과가 실행마다 안정적으로 유지되게 한다.
 * Sequence로 후보를 가공하고 종목코드 중복 제거와 개수 제한을 마지막에 적용한다.
 */
package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.StockSearchSuggestion
import java.util.Locale

object StockSearchMatcher {
    fun find(
        stocks: Collection<StockSearchSuggestion>,
        query: String,
        limit: Int
    ): List<StockSearchSuggestion> {
        if (limit <= 0) return emptyList()
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()
        // 먼저 일치하지 않는 후보를 제거하고 명시적 rank와 보조 키로 안정 정렬한다.
        return stocks.asSequence()
            .mapNotNull { stock ->
                val name = normalize(stock.companyName)
                val code = normalize(stock.stockCode)
                val rank = matchRank(name, code, normalizedQuery) ?: return@mapNotNull null
                RankedStock(stock, rank, name.length)
            }
            .sortedWith(
                compareBy<RankedStock> { it.rank }
                    .thenBy { it.nameLength }
                    .thenBy { it.stock.companyName }
                    .thenBy { it.stock.stockCode }
            )
            .map(RankedStock::stock)
            .distinctBy(StockSearchSuggestion::stockCode)
            .take(limit)
            .toList()
    }

    fun isExactName(suggestion: StockSearchSuggestion, query: String): Boolean =
        normalize(suggestion.companyName) == normalize(query)

    // 숫자가 정확히 맞는 경우를 최우선으로 두고 느슨한 포함 검색일수록 큰 순위를 부여한다.
    private fun matchRank(name: String, code: String, query: String): Int? = when {
        code == query -> 0
        name == query -> 1
        code.startsWith(query) -> 2
        name.startsWith(query) -> 3
        name.contains(query) -> 4
        code.contains(query) -> 5
        else -> null
    }

    // 사용자에게 보이는 원문은 유지하고 비교용 값에서만 공백·기호·대소문자 차이를 제거한다.
    private fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.KOREAN)
        .filter(Char::isLetterOrDigit)

    private data class RankedStock(
        val stock: StockSearchSuggestion,
        val rank: Int,
        val nameLength: Int
    )
}
