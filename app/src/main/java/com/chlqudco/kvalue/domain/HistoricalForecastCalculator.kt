package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.DirectionForecast
import com.chlqudco.kvalue.domain.model.ForecastConfidence
import com.chlqudco.kvalue.domain.model.ForecastDirection
import com.chlqudco.kvalue.domain.model.ForecastUnavailableReason
import com.chlqudco.kvalue.domain.model.ForecastValidation
import com.chlqudco.kvalue.domain.model.HistoricalForecast
import com.chlqudco.kvalue.domain.model.HistoricalForecastResult
import com.chlqudco.kvalue.domain.model.PricePoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

object HistoricalForecastCalculator {
    const val MINIMUM_OBSERVATION_COUNT = 180
    const val HORIZON_TRADING_DAYS = 5

    fun calculate(
        history: List<PricePoint>,
        currentPrice: Long
    ): HistoricalForecastResult {
        val normalized = history
            .filter { it.close > 0L }
            .distinctBy(PricePoint::date)
            .sortedBy(PricePoint::date)
        if (currentPrice <= 0L) {
            return unavailable(
                ForecastUnavailableReason.INVALID_PRICE_HISTORY,
                normalized.size
            )
        }
        if (normalized.size < MINIMUM_OBSERVATION_COUNT) {
            return unavailable(
                ForecastUnavailableReason.INSUFFICIENT_HISTORY,
                normalized.size
            )
        }
        if (normalized.any { !it.close.toDouble().isFinite() }) {
            return unavailable(
                ForecastUnavailableReason.INVALID_PRICE_HISTORY,
                normalized.size
            )
        }

        val features = normalized.indices.map { featureAt(normalized, it) }
        val range = estimateRange(normalized, normalized.lastIndex)
            ?: return unavailable(
                ForecastUnavailableReason.INVALID_PRICE_HISTORY,
                normalized.size
            )
        val validation = validate(normalized, features)
        val lowerPrice = projectedPrice(currentPrice, range.lowerReturn)
        val upperPrice = projectedPrice(currentPrice, range.upperReturn)
        if (lowerPrice == null || upperPrice == null) {
            return unavailable(
                ForecastUnavailableReason.INVALID_PRICE_HISTORY,
                normalized.size
            )
        }

        val directionEstimate = estimateDirection(
            points = normalized,
            features = features,
            targetAnchor = normalized.lastIndex,
            candidateEndInclusive = normalized.lastIndex - HORIZON_TRADING_DAYS
        )
        val direction = if (validation.directionPassed && directionEstimate != null) {
            projectedPrice(currentPrice, directionEstimate.medianReturn)?.let { medianPrice ->
                DirectionForecast(
                    direction = directionEstimate.direction,
                    upwardProbabilityPercent = directionEstimate.probabilities.upward * 100.0,
                    neutralProbabilityPercent = directionEstimate.probabilities.neutral * 100.0,
                    downwardProbabilityPercent = directionEstimate.probabilities.downward * 100.0,
                    medianReturnPercent = directionEstimate.medianReturn * 100.0,
                    medianPrice = medianPrice,
                    analogCount = directionEstimate.analogCount
                )
            }
        } else {
            null
        }
        val confidence = if (
            validation.intervalPredictionCount >= MODERATE_VALIDATION_COUNT &&
            validation.intervalCoveragePercent?.let {
                abs(it - TARGET_INTERVAL_COVERAGE_PERCENT) <=
                    MODERATE_COVERAGE_TOLERANCE_PERCENT
            } == true
        ) {
            ForecastConfidence.MODERATE
        } else {
            ForecastConfidence.LIMITED
        }

        return HistoricalForecastResult.Available(
            HistoricalForecast(
                horizonTradingDays = HORIZON_TRADING_DAYS,
                lowerReturnPercent = range.lowerReturn * 100.0,
                upperReturnPercent = range.upperReturn * 100.0,
                lowerPrice = minOf(lowerPrice, upperPrice),
                upperPrice = maxOf(lowerPrice, upperPrice),
                observationCount = normalized.size,
                rangeSampleCount = range.sampleCount,
                historyStartDate = normalized.first().date,
                historyEndDate = normalized.last().date,
                confidence = confidence,
                validation = validation,
                direction = direction
            )
        )
    }

    private fun validate(
        points: List<PricePoint>,
        features: List<FeatureSnapshot?>
    ): ForecastValidation {
        val directionOutcomes = buildList {
            val minimumAnchor = FEATURE_WINDOW + HORIZON_TRADING_DAYS +
                MINIMUM_DIRECTION_CANDIDATE_COUNT - 1
            val lastAnchor = points.lastIndex - HORIZON_TRADING_DAYS
            if (minimumAnchor <= lastAnchor) {
                for (anchor in minimumAnchor..lastAnchor step HORIZON_TRADING_DAYS) {
                    val estimate = estimateDirection(
                        points = points,
                        features = features,
                        targetAnchor = anchor,
                        candidateEndInclusive = anchor - HORIZON_TRADING_DAYS
                    ) ?: continue
                    val actualReturn = returnBetween(
                        points[anchor].close.toDouble(),
                        points[anchor + HORIZON_TRADING_DAYS].close.toDouble()
                    ) ?: continue
                    val actual = classify(actualReturn, estimate.neutralThreshold)
                    add(
                        DirectionValidationOutcome(
                            modelBrier = brierScore(estimate.probabilities, actual),
                            baselineBrier = brierScore(
                                estimate.baselineProbabilities,
                                actual
                            ),
                            correct = estimate.predictedClass == actual
                        )
                    )
                }
            }
        }
        val intervalOutcomes = buildList {
            val lastAnchor = points.lastIndex - HORIZON_TRADING_DAYS
            if (RANGE_VALIDATION_START_ANCHOR <= lastAnchor) {
                for (
                    anchor in RANGE_VALIDATION_START_ANCHOR..lastAnchor
                    step HORIZON_TRADING_DAYS
                ) {
                    val estimate = estimateRange(points, anchor) ?: continue
                    val actualReturn = returnBetween(
                        points[anchor].close.toDouble(),
                        points[anchor + HORIZON_TRADING_DAYS].close.toDouble()
                    ) ?: continue
                    add(
                        actualReturn >= estimate.lowerReturn - ZERO_TOLERANCE &&
                            actualReturn <= estimate.upperReturn + ZERO_TOLERANCE
                    )
                }
            }
        }

        val modelBrier = directionOutcomes
            .takeIf { it.isNotEmpty() }
            ?.map(DirectionValidationOutcome::modelBrier)
            ?.average()
        val baselineBrier = directionOutcomes
            .takeIf { it.isNotEmpty() }
            ?.map(DirectionValidationOutcome::baselineBrier)
            ?.average()
        val brierSkill = if (
            modelBrier != null &&
            baselineBrier != null &&
            baselineBrier > ZERO_TOLERANCE
        ) {
            (baselineBrier - modelBrier) / baselineBrier * 100.0
        } else {
            null
        }
        val winningFoldCount = winningFoldCount(directionOutcomes)
        val requiredWinningFolds = minOf(
            MINIMUM_WINNING_FOLD_COUNT,
            minOf(VALIDATION_FOLD_COUNT, directionOutcomes.size)
        )
        val directionPassed = directionOutcomes.size >= MINIMUM_DIRECTION_VALIDATION_COUNT &&
            brierSkill != null &&
            brierSkill > MINIMUM_BRIER_SKILL_PERCENT &&
            winningFoldCount >= requiredWinningFolds

        return ForecastValidation(
            directionPredictionCount = directionOutcomes.size,
            directionAccuracyPercent = directionOutcomes
                .takeIf { it.isNotEmpty() }
                ?.count(DirectionValidationOutcome::correct)
                ?.toDouble()
                ?.div(directionOutcomes.size)
                ?.times(100.0),
            modelBrierScore = modelBrier,
            baselineBrierScore = baselineBrier,
            brierSkillPercent = brierSkill,
            intervalPredictionCount = intervalOutcomes.size,
            intervalCoveragePercent = intervalOutcomes
                .takeIf { it.isNotEmpty() }
                ?.count { it }
                ?.toDouble()
                ?.div(intervalOutcomes.size)
                ?.times(100.0),
            targetIntervalCoveragePercent = TARGET_INTERVAL_COVERAGE_PERCENT,
            directionPassed = directionPassed
        )
    }

    private fun estimateDirection(
        points: List<PricePoint>,
        features: List<FeatureSnapshot?>,
        targetAnchor: Int,
        candidateEndInclusive: Int
    ): DirectionEstimate? {
        val target = features.getOrNull(targetAnchor) ?: return null
        val firstCandidate = maxOf(
            FEATURE_WINDOW,
            candidateEndInclusive - MAXIMUM_DIRECTION_CANDIDATE_COUNT + 1
        )
        val candidates = if (firstCandidate <= candidateEndInclusive) {
            (firstCandidate..candidateEndInclusive).mapNotNull { anchor ->
                val feature = features.getOrNull(anchor) ?: return@mapNotNull null
                val outcome = returnBetween(
                    points[anchor].close.toDouble(),
                    points[anchor + HORIZON_TRADING_DAYS].close.toDouble()
                ) ?: return@mapNotNull null
                DirectionCandidate(anchor, feature.values, outcome)
            }
        } else {
            emptyList()
        }
        if (candidates.size < MINIMUM_DIRECTION_CANDIDATE_COUNT) return null

        val featureMeans = DoubleArray(FEATURE_COUNT) { index ->
            candidates.map { it.features[index] }.average()
        }
        val featureScales = DoubleArray(FEATURE_COUNT) { index ->
            val variance = candidates.map {
                val difference = it.features[index] - featureMeans[index]
                difference * difference
            }.average()
            sqrt(variance).takeIf { it > ZERO_TOLERANCE } ?: 1.0
        }
        val analogs = candidates
            .map { candidate ->
                val distance = (0 until FEATURE_COUNT).sumOf { index ->
                    val standardized =
                        (candidate.features[index] - target.values[index]) /
                            featureScales[index]
                    standardized * standardized
                }
                WeightedCandidate(
                    outcome = candidate.outcome,
                    weight = 1.0 / (sqrt(max(0.0, distance)) + DISTANCE_WEIGHT_FLOOR)
                )
            }
            .sortedByDescending(WeightedCandidate::weight)
            .take(minOf(NEIGHBOR_COUNT, candidates.size))
        if (analogs.size < MINIMUM_ANALOG_COUNT) return null

        val neutralThreshold = maxOf(
            MINIMUM_NEUTRAL_RETURN,
            target.dailyVolatility * sqrt(HORIZON_TRADING_DAYS.toDouble()) *
                NEUTRAL_VOLATILITY_MULTIPLIER
        )
        val baselineSamples = candidates.map { candidate ->
            val age = (candidateEndInclusive - candidate.anchor).coerceAtLeast(0)
            WeightedOutcome(
                outcomeClass = classify(candidate.outcome, neutralThreshold),
                weight = 0.5.pow(age / BASELINE_HALF_LIFE_TRADING_DAYS)
            )
        }
        val baselineProbabilities = probabilities(baselineSamples) ?: return null
        val analogProbabilities = probabilities(
            analogs.map {
                WeightedOutcome(
                    outcomeClass = classify(it.outcome, neutralThreshold),
                    weight = it.weight
                )
            }
        ) ?: return null
        val shrinkage = analogs.size.toDouble() /
            (analogs.size + PROBABILITY_SHRINKAGE_STRENGTH)
        val probabilities = ProbabilityVector(
            upward = baselineProbabilities.upward +
                shrinkage * (analogProbabilities.upward - baselineProbabilities.upward),
            neutral = baselineProbabilities.neutral +
                shrinkage * (analogProbabilities.neutral - baselineProbabilities.neutral),
            downward = baselineProbabilities.downward +
                shrinkage * (analogProbabilities.downward - baselineProbabilities.downward)
        ).normalized() ?: return null
        val medianReturn = weightedPercentile(
            analogs.map { WeightedValue(it.outcome, it.weight) },
            MEDIAN_PERCENTILE
        ) ?: return null
        val predictedClass = predictedClass(probabilities)
        return DirectionEstimate(
            probabilities = probabilities,
            baselineProbabilities = baselineProbabilities,
            predictedClass = predictedClass,
            direction = when (predictedClass) {
                OutcomeClass.UPWARD -> ForecastDirection.UPWARD
                OutcomeClass.NEUTRAL -> ForecastDirection.NEUTRAL
                OutcomeClass.DOWNWARD -> ForecastDirection.DOWNWARD
            },
            medianReturn = medianReturn,
            analogCount = analogs.size,
            neutralThreshold = neutralThreshold
        )
    }

    private fun estimateRange(
        points: List<PricePoint>,
        targetAnchor: Int
    ): RangeEstimate? {
        val candidateEndInclusive = targetAnchor - HORIZON_TRADING_DAYS
        if (candidateEndInclusive < 0) return null
        val candidateStart = maxOf(
            0,
            candidateEndInclusive - MAXIMUM_RANGE_SAMPLE_COUNT + 1
        )
        val samples = (candidateStart..candidateEndInclusive).mapNotNull { anchor ->
            val outcome = returnBetween(
                points[anchor].close.toDouble(),
                points[anchor + HORIZON_TRADING_DAYS].close.toDouble()
            ) ?: return@mapNotNull null
            val age = candidateEndInclusive - anchor
            WeightedValue(
                value = outcome,
                weight = 0.5.pow(age / RANGE_HALF_LIFE_TRADING_DAYS)
            )
        }
        if (samples.size < MINIMUM_RANGE_SAMPLE_COUNT) return null
        val lower = weightedPercentile(samples, LOWER_RANGE_PERCENTILE) ?: return null
        val upper = weightedPercentile(samples, UPPER_RANGE_PERCENTILE) ?: return null
        return RangeEstimate(
            lowerReturn = minOf(lower, 0.0),
            upperReturn = maxOf(upper, 0.0),
            sampleCount = samples.size
        )
    }

    private fun featureAt(points: List<PricePoint>, anchor: Int): FeatureSnapshot? {
        if (anchor < FEATURE_WINDOW || anchor >= points.size) return null
        val close = points[anchor].close.toDouble()
        val return1 = returnBetween(points[anchor - 1].close.toDouble(), close) ?: return null
        val return5 = returnBetween(points[anchor - 5].close.toDouble(), close) ?: return null
        val return20 = returnBetween(points[anchor - 20].close.toDouble(), close) ?: return null
        val return60 = returnBetween(points[anchor - 60].close.toDouble(), close) ?: return null
        val recentCloses = (anchor - MOVING_AVERAGE_WINDOW + 1..anchor)
            .map { points[it].close.toDouble() }
        val movingAverage = recentCloses.average().takeIf { it > 0.0 } ?: return null
        val movingAverageGap = close / movingAverage - 1.0
        val dailyReturns = (anchor - VOLATILITY_WINDOW + 1..anchor).mapNotNull { index ->
            returnBetween(points[index - 1].close.toDouble(), points[index].close.toDouble())
        }
        if (dailyReturns.size != VOLATILITY_WINDOW) return null
        val dailyReturnMean = dailyReturns.average()
        val dailyVolatility = sqrt(
            dailyReturns.sumOf {
                val difference = it - dailyReturnMean
                difference * difference
            } / dailyReturns.size
        )
        val rsi = rsi(points, anchor) ?: return null
        val atrRatio = atrRatio(points, anchor) ?: return null
        val volumeZScore = volumeZScore(points, anchor) ?: return null
        val open = points[anchor].open?.toDouble()?.takeIf { it > 0.0 } ?: return null
        val high = points[anchor].high?.toDouble()?.takeIf { it > 0.0 } ?: return null
        val low = points[anchor].low?.toDouble()?.takeIf { it > 0.0 } ?: return null
        val previousClose = points[anchor - 1].close.toDouble()
        if (high < low || previousClose <= 0.0) return null
        val gapReturn = open / previousClose - 1.0
        val intradayRange = (high - low) / previousClose
        val values = doubleArrayOf(
            return1,
            return5,
            return20,
            return60,
            movingAverageGap,
            dailyVolatility,
            rsi,
            atrRatio,
            volumeZScore,
            gapReturn,
            intradayRange
        )
        if (values.any { !it.isFinite() }) return null
        return FeatureSnapshot(values, dailyVolatility)
    }

    private fun rsi(points: List<PricePoint>, anchor: Int): Double? {
        val changes = (anchor - RSI_WINDOW + 1..anchor).map { index ->
            returnBetween(
                points[index - 1].close.toDouble(),
                points[index].close.toDouble()
            ) ?: return null
        }
        val averageGain = changes.sumOf { maxOf(it, 0.0) } / changes.size
        val averageLoss = changes.sumOf { maxOf(-it, 0.0) } / changes.size
        return when {
            averageGain <= ZERO_TOLERANCE && averageLoss <= ZERO_TOLERANCE -> 0.5
            averageLoss <= ZERO_TOLERANCE -> 1.0
            else -> {
                val relativeStrength = averageGain / averageLoss
                1.0 - 1.0 / (1.0 + relativeStrength)
            }
        }
    }

    private fun atrRatio(points: List<PricePoint>, anchor: Int): Double? {
        val trueRanges = (anchor - ATR_WINDOW + 1..anchor).map { index ->
            val high = points[index].high?.toDouble()?.takeIf { it > 0.0 } ?: return null
            val low = points[index].low?.toDouble()?.takeIf { it > 0.0 } ?: return null
            val previousClose = points[index - 1].close.toDouble().takeIf { it > 0.0 }
                ?: return null
            if (high < low) return null
            maxOf(
                high - low,
                abs(high - previousClose),
                abs(low - previousClose)
            ) / previousClose
        }
        return trueRanges.average().takeIf(Double::isFinite)
    }

    private fun volumeZScore(points: List<PricePoint>, anchor: Int): Double? {
        val volumes = (anchor - VOLUME_WINDOW + 1..anchor).map { index ->
            points[index].volume?.toDouble()?.takeIf { it > 0.0 } ?: return null
        }
        val mean = volumes.average()
        val variance = volumes.sumOf {
            val difference = it - mean
            difference * difference
        } / volumes.size
        val scale = sqrt(variance)
        return if (scale <= ZERO_TOLERANCE) {
            0.0
        } else {
            (volumes.last() - mean) / scale
        }
    }

    private fun probabilities(samples: List<WeightedOutcome>): ProbabilityVector? {
        val totalWeight = samples.sumOf(WeightedOutcome::weight)
        if (!totalWeight.isFinite() || totalWeight <= ZERO_TOLERANCE) return null
        return ProbabilityVector(
            upward = samples.filter { it.outcomeClass == OutcomeClass.UPWARD }
                .sumOf(WeightedOutcome::weight) / totalWeight,
            neutral = samples.filter { it.outcomeClass == OutcomeClass.NEUTRAL }
                .sumOf(WeightedOutcome::weight) / totalWeight,
            downward = samples.filter { it.outcomeClass == OutcomeClass.DOWNWARD }
                .sumOf(WeightedOutcome::weight) / totalWeight
        ).normalized()
    }

    private fun ProbabilityVector.normalized(): ProbabilityVector? {
        val total = upward + neutral + downward
        if (!total.isFinite() || total <= ZERO_TOLERANCE) return null
        return ProbabilityVector(
            upward = (upward / total).coerceIn(0.0, 1.0),
            neutral = (neutral / total).coerceIn(0.0, 1.0),
            downward = (downward / total).coerceIn(0.0, 1.0)
        )
    }

    private fun classify(value: Double, neutralThreshold: Double): OutcomeClass = when {
        value > neutralThreshold -> OutcomeClass.UPWARD
        value < -neutralThreshold -> OutcomeClass.DOWNWARD
        else -> OutcomeClass.NEUTRAL
    }

    private fun predictedClass(probabilities: ProbabilityVector): OutcomeClass {
        val directionalDifference = probabilities.upward - probabilities.downward
        return when {
            probabilities.neutral >= probabilities.upward &&
                probabilities.neutral >= probabilities.downward -> OutcomeClass.NEUTRAL
            directionalDifference >= MINIMUM_DIRECTION_PROBABILITY_SPREAD -> {
                OutcomeClass.UPWARD
            }
            directionalDifference <= -MINIMUM_DIRECTION_PROBABILITY_SPREAD -> {
                OutcomeClass.DOWNWARD
            }
            else -> OutcomeClass.NEUTRAL
        }
    }

    private fun brierScore(
        probabilities: ProbabilityVector,
        actual: OutcomeClass
    ): Double {
        val upwardTarget = if (actual == OutcomeClass.UPWARD) 1.0 else 0.0
        val neutralTarget = if (actual == OutcomeClass.NEUTRAL) 1.0 else 0.0
        val downwardTarget = if (actual == OutcomeClass.DOWNWARD) 1.0 else 0.0
        return (
            (probabilities.upward - upwardTarget).let { it * it } +
                (probabilities.neutral - neutralTarget).let { it * it } +
                (probabilities.downward - downwardTarget).let { it * it }
            ) / OUTCOME_CLASS_COUNT
    }

    private fun winningFoldCount(outcomes: List<DirectionValidationOutcome>): Int {
        if (outcomes.isEmpty()) return 0
        val foldCount = minOf(VALIDATION_FOLD_COUNT, outcomes.size)
        return (0 until foldCount).count { fold ->
            val foldOutcomes = outcomes.filterIndexed { index, _ ->
                index * foldCount / outcomes.size == fold
            }
            foldOutcomes.isNotEmpty() &&
                foldOutcomes.map(DirectionValidationOutcome::modelBrier).average() <
                foldOutcomes.map(DirectionValidationOutcome::baselineBrier).average()
        }
    }

    private fun weightedPercentile(
        samples: List<WeightedValue>,
        percentile: Double
    ): Double? {
        val valid = samples
            .filter { it.value.isFinite() && it.weight.isFinite() && it.weight > 0.0 }
            .sortedBy(WeightedValue::value)
        if (valid.isEmpty()) return null
        val totalWeight = valid.sumOf(WeightedValue::weight)
        if (totalWeight <= ZERO_TOLERANCE) return null
        val targetWeight = totalWeight * percentile.coerceIn(0.0, 1.0)
        var cumulativeWeight = 0.0
        valid.forEach { sample ->
            cumulativeWeight += sample.weight
            if (cumulativeWeight >= targetWeight) return sample.value
        }
        return valid.last().value
    }

    private fun returnBetween(start: Double, end: Double): Double? {
        if (!start.isFinite() || !end.isFinite() || start <= 0.0 || end <= 0.0) return null
        return (end / start - 1.0).takeIf(Double::isFinite)
    }

    private fun projectedPrice(currentPrice: Long, projectedReturn: Double): Long? {
        val value = currentPrice.toDouble() * (1.0 + projectedReturn)
        return value.takeIf { it.isFinite() && it > 0.0 && it <= Long.MAX_VALUE.toDouble() }
            ?.roundToLong()
    }

    private fun unavailable(
        reason: ForecastUnavailableReason,
        observationCount: Int
    ) = HistoricalForecastResult.Unavailable(
        reason = reason,
        observationCount = observationCount,
        requiredObservationCount = MINIMUM_OBSERVATION_COUNT
    )

    private data class FeatureSnapshot(
        val values: DoubleArray,
        val dailyVolatility: Double
    )

    private data class DirectionCandidate(
        val anchor: Int,
        val features: DoubleArray,
        val outcome: Double
    )

    private data class WeightedCandidate(
        val outcome: Double,
        val weight: Double
    )

    private data class WeightedOutcome(
        val outcomeClass: OutcomeClass,
        val weight: Double
    )

    private data class WeightedValue(
        val value: Double,
        val weight: Double
    )

    private data class ProbabilityVector(
        val upward: Double,
        val neutral: Double,
        val downward: Double
    )

    private data class DirectionEstimate(
        val probabilities: ProbabilityVector,
        val baselineProbabilities: ProbabilityVector,
        val predictedClass: OutcomeClass,
        val direction: ForecastDirection,
        val medianReturn: Double,
        val analogCount: Int,
        val neutralThreshold: Double
    )

    private data class RangeEstimate(
        val lowerReturn: Double,
        val upperReturn: Double,
        val sampleCount: Int
    )

    private data class DirectionValidationOutcome(
        val modelBrier: Double,
        val baselineBrier: Double,
        val correct: Boolean
    )

    private enum class OutcomeClass {
        UPWARD,
        NEUTRAL,
        DOWNWARD
    }

    private const val FEATURE_WINDOW = 60
    private const val MOVING_AVERAGE_WINDOW = 20
    private const val VOLATILITY_WINDOW = 20
    private const val RSI_WINDOW = 14
    private const val ATR_WINDOW = 14
    private const val VOLUME_WINDOW = 20
    private const val FEATURE_COUNT = 11
    private const val MAXIMUM_DIRECTION_CANDIDATE_COUNT = 500
    private const val MINIMUM_DIRECTION_CANDIDATE_COUNT = 80
    private const val NEIGHBOR_COUNT = 40
    private const val MINIMUM_ANALOG_COUNT = 24
    private const val MINIMUM_DIRECTION_VALIDATION_COUNT = 20
    private const val VALIDATION_FOLD_COUNT = 3
    private const val MINIMUM_WINNING_FOLD_COUNT = 2
    private const val MINIMUM_BRIER_SKILL_PERCENT = 0.0
    private const val MODERATE_VALIDATION_COUNT = 40
    private const val MODERATE_COVERAGE_TOLERANCE_PERCENT = 10.0
    private const val RANGE_VALIDATION_START_ANCHOR = 120
    private const val MAXIMUM_RANGE_SAMPLE_COUNT = 252
    private const val MINIMUM_RANGE_SAMPLE_COUNT = 60
    private const val TARGET_INTERVAL_COVERAGE_PERCENT = 80.0
    private const val LOWER_RANGE_PERCENTILE = 0.10
    private const val UPPER_RANGE_PERCENTILE = 0.90
    private const val MEDIAN_PERCENTILE = 0.50
    private const val RANGE_HALF_LIFE_TRADING_DAYS = 63.0
    private const val BASELINE_HALF_LIFE_TRADING_DAYS = 126.0
    private const val PROBABILITY_SHRINKAGE_STRENGTH = 24.0
    private const val NEUTRAL_VOLATILITY_MULTIPLIER = 0.35
    private const val MINIMUM_NEUTRAL_RETURN = 0.005
    private const val MINIMUM_DIRECTION_PROBABILITY_SPREAD = 0.08
    private const val DISTANCE_WEIGHT_FLOOR = 0.25
    private const val OUTCOME_CLASS_COUNT = 3.0
    private const val ZERO_TOLERANCE = 1e-12
}
