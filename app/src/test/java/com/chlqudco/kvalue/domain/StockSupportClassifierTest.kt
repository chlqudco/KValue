package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.SupportReason
import com.chlqudco.kvalue.domain.model.SupportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StockSupportClassifierTest {
    @Test
    fun supportsOrdinaryProfitableCompany() {
        val result = StockSupportClassifier.classify(
            companyName = "삼성전자",
            sectorName = "전기전자",
            eps = 5_000.0,
            hasDartCompany = true
        )

        assertSame(SupportStatus.Supported, result)
    }

    @Test
    fun excludesNonPositiveEps() {
        val result = StockSupportClassifier.classify(
            companyName = "테스트기업",
            sectorName = "제조업",
            eps = 0.0,
            hasDartCompany = true
        )

        assertEquals(
            SupportStatus.Unsupported(SupportReason.NON_POSITIVE_EPS),
            result
        )
    }

    @Test
    fun excludesEtfPreferredReitAndFinancialCompany() {
        assertUnsupported("KODEX 200", "ETF", false, SupportReason.ETF_OR_ETN)
        assertUnsupported("삼성전자우", "전기전자", false, SupportReason.PREFERRED_STOCK)
        assertUnsupported("신한알파리츠", "서비스업", true, SupportReason.REIT)
        assertUnsupported("KB금융", "금융업", true, SupportReason.FINANCIAL_COMPANY)
    }

    @Test
    fun doesNotTreatLifeScienceNameAsFinancialCompany() {
        val result = StockSupportClassifier.classify(
            companyName = "HLB생명과학",
            sectorName = "제약",
            eps = 100.0,
            hasDartCompany = true
        )

        assertSame(SupportStatus.Supported, result)
    }

    @Test
    fun excludesUnknownTypeWithoutDartCompany() {
        val result = StockSupportClassifier.classify(
            companyName = "알수없는종목",
            sectorName = null,
            eps = 100.0,
            hasDartCompany = false
        )

        assertEquals(SupportStatus.Unsupported(SupportReason.UNKNOWN_TYPE), result)
    }

    private fun assertUnsupported(
        name: String,
        sector: String,
        hasDartCompany: Boolean,
        reason: SupportReason
    ) {
        val result = StockSupportClassifier.classify(
            companyName = name,
            sectorName = sector,
            eps = 100.0,
            hasDartCompany = hasDartCompany
        )

        assertEquals(SupportStatus.Unsupported(reason), result)
    }
}
