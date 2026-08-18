package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.SrimAssumptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SrimValueCalculatorTest {
    @Test
    fun calculatesPersistenceScenariosAndGap() {
        val result = SrimValueCalculator.calculate(
            bps = 10_000.0,
            assumptions = SrimAssumptions(
                returnOnEquityPercent = 20.0,
                requiredReturnPercent = 10.0
            ),
            currentPrice = 15_000L
        )

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(12_700L, result.fastFade)
        assertEquals(14_500L, result.gradualFade)
        assertEquals(20_000L, result.persistent)
        assertEquals(-3.3333, result.gradualGapPercent, 0.0001)
        assertEquals(10.0, result.excessReturnPercent, 0.0)
    }

    @Test
    fun returnsBpsWhenRoeMatchesRequiredReturn() {
        val result = SrimValueCalculator.calculate(
            bps = 58_786.0,
            assumptions = SrimAssumptions(10.0, 10.0),
            currentPrice = 58_800L
        )

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(58_800L, result.fastFade)
        assertEquals(58_800L, result.gradualFade)
        assertEquals(58_800L, result.persistent)
        assertEquals(0.0, result.gradualGapPercent, 0.0)
    }

    @Test
    fun keepsValidResultsWhenRoeIsBelowRequiredReturn() {
        val result = SrimValueCalculator.calculate(
            bps = 10_000.0,
            assumptions = SrimAssumptions(5.0, 10.0),
            currentPrice = 8_000L
        )

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(8_700L, result.fastFade)
        assertEquals(7_800L, result.gradualFade)
        assertEquals(5_000L, result.persistent)
    }

    @Test
    fun rejectsInvalidOrNonFiniteInputs() {
        val valid = SrimAssumptions(12.0, 10.0)

        assertNull(SrimValueCalculator.calculate(0.0, valid, 10_000L))
        assertNull(SrimValueCalculator.calculate(-1.0, valid, 10_000L))
        assertNull(SrimValueCalculator.calculate(Double.NaN, valid, 10_000L))
        assertNull(
            SrimValueCalculator.calculate(
                10_000.0,
                SrimAssumptions(Double.POSITIVE_INFINITY, 10.0),
                10_000L
            )
        )
        assertNull(
            SrimValueCalculator.calculate(
                10_000.0,
                SrimAssumptions(12.0, 0.0),
                10_000L
            )
        )
        assertNull(
            SrimValueCalculator.calculate(
                10_000.0,
                SrimAssumptions(12.0, 100.1),
                10_000L
            )
        )
        assertNull(SrimValueCalculator.calculate(10_000.0, valid, 0L))
        assertNull(SrimValueCalculator.calculate(Double.MAX_VALUE, valid, 10_000L))
    }
}
