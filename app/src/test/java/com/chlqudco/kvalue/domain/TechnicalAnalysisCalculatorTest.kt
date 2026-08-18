package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.PricePoint
import com.chlqudco.kvalue.domain.model.RsiState
import com.chlqudco.kvalue.domain.model.TechnicalDirection
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalAnalysisCalculatorTest {
    @Test
    fun calculatesIndicatorsAndPriceZones() {
        val history = oscillatingHistory()
        val currentPrice = 11_000L

        val result = TechnicalAnalysisCalculator.calculate(history, currentPrice)

        assertNotNull(result)
        result ?: return
        assertEquals(100, result.sampleSize)
        assertNotNull(result.movingAverage5)
        assertNotNull(result.movingAverage60)
        assertNotNull(result.macd)
        assertTrue(result.rsi14 in 0.0..100.0)
        assertTrue(result.bollingerLower < result.bollingerMiddle)
        assertTrue(result.bollingerMiddle < result.bollingerUpper)
        assertTrue(requireNotNull(result.annualizedVolatilityPercent) >= 0.0)
        assertTrue(result.supportZones.all { it.upper < currentPrice })
        assertTrue(result.resistanceZones.all { it.lower > currentPrice })
        assertTrue(result.supportZones.size <= 2)
        assertTrue(result.resistanceZones.size <= 2)
        assertTrue(result.supportZones.isNotEmpty())
        assertTrue(result.resistanceZones.isNotEmpty())
    }

    @Test
    fun classifiesSteadyRiseAsPositiveAndOverbought() {
        val history = (0 until 100).map { index ->
            val close = 10_000L + index * 100L
            PricePoint(
                date = LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
                close = close,
                high = close + 100L,
                low = close - 100L,
                volume = 1_000_000L
            )
        }

        val result = TechnicalAnalysisCalculator.calculate(history, history.last().close)

        assertEquals(TechnicalDirection.POSITIVE, result?.direction)
        assertEquals(RsiState.OVERBOUGHT, result?.rsiState)
    }

    @Test
    fun requiresTwentyValidTradingDaysAndPositiveCurrentPrice() {
        val history = oscillatingHistory().take(19)

        assertNull(TechnicalAnalysisCalculator.calculate(history, 10_000L))
        assertNull(TechnicalAnalysisCalculator.calculate(oscillatingHistory(), 0L))
    }

    private fun oscillatingHistory(): List<PricePoint> = (0 until 100).map { index ->
        val close = 10_400L + index * 12L + (sin(index / 3.0) * 900.0).roundToLong()
        PricePoint(
            date = LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
            close = close,
            open = close - 80L,
            high = close + 260L,
            low = close - 240L,
            volume = 1_000_000L + index * 5_000L
        )
    }
}
