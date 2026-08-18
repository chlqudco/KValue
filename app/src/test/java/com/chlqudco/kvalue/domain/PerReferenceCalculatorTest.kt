package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.PerReferenceBand
import com.chlqudco.kvalue.domain.model.PerAssumptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PerReferenceCalculatorTest {
    @Test
    fun calculatesThreeScenariosAndGap() {
        val result = PerReferenceCalculator.calculate(
            eps = 5_800.0,
            assumptions = PerAssumptions(10.0, 15.0, 20.0),
            currentPrice = 82_300L
        )

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(58_000L, result.conservative)
        assertEquals(87_000L, result.base)
        assertEquals(116_000L, result.optimistic)
        assertEquals(5.7108, result.baseGapPercent, 0.0001)
        assertEquals(PerReferenceBand.BETWEEN_CONSERVATIVE_AND_BASE, result.band)
    }

    @Test
    fun roundsReferencePricesToNearestHundredWon() {
        val result = PerReferenceCalculator.calculate(
            eps = 585.0,
            assumptions = PerAssumptions(10.0, 15.0, 20.0),
            currentPrice = 5_850L
        )

        assertEquals(5_900L, requireNotNull(result).conservative)
    }

    @Test
    fun returnsZeroGapWhenCurrentPriceMatchesBaseReference() {
        val result = PerReferenceCalculator.calculate(
            eps = 5_800.0,
            assumptions = PerAssumptions(),
            currentPrice = 87_000L
        )

        assertEquals(0.0, requireNotNull(result).baseGapPercent, 0.0)
    }

    @Test
    fun rejectsNonPositiveOrNonFiniteEps() {
        val assumptions = PerAssumptions()

        assertNull(PerReferenceCalculator.calculate(0.0, assumptions, 82_300L))
        assertNull(PerReferenceCalculator.calculate(-100.0, assumptions, 82_300L))
        assertNull(PerReferenceCalculator.calculate(Double.NaN, assumptions, 82_300L))
        assertNull(PerReferenceCalculator.calculate(Double.POSITIVE_INFINITY, assumptions, 82_300L))
    }

    @Test
    fun rejectsInvalidPerOrderAndCurrentPrice() {
        assertNull(
            PerReferenceCalculator.calculate(
                5_800.0,
                PerAssumptions(20.0, 15.0, 10.0),
                82_300L
            )
        )
        assertNull(
            PerReferenceCalculator.calculate(
                5_800.0,
                PerAssumptions(0.0, 15.0, 20.0),
                82_300L
            )
        )
        assertNull(PerReferenceCalculator.calculate(5_800.0, PerAssumptions(), 0L))
    }
}
