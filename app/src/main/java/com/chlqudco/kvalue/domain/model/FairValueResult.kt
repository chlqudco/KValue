package com.chlqudco.kvalue.domain.model

data class PerAssumptions(
    val conservative: Double = 10.0,
    val base: Double = 15.0,
    val optimistic: Double = 20.0
)

enum class FairValueBand {
    BELOW_CONSERVATIVE,
    BETWEEN_CONSERVATIVE_AND_BASE,
    BETWEEN_BASE_AND_OPTIMISTIC,
    ABOVE_OPTIMISTIC
}

data class FairValueResult(
    val conservative: Long,
    val base: Long,
    val optimistic: Long,
    val baseGapPercent: Double,
    val band: FairValueBand
)
