package com.chlqudco.kvalue.domain.model

import java.time.LocalDate

enum class ForecastUnavailableReason {
    INSUFFICIENT_HISTORY,
    INVALID_PRICE_HISTORY
}

enum class ForecastConfidence {
    LIMITED,
    MODERATE
}

enum class ForecastDirection {
    UPWARD,
    NEUTRAL,
    DOWNWARD
}

data class DirectionForecast(
    val direction: ForecastDirection,
    val upwardProbabilityPercent: Double,
    val neutralProbabilityPercent: Double,
    val downwardProbabilityPercent: Double,
    val medianReturnPercent: Double,
    val medianPrice: Long,
    val analogCount: Int
)

data class ForecastValidation(
    val directionPredictionCount: Int,
    val directionAccuracyPercent: Double?,
    val modelBrierScore: Double?,
    val baselineBrierScore: Double?,
    val brierSkillPercent: Double?,
    val intervalPredictionCount: Int,
    val intervalCoveragePercent: Double?,
    val targetIntervalCoveragePercent: Double,
    val directionPassed: Boolean
)

data class HistoricalForecast(
    val horizonTradingDays: Int,
    val lowerReturnPercent: Double,
    val upperReturnPercent: Double,
    val lowerPrice: Long,
    val upperPrice: Long,
    val observationCount: Int,
    val rangeSampleCount: Int,
    val historyStartDate: LocalDate,
    val historyEndDate: LocalDate,
    val confidence: ForecastConfidence,
    val validation: ForecastValidation,
    val direction: DirectionForecast?
)

sealed interface HistoricalForecastResult {
    data class Available(val forecast: HistoricalForecast) : HistoricalForecastResult

    data class Unavailable(
        val reason: ForecastUnavailableReason,
        val observationCount: Int,
        val requiredObservationCount: Int
    ) : HistoricalForecastResult
}
