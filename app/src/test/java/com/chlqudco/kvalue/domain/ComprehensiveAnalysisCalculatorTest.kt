package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.data.SampleStockData
import com.chlqudco.kvalue.domain.model.AnnualFinancial
import com.chlqudco.kvalue.domain.model.PerReferenceBand
import com.chlqudco.kvalue.domain.model.PerReferenceResult
import com.chlqudco.kvalue.domain.model.FinancialTrend
import com.chlqudco.kvalue.domain.model.ReferenceMethod
import com.chlqudco.kvalue.domain.model.SrimValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComprehensiveAnalysisCalculatorTest {
    @Test
    fun combinesPerAndSortedSrimScenariosWithEqualWeight() {
        val per = PerReferenceResult(
            conservative = 80_000L,
            base = 100_000L,
            optimistic = 120_000L,
            baseGapPercent = 0.0,
            band = PerReferenceBand.BETWEEN_CONSERVATIVE_AND_BASE
        )
        val srim = SrimValueResult(
            fastFade = 150_000L,
            gradualFade = 90_000L,
            persistent = 60_000L,
            gradualGapPercent = -10.0,
            excessReturnPercent = 2.0
        )

        val result = ComprehensiveAnalysisCalculator.compositeReference(
            perReference = per,
            srimReference = srim,
            currentPrice = 100_000L
        )

        assertEquals(70_000L, result?.lower)
        assertEquals(95_000L, result?.base)
        assertEquals(135_000L, result?.upper)
        assertEquals(-5.0, result?.baseGapPercent ?: Double.NaN, 0.000001)
        assertEquals(setOf(ReferenceMethod.PER, ReferenceMethod.SRIM), result?.methods)
    }

    @Test
    fun usesSingleAvailableReferenceAndRejectsInvalidCurrentPrice() {
        val per = PerReferenceResult(
            conservative = 80_000L,
            base = 100_000L,
            optimistic = 120_000L,
            baseGapPercent = 0.0,
            band = PerReferenceBand.BETWEEN_CONSERVATIVE_AND_BASE
        )

        val result = ComprehensiveAnalysisCalculator.compositeReference(per, null, 90_000L)

        assertEquals(80_000L, result?.lower)
        assertEquals(100_000L, result?.base)
        assertEquals(120_000L, result?.upper)
        assertEquals(setOf(ReferenceMethod.PER), result?.methods)
        assertNull(ComprehensiveAnalysisCalculator.compositeReference(per, null, 0L))
        assertNull(ComprehensiveAnalysisCalculator.compositeReference(null, null, 90_000L))
    }

    @Test
    fun evaluatesImprovingAndWeakeningFinancialTrends() {
        val improving = financials(100L, 200L, 10L, 30L, 8L, 20L)
        val weakening = financials(200L, 100L, 30L, 10L, 20L, 8L)

        assertEquals(
            FinancialTrend.IMPROVING,
            ComprehensiveAnalysisCalculator.financialTrend(improving).trend
        )
        assertEquals(
            FinancialTrend.WEAKENING,
            ComprehensiveAnalysisCalculator.financialTrend(weakening).trend
        )
        assertEquals(
            FinancialTrend.UNAVAILABLE,
            ComprehensiveAnalysisCalculator.financialTrend(emptyList()).trend
        )
    }

    @Test
    fun calculatesComprehensiveResultFromSampleData() {
        val analysis = SampleStockData.samsungElectronics()
        val per = PerReferenceCalculator.calculate(
            eps = requireNotNull(analysis.ratios.eps),
            assumptions = com.chlqudco.kvalue.domain.model.PerAssumptions(),
            currentPrice = analysis.price.currentPrice
        )
        val srim = SrimValueCalculator.calculate(
            bps = requireNotNull(analysis.ratios.bps),
            assumptions = com.chlqudco.kvalue.domain.model.SrimAssumptions(
                returnOnEquityPercent = requireNotNull(analysis.ratios.roe)
            ),
            currentPrice = analysis.price.currentPrice
        )

        val result = ComprehensiveAnalysisCalculator.calculate(analysis, per, srim)

        assertNotNull(result.reference)
        assertNotNull(result.technical)
        assertEquals(FinancialTrend.IMPROVING, result.financialTrend.trend)
        assertTrue(result.score in -5..5)
    }

    private fun financials(
        firstRevenue: Long,
        lastRevenue: Long,
        firstOperatingIncome: Long,
        lastOperatingIncome: Long,
        firstNetIncome: Long,
        lastNetIncome: Long
    ) = listOf(
        AnnualFinancial(2024, firstRevenue, firstOperatingIncome, firstNetIncome),
        AnnualFinancial(2025, lastRevenue, lastOperatingIncome, lastNetIncome)
    )
}
