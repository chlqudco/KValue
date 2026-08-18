package com.chlqudco.kvalue.domain.model

import java.time.LocalDate

data class PriceLevel(
    val price: Long,
    val touchCount: Int
)

data class SupportResistanceIndicator(
    val referencePrice: Long,
    val asOf: LocalDate,
    val sampleSize: Int,
    val pivotWindowSize: Int,
    val clusterTolerancePercent: Double,
    val minimumLevelDistancePercent: Double,
    val supportLevels: List<PriceLevel>,
    val resistanceLevels: List<PriceLevel>
)

enum class SupportResistanceUnavailableReason {
    INSUFFICIENT_HISTORY,
    INVALID_CURRENT_PRICE,
    NO_DISTINCT_LEVELS
}

sealed interface SupportResistanceResult {
    data class Available(
        val indicator: SupportResistanceIndicator
    ) : SupportResistanceResult

    data class Unavailable(
        val reason: SupportResistanceUnavailableReason,
        val observationCount: Int,
        val requiredObservationCount: Int
    ) : SupportResistanceResult
}
