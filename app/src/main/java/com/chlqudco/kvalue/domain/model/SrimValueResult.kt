package com.chlqudco.kvalue.domain.model

data class SrimAssumptions(
    val returnOnEquityPercent: Double,
    val requiredReturnPercent: Double = 10.0
)

data class SrimValueResult(
    val fastFade: Long,
    val gradualFade: Long,
    val persistent: Long,
    val gradualGapPercent: Double,
    val excessReturnPercent: Double
)
