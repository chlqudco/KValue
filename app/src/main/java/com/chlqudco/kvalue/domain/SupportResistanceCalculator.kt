package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.PriceLevel
import com.chlqudco.kvalue.domain.model.PricePoint
import com.chlqudco.kvalue.domain.model.SupportResistanceIndicator
import com.chlqudco.kvalue.domain.model.SupportResistanceResult
import com.chlqudco.kvalue.domain.model.SupportResistanceUnavailableReason
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

object SupportResistanceCalculator {
    const val MINIMUM_OBSERVATION_COUNT = 20
    const val MAXIMUM_OBSERVATION_COUNT = 100
    const val PIVOT_WINDOW_SIZE = 5
    const val CLUSTER_TOLERANCE_PERCENT = 1.5
    const val MINIMUM_LEVEL_DISTANCE_PERCENT = 0.3

    fun calculate(
        priceHistory: List<PricePoint>,
        currentPrice: Long
    ): SupportResistanceResult {
        val points = priceHistory
            .filter { it.close > 0L }
            .sortedBy(PricePoint::date)
            .distinctBy(PricePoint::date)
            .takeLast(MAXIMUM_OBSERVATION_COUNT)
        if (currentPrice <= 0L) {
            return unavailable(
                reason = SupportResistanceUnavailableReason.INVALID_CURRENT_PRICE,
                observationCount = points.size
            )
        }
        if (points.size < MINIMUM_OBSERVATION_COUNT) {
            return unavailable(
                reason = SupportResistanceUnavailableReason.INSUFFICIENT_HISTORY,
                observationCount = points.size
            )
        }

        val candidates = findCandidates(points)
        val clusters = cluster(candidates)
        val current = currentPrice.toDouble()
        val minimumDistance = current * MINIMUM_LEVEL_DISTANCE_PERCENT / 100.0
        val supports = clusters
            .filter { it.center <= current - minimumDistance }
            .sortedByDescending(LevelCluster::center)
            .take(MAXIMUM_LEVELS_PER_SIDE)
            .map { it.toPriceLevel() }
        val resistances = clusters
            .filter { it.center >= current + minimumDistance }
            .sortedBy(LevelCluster::center)
            .take(MAXIMUM_LEVELS_PER_SIDE)
            .map { it.toPriceLevel() }

        if (supports.isEmpty() && resistances.isEmpty()) {
            return unavailable(
                reason = SupportResistanceUnavailableReason.NO_DISTINCT_LEVELS,
                observationCount = points.size
            )
        }
        return SupportResistanceResult.Available(
            SupportResistanceIndicator(
                referencePrice = currentPrice,
                asOf = points.last().date,
                sampleSize = points.size,
                pivotWindowSize = PIVOT_WINDOW_SIZE,
                clusterTolerancePercent = CLUSTER_TOLERANCE_PERCENT,
                minimumLevelDistancePercent = MINIMUM_LEVEL_DISTANCE_PERCENT,
                supportLevels = supports,
                resistanceLevels = resistances
            )
        )
    }

    private fun findCandidates(points: List<PricePoint>): List<LevelCandidate> {
        val lows = points.map { point ->
            min(point.close, point.low?.takeIf { it > 0L } ?: point.close).toDouble()
        }
        val highs = points.map { point ->
            max(point.close, point.high?.takeIf { it > 0L } ?: point.close).toDouble()
        }
        val candidates = linkedMapOf<CandidateKey, LevelCandidate>()
        val radius = PIVOT_WINDOW_SIZE / 2
        for (index in radius until points.size - radius) {
            val range = index - radius..index + radius
            val localLows = range.map(lows::get)
            val localHighs = range.map(highs::get)
            if (lows[index] == localLows.minOrNull() && localLows.any { it > lows[index] }) {
                candidates[CandidateKey(index, CandidateType.LOW)] =
                    LevelCandidate(lows[index], index)
            }
            if (highs[index] == localHighs.maxOrNull() && localHighs.any { it < highs[index] }) {
                candidates[CandidateKey(index, CandidateType.HIGH)] =
                    LevelCandidate(highs[index], index)
            }
        }

        (EXTREME_LOOKBACKS + points.size)
            .filter { it <= points.size }
            .distinct()
            .forEach { lookback ->
            val start = points.size - lookback
            val lowIndex = (start until points.size).minBy(lows::get)
            val highIndex = (start until points.size).maxBy(highs::get)
            candidates.putIfAbsent(
                CandidateKey(lowIndex, CandidateType.LOW),
                LevelCandidate(lows[lowIndex], lowIndex)
            )
            candidates.putIfAbsent(
                CandidateKey(highIndex, CandidateType.HIGH),
                LevelCandidate(highs[highIndex], highIndex)
            )
        }
        return candidates.values.toList()
    }

    private fun cluster(candidates: List<LevelCandidate>): List<LevelCluster> {
        val clusters = mutableListOf<MutableLevelCluster>()
        candidates.sortedBy(LevelCandidate::price).forEach { candidate ->
            val target = clusters.lastOrNull()?.takeIf { cluster ->
                abs(candidate.price - cluster.center) / cluster.center <=
                    CLUSTER_TOLERANCE_PERCENT / 100.0
            }
            if (target == null) {
                clusters += MutableLevelCluster(
                    prices = mutableListOf(candidate.price),
                    indices = mutableSetOf(candidate.index)
                )
            } else {
                target.prices += candidate.price
                target.indices += candidate.index
            }
        }
        return clusters.map { cluster ->
            LevelCluster(
                center = cluster.prices.sorted().let { prices ->
                    val middle = prices.size / 2
                    if (prices.size % 2 == 0) {
                        (prices[middle - 1] + prices[middle]) / 2.0
                    } else {
                        prices[middle]
                    }
                },
                touchCount = cluster.indices.size
            )
        }
    }

    private fun LevelCluster.toPriceLevel(): PriceLevel = PriceLevel(
        price = center.roundToLong().coerceAtLeast(1L),
        touchCount = touchCount
    )

    private fun unavailable(
        reason: SupportResistanceUnavailableReason,
        observationCount: Int
    ): SupportResistanceResult.Unavailable = SupportResistanceResult.Unavailable(
        reason = reason,
        observationCount = observationCount,
        requiredObservationCount = MINIMUM_OBSERVATION_COUNT
    )

    private enum class CandidateType {
        LOW,
        HIGH
    }

    private data class CandidateKey(
        val index: Int,
        val type: CandidateType
    )

    private data class LevelCandidate(
        val price: Double,
        val index: Int
    )

    private data class MutableLevelCluster(
        val prices: MutableList<Double>,
        val indices: MutableSet<Int>
    ) {
        val center: Double
            get() = prices.average()
    }

    private data class LevelCluster(
        val center: Double,
        val touchCount: Int
    )

    private val EXTREME_LOOKBACKS = listOf(20, 60, 100)
    private const val MAXIMUM_LEVELS_PER_SIDE = 2
}
