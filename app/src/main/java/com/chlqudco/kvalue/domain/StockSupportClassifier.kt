package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.SupportReason
import com.chlqudco.kvalue.domain.model.SupportStatus

object StockSupportClassifier {
    fun classify(
        companyName: String,
        sectorName: String?,
        eps: Double?,
        hasDartCompany: Boolean
    ): SupportStatus {
        val compactName = companyName.replace(" ", "")
        val upperName = companyName.uppercase()
        val sector = sectorName.orEmpty()
        val reason = when {
            isExchangeTradedProduct(upperName, sector) -> SupportReason.ETF_OR_ETN
            isPreferredStock(compactName) -> SupportReason.PREFERRED_STOCK
            "스팩" in compactName || "SPAC" in upperName -> SupportReason.SPAC
            "리츠" in compactName || "REIT" in upperName -> SupportReason.REIT
            isFinancialCompany(compactName, sector) -> SupportReason.FINANCIAL_COMPANY
            !hasDartCompany -> SupportReason.UNKNOWN_TYPE
            eps == null || !eps.isFinite() || eps <= 0.0 -> SupportReason.NON_POSITIVE_EPS
            else -> null
        }
        return reason?.let(SupportStatus::Unsupported) ?: SupportStatus.Supported
    }

    private fun isExchangeTradedProduct(name: String, sector: String): Boolean {
        if ("ETF" in name || "ETN" in name || "ETF" in sector.uppercase() ||
            "ETN" in sector.uppercase()
        ) {
            return true
        }
        return ETF_PREFIXES.any(name::startsWith)
    }

    private fun isPreferredStock(name: String): Boolean =
        "우선주" in name || PREFERRED_STOCK_SUFFIX.containsMatchIn(name)

    private fun isFinancialCompany(name: String, sector: String): Boolean {
        if (FINANCIAL_SECTOR_KEYWORDS.any(sector::contains)) return true
        return "은행" in name || "증권" in name || "금융" in name ||
            "손해보험" in name || name.endsWith("생명") || name.endsWith("화재")
    }

    private val ETF_PREFIXES = listOf(
        "KODEX",
        "TIGER",
        "ACE",
        "RISE",
        "SOL",
        "HANARO",
        "PLUS",
        "KOSEF",
        "TIMEFOLIO",
        "ARIRANG",
        "KIWOOM",
        "WON",
        "1Q"
    )
    private val FINANCIAL_SECTOR_KEYWORDS = listOf(
        "은행",
        "보험",
        "증권",
        "금융"
    )
    private val PREFERRED_STOCK_SUFFIX = Regex("(?:\\d+)?우[A-Z]?$")
}
