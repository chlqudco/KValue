package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.PricePoint
import com.chlqudco.kvalue.domain.model.PriceZone
import com.chlqudco.kvalue.domain.model.RsiState
import com.chlqudco.kvalue.domain.model.TechnicalAnalysisResult
import com.chlqudco.kvalue.domain.model.TechnicalDirection
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

object TechnicalAnalysisCalculator {
    fun calculate(
        priceHistory: List<PricePoint>,
        currentPrice: Long
    ): TechnicalAnalysisResult? {
        if (currentPrice <= 0L) return null
        val points = priceHistory
            .filter { it.close > 0L }
            .sortedBy(PricePoint::date)
            .distinctBy(PricePoint::date)
            .takeLast(MAX_SAMPLE_SIZE)
        if (points.size < MINIMUM_SAMPLE_SIZE) return null

        val closes = points.map { it.close.toDouble() }
        val movingAverage5 = closes.averageLast(5)
        val movingAverage20 = closes.averageLast(20) ?: return null
        val movingAverage60 = closes.averageLast(60)
        val rsi14 = rsi(closes, 14) ?: return null
        val macdValues = macd(closes)
        val bollinger = bollinger(closes, 20) ?: return null
        val volatility = annualizedVolatility(closes, 20)
        val volumeRatio = volumeRatio(points, 20)

        var score = 0
        val lastClose = closes.last()
        score += compare(lastClose, movingAverage20)
        if (movingAverage5 != null) score += compare(movingAverage5, movingAverage20)
        if (movingAverage60 != null) score += compare(lastClose, movingAverage60)
        macdValues?.histogram?.let { score += compare(it, 0.0) }
        score += when {
            rsi14 > 55.0 -> 1
            rsi14 < 45.0 -> -1
            else -> 0
        }
        if (volumeRatio != null && volumeRatio >= 1.5 && closes.size >= 2) {
            score += compare(closes.last(), closes[closes.lastIndex - 1])
        }

        val zones = priceZones(
            points = points,
            currentPrice = currentPrice,
            movingAverage20 = movingAverage20,
            movingAverage60 = movingAverage60,
            bollingerLower = bollinger.lower,
            bollingerUpper = bollinger.upper
        )
        return TechnicalAnalysisResult(
            asOf = points.last().date,
            sampleSize = points.size,
            movingAverage5 = movingAverage5,
            movingAverage20 = movingAverage20,
            movingAverage60 = movingAverage60,
            rsi14 = rsi14,
            rsiState = when {
                rsi14 >= 70.0 -> RsiState.OVERBOUGHT
                rsi14 >= 55.0 -> RsiState.POSITIVE
                rsi14 > 45.0 -> RsiState.NEUTRAL
                rsi14 > 30.0 -> RsiState.NEGATIVE
                else -> RsiState.OVERSOLD
            },
            macd = macdValues?.line,
            macdSignal = macdValues?.signal,
            macdHistogram = macdValues?.histogram,
            bollingerLower = bollinger.lower,
            bollingerMiddle = bollinger.middle,
            bollingerUpper = bollinger.upper,
            annualizedVolatilityPercent = volatility,
            volumeRatio20 = volumeRatio,
            supportZones = zones.first,
            resistanceZones = zones.second,
            direction = when {
                score >= 3 -> TechnicalDirection.POSITIVE
                score <= -3 -> TechnicalDirection.NEGATIVE
                else -> TechnicalDirection.NEUTRAL
            },
            score = score
        )
    }

    private fun compare(left: Double, right: Double): Int = when {
        left > right -> 1
        left < right -> -1
        else -> 0
    }

    private fun List<Double>.averageLast(period: Int): Double? {
        if (size < period) return null
        return takeLast(period).average().takeIf(Double::isFinite)
    }

    private fun rsi(values: List<Double>, period: Int): Double? {
        if (values.size <= period) return null
        val changes = values.takeLast(period + 1).zipWithNext { previous, current ->
            current - previous
        }
        val averageGain = changes.sumOf { max(it, 0.0) } / period
        val averageLoss = changes.sumOf { max(-it, 0.0) } / period
        val result = when {
            averageLoss == 0.0 && averageGain == 0.0 -> 50.0
            averageLoss == 0.0 -> 100.0
            averageGain == 0.0 -> 0.0
            else -> 100.0 - 100.0 / (1.0 + averageGain / averageLoss)
        }
        return result.takeIf(Double::isFinite)
    }

    private fun macd(values: List<Double>): MacdValues? {
        if (values.size < MACD_MINIMUM_SAMPLE_SIZE) return null
        val fast = ema(values, 12)
        val slow = ema(values, 26)
        val line = fast.zip(slow) { fastValue, slowValue -> fastValue - slowValue }
        val signal = ema(line, 9)
        val lastLine = line.last()
        val lastSignal = signal.last()
        val histogram = lastLine - lastSignal
        if (listOf(lastLine, lastSignal, histogram).any { !it.isFinite() }) return null
        return MacdValues(lastLine, lastSignal, histogram)
    }

    private fun ema(values: List<Double>, period: Int): List<Double> {
        val multiplier = 2.0 / (period + 1.0)
        var current = values.first()
        return values.mapIndexed { index, value ->
            if (index > 0) current = (value - current) * multiplier + current
            current
        }
    }

    private fun bollinger(values: List<Double>, period: Int): BollingerValues? {
        if (values.size < period) return null
        val sample = values.takeLast(period)
        val middle = sample.average()
        val deviation = sqrt(sample.sumOf { (it - middle) * (it - middle) } / period)
        val lower = middle - 2.0 * deviation
        val upper = middle + 2.0 * deviation
        if (listOf(lower, middle, upper).any { !it.isFinite() || it <= 0.0 }) return null
        return BollingerValues(lower, middle, upper)
    }

    private fun annualizedVolatility(values: List<Double>, period: Int): Double? {
        if (values.size <= period) return null
        val returns = values.takeLast(period + 1).zipWithNext { previous, current ->
            ln(current / previous)
        }
        val average = returns.average()
        val variance = returns.sumOf { (it - average) * (it - average) } /
            (returns.size - 1).coerceAtLeast(1)
        return (sqrt(variance) * sqrt(TRADING_DAYS_PER_YEAR) * 100.0)
            .takeIf(Double::isFinite)
    }

    private fun volumeRatio(points: List<PricePoint>, period: Int): Double? {
        val volumes = points.takeLast(period).mapNotNull { it.volume?.takeIf { value -> value > 0L } }
        if (volumes.size < period / 2) return null
        val average = volumes.average()
        if (!average.isFinite() || average <= 0.0) return null
        val latest = points.last().volume?.takeIf { it > 0L } ?: return null
        return (latest / average).takeIf(Double::isFinite)
    }

    private fun priceZones(
        points: List<PricePoint>,
        currentPrice: Long,
        movingAverage20: Double,
        movingAverage60: Double?,
        bollingerLower: Double,
        bollingerUpper: Double
    ): Pair<List<PriceZone>, List<PriceZone>> {
        val recent = points.takeLast(60)
        val lows = recent.map { (it.low ?: it.close).toDouble() }
        val highs = recent.map { (it.high ?: it.close).toDouble() }
        val candidates = mutableListOf<Double>()
        if (recent.size >= 5) {
            for (index in 2 until recent.size - 2) {
                val nearbyLows = lows.subList(index - 2, index + 3)
                val nearbyHighs = highs.subList(index - 2, index + 3)
                if (lows[index] == nearbyLows.minOrNull() && nearbyLows.any { it > lows[index] }) {
                    candidates += lows[index]
                }
                if (highs[index] == nearbyHighs.maxOrNull() && nearbyHighs.any { it < highs[index] }) {
                    candidates += highs[index]
                }
            }
        }
        candidates += lows.takeLast(20).minOrNull().orEmptyLevel()
        candidates += highs.takeLast(20).maxOrNull().orEmptyLevel()
        candidates += movingAverage20
        movingAverage60?.let(candidates::add)
        candidates += bollingerLower
        candidates += bollingerUpper

        val clusters = cluster(candidates.filter { it.isFinite() && it > 0.0 })
        val current = currentPrice.toDouble()
        val supports = clusters
            .filter { it.center < current }
            .sortedByDescending(LevelCluster::center)
            .take(MAX_ZONES_PER_SIDE)
            .mapNotNull { it.toZone(isSupport = true, currentPrice = currentPrice) }
        val resistances = clusters
            .filter { it.center > current }
            .sortedBy(LevelCluster::center)
            .take(MAX_ZONES_PER_SIDE)
            .mapNotNull { it.toZone(isSupport = false, currentPrice = currentPrice) }
        return supports to resistances
    }

    private fun Double?.orEmptyLevel(): Double = this ?: Double.NaN

    private fun cluster(levels: List<Double>): List<LevelCluster> {
        val clusters = mutableListOf<LevelCluster>()
        levels.sorted().forEach { level ->
            val target = clusters.lastOrNull()?.takeIf {
                abs(level - it.center) / it.center <= CLUSTER_TOLERANCE
            }
            if (target == null) {
                clusters += LevelCluster(level, level, level, 1)
            } else {
                target.minimum = min(target.minimum, level)
                target.maximum = max(target.maximum, level)
                target.sum += level
                target.count += 1
            }
        }
        return clusters
    }

    private fun LevelCluster.toZone(
        isSupport: Boolean,
        currentPrice: Long
    ): PriceZone? {
        var lower = roundHundred(minimum * (1.0 - ZONE_PADDING)) ?: return null
        var upper = roundHundred(maximum * (1.0 + ZONE_PADDING)) ?: return null
        if (isSupport) {
            upper = min(upper, currentPrice - 100L)
        } else {
            lower = max(lower, currentPrice + 100L)
        }
        if (lower <= 0L || upper <= 0L || lower > upper) return null
        return PriceZone(lower = lower, upper = upper, touchCount = count)
    }

    private fun roundHundred(value: Double): Long? {
        if (!value.isFinite() || value <= 0.0) return null
        val units = value / 100.0
        if (units > Long.MAX_VALUE.toDouble() / 100.0) return null
        return units.roundToLong() * 100L
    }

    private data class MacdValues(
        val line: Double,
        val signal: Double,
        val histogram: Double
    )

    private data class BollingerValues(
        val lower: Double,
        val middle: Double,
        val upper: Double
    )

    private data class LevelCluster(
        var minimum: Double,
        var maximum: Double,
        var sum: Double,
        var count: Int
    ) {
        val center: Double
            get() = sum / count
    }

    private const val MINIMUM_SAMPLE_SIZE = 20
    private const val MACD_MINIMUM_SAMPLE_SIZE = 35
    private const val MAX_SAMPLE_SIZE = 100
    private const val MAX_ZONES_PER_SIDE = 2
    private const val CLUSTER_TOLERANCE = 0.018
    private const val ZONE_PADDING = 0.006
    private const val TRADING_DAYS_PER_YEAR = 252.0
}
