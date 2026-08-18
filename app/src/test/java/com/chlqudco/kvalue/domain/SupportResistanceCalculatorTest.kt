package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.PricePoint
import com.chlqudco.kvalue.domain.model.SupportResistanceResult
import com.chlqudco.kvalue.domain.model.SupportResistanceUnavailableReason
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportResistanceCalculatorTest {
    @Test
    fun findsNearestSupportAndResistanceFromRepeatedTurningPoints() {
        val result = SupportResistanceCalculator.calculate(
            priceHistory = oscillatingHistory(),
            currentPrice = 10_000L
        )

        assertTrue(result is SupportResistanceResult.Available)
        val indicator = (result as SupportResistanceResult.Available).indicator
        assertEquals(100, indicator.sampleSize)
        assertEquals(1.5, indicator.clusterTolerancePercent, 0.0)
        assertEquals(0.3, indicator.minimumLevelDistancePercent, 0.0)
        assertTrue(indicator.supportLevels.isNotEmpty())
        assertTrue(indicator.resistanceLevels.isNotEmpty())
        assertTrue(indicator.supportLevels.size <= 2)
        assertTrue(indicator.resistanceLevels.size <= 2)
        assertTrue(indicator.supportLevels.all { it.price < 9_970L })
        assertTrue(indicator.resistanceLevels.all { it.price > 10_030L })
        assertTrue(indicator.supportLevels.zipWithNext().all { (first, second) ->
            first.price > second.price
        })
        assertTrue(indicator.resistanceLevels.zipWithNext().all { (first, second) ->
            first.price < second.price
        })
        assertTrue(
            (indicator.supportLevels + indicator.resistanceLevels).all {
                it.touchCount > 0
            }
        )
    }

    @Test
    fun sortsDeduplicatesAndLimitsHistoryToOneHundredDays() {
        val history = oscillatingHistory(120).reversed().toMutableList().apply {
            add(last().copy(close = 1L))
        }

        val result = SupportResistanceCalculator.calculate(history, 10_000L)

        assertTrue(result is SupportResistanceResult.Available)
        val indicator = (result as SupportResistanceResult.Available).indicator
        assertEquals(100, indicator.sampleSize)
        assertEquals(LocalDate.of(2026, 4, 30), indicator.asOf)
    }

    @Test
    fun reportsInsufficientHistoryAndInvalidCurrentPrice() {
        val insufficient = SupportResistanceCalculator.calculate(
            priceHistory = oscillatingHistory(19),
            currentPrice = 10_000L
        )
        val invalid = SupportResistanceCalculator.calculate(
            priceHistory = oscillatingHistory(),
            currentPrice = 0L
        )

        assertEquals(
            SupportResistanceUnavailableReason.INSUFFICIENT_HISTORY,
            (insufficient as SupportResistanceResult.Unavailable).reason
        )
        assertEquals(19, insufficient.observationCount)
        assertEquals(
            SupportResistanceUnavailableReason.INVALID_CURRENT_PRICE,
            (invalid as SupportResistanceResult.Unavailable).reason
        )
    }

    @Test
    fun doesNotInventLevelsForFlatPrices() {
        val history = (0 until 30).map { index ->
            PricePoint(
                date = LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
                close = 10_000L,
                high = 10_000L,
                low = 10_000L
            )
        }

        val result = SupportResistanceCalculator.calculate(history, 10_000L)

        assertEquals(
            SupportResistanceUnavailableReason.NO_DISTINCT_LEVELS,
            (result as SupportResistanceResult.Unavailable).reason
        )
    }

    private fun oscillatingHistory(size: Int = 100): List<PricePoint> =
        (0 until size).map { index ->
            val close = 10_000L + (sin(index / 3.0) * 1_200.0).roundToLong()
            PricePoint(
                date = LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
                close = close,
                open = close - 30L,
                high = close + 180L,
                low = close - 170L,
                volume = 1_000_000L + index * 10_000L
            )
        }
}
