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

    private fun matchRank(name: String, code: String, query: String): Int? = when {
        code == query -> 0
        name == query -> 1
        code.startsWith(query) -> 2
        name.startsWith(query) -> 3
        name.contains(query) -> 4
        code.contains(query) -> 5
        else -> null
    }

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
