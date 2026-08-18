package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.ForecastUnavailableReason
import com.chlqudco.kvalue.domain.model.HistoricalForecastResult
import com.chlqudco.kvalue.domain.model.PricePoint
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalForecastCalculatorTest {
    @Test
    fun rejectsInsufficientHistory() {
        val history = cyclicalHistory(100)

        val result = HistoricalForecastCalculator.calculate(
            history = history,
            currentPrice = history.last().close
        )

        assertTrue(result is HistoricalForecastResult.Unavailable)
        val unavailable = result as HistoricalForecastResult.Unavailable
        assertEquals(ForecastUnavailableReason.INSUFFICIENT_HISTORY, unavailable.reason)
        assertEquals(100, unavailable.observationCount)
        assertEquals(
            HistoricalForecastCalculator.MINIMUM_OBSERVATION_COUNT,
            unavailable.requiredObservationCount
        )
    }

    @Test
    fun keepsRangeWhenDirectionModelDoesNotBeatBaseline() {
        val history = (0 until 320).map { index ->
            PricePoint(
                date = LocalDate.of(2025, 1, 1).plusDays(index.toLong()),
                close = 100_000L,
                open = 100_000L,
                high = 100_000L,
                low = 100_000L,
                volume = 1_000_000L
            )
        }

        val result = HistoricalForecastCalculator.calculate(history, 100_000L)

        assertTrue(result is HistoricalForecastResult.Available)
        val forecast = (result as HistoricalForecastResult.Available).forecast
        assertEquals(100_000L, forecast.lowerPrice)
        assertEquals(100_000L, forecast.upperPrice)
        assertNull(forecast.direction)
        assertTrue(!forecast.validation.directionPassed)
        assertTrue(forecast.validation.intervalPredictionCount > 0)
    }

    @Test
    fun returnsRangeAndValidatedDirectionForPredictableOhlcv() {
        val history = cyclicalHistory(640)

        val result = HistoricalForecastCalculator.calculate(
            history = history,
            currentPrice = history.last().close
        )

        assertTrue(result is HistoricalForecastResult.Available)
        val forecast = (result as HistoricalForecastResult.Available).forecast
        assertEquals(5, forecast.horizonTradingDays)
        assertEquals(640, forecast.observationCount)
        assertTrue(forecast.lowerReturnPercent <= forecast.upperReturnPercent)
        assertTrue(forecast.lowerPrice <= forecast.upperPrice)
        assertTrue(forecast.validation.intervalPredictionCount > 0)
        assertTrue(forecast.validation.directionPassed)
        val direction = requireNotNull(forecast.direction)
        val probabilityTotal = direction.upwardProbabilityPercent +
            direction.neutralProbabilityPercent +
            direction.downwardProbabilityPercent
        assertTrue(probabilityTotal in 99.999..100.001)
        assertTrue(direction.analogCount > 0)
    }

    @Test
    fun omitsDirectionWhenOhlcvFieldsAreMissing() {
        val history = cyclicalHistory(320).map {
            it.copy(open = null, high = null, low = null, volume = null)
        }

        val result = HistoricalForecastCalculator.calculate(
            history = history,
            currentPrice = history.last().close
        )

        assertTrue(result is HistoricalForecastResult.Available)
        val forecast = (result as HistoricalForecastResult.Available).forecast
        assertNull(forecast.direction)
        assertEquals(0, forecast.validation.directionPredictionCount)
    }

    @Test
    fun rejectsNonPositiveCurrentPrice() {
        val history = cyclicalHistory(320)

        val result = HistoricalForecastCalculator.calculate(history, 0L)

        assertTrue(result is HistoricalForecastResult.Unavailable)
        assertEquals(
            ForecastUnavailableReason.INVALID_PRICE_HISTORY,
            (result as HistoricalForecastResult.Unavailable).reason
        )
    }

    private fun cyclicalHistory(count: Int): List<PricePoint> =
        (0 until count).map { index ->
            val close = closeAt(index)
            val previousClose = closeAt((index - 1).coerceAtLeast(0))
            val open = ((close + previousClose) / 2.0 + sin(index / 5.0) * 180.0)
                .roundToLong()
            val range = 700L + (sin(index / 11.0) * 180.0).roundToLong()
            PricePoint(
                date = LocalDate.of(2022, 1, 1).plusDays(index.toLong()),
                close = close,
                open = open,
                high = maxOf(open, close) + range,
                low = minOf(open, close) - range,
                volume = 1_500_000L +
                    (sin(index / 7.0) * 300_000.0).roundToLong() +
                    index % 13 * 10_000L
            )
        }

    private fun closeAt(index: Int): Long = (
        100_000.0 +
            index * 25.0 +
            sin(index / 7.0) * 5_000.0 +
            sin(index / 19.0) * 2_000.0
        ).roundToLong()
}
