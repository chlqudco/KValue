package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.SrimAssumptions
import com.chlqudco.kvalue.domain.model.SrimValueResult
import kotlin.math.roundToLong

object SrimValueCalculator {
    fun calculate(
        bps: Double,
        assumptions: SrimAssumptions,
        currentPrice: Long
    ): SrimValueResult? {
        val roe = assumptions.returnOnEquityPercent
        val requiredReturn = assumptions.requiredReturnPercent
        if (!bps.isFinite() || bps <= 0.0) return null
        if (!roe.isFinite() || roe <= 0.0) return null
        if (!requiredReturn.isFinite() || requiredReturn <= 0.0 || requiredReturn > 100.0) {
            return null
        }
        if (currentPrice <= 0L) return null

        val roeRate = roe / 100.0
        val requiredReturnRate = requiredReturn / 100.0
        val fastFade = referencePrice(
            bps,
            roeRate,
            requiredReturnRate,
            FAST_FADE_PERSISTENCE
        ) ?: return null
        val gradualFade = referencePrice(
            bps,
            roeRate,
            requiredReturnRate,
            GRADUAL_FADE_PERSISTENCE
        ) ?: return null
        val persistent = referencePrice(
            bps,
            roeRate,
            requiredReturnRate,
            PERSISTENT_PERSISTENCE
        ) ?: return null
        val gap = (gradualFade.toDouble() / currentPrice - 1.0) * 100.0
        if (!gap.isFinite()) return null

        return SrimValueResult(
            fastFade = fastFade,
            gradualFade = gradualFade,
            persistent = persistent,
            gradualGapPercent = gap,
            excessReturnPercent = roe - requiredReturn
        )
    }

    private fun referencePrice(
        bps: Double,
        roeRate: Double,
        requiredReturnRate: Double,
        persistence: Double
    ): Long? {
        val denominator = 1.0 + requiredReturnRate - persistence
        if (!denominator.isFinite() || denominator <= 0.0) return null
        val residualIncome = bps * (roeRate - requiredReturnRate)
        val value = bps + residualIncome * persistence / denominator
        if (!value.isFinite() || value <= 0.0) return null
        val units = value / 100.0
        if (units > Long.MAX_VALUE.toDouble() / 100.0) return null
        return units.roundToLong() * 100L
    }

    private const val FAST_FADE_PERSISTENCE = 0.8
    private const val GRADUAL_FADE_PERSISTENCE = 0.9
    private const val PERSISTENT_PERSISTENCE = 1.0
}
