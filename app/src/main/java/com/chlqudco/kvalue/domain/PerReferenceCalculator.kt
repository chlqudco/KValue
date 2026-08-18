package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.PerReferenceBand
import com.chlqudco.kvalue.domain.model.PerReferenceResult
import com.chlqudco.kvalue.domain.model.PerAssumptions
import kotlin.math.roundToLong

object PerReferenceCalculator {
    fun calculate(
        eps: Double,
        assumptions: PerAssumptions,
        currentPrice: Long
    ): PerReferenceResult? {
        val values = listOf(
            eps,
            assumptions.conservative,
            assumptions.base,
            assumptions.optimistic
        )
        if (values.any { !it.isFinite() || it <= 0.0 }) return null
        if (assumptions.conservative > assumptions.base) return null
        if (assumptions.base > assumptions.optimistic) return null
        if (currentPrice <= 0L) return null

        val conservative = roundedPrice(eps * assumptions.conservative) ?: return null
        val base = roundedPrice(eps * assumptions.base) ?: return null
        val optimistic = roundedPrice(eps * assumptions.optimistic) ?: return null
        val gap = (base.toDouble() / currentPrice - 1.0) * 100.0
        if (!gap.isFinite()) return null

        val band = when {
            currentPrice < conservative -> PerReferenceBand.BELOW_CONSERVATIVE
            currentPrice <= base -> PerReferenceBand.BETWEEN_CONSERVATIVE_AND_BASE
            currentPrice <= optimistic -> PerReferenceBand.BETWEEN_BASE_AND_OPTIMISTIC
            else -> PerReferenceBand.ABOVE_OPTIMISTIC
        }
        return PerReferenceResult(
            conservative = conservative,
            base = base,
            optimistic = optimistic,
            baseGapPercent = gap,
            band = band
        )
    }

    private fun roundedPrice(value: Double): Long? {
        if (!value.isFinite() || value <= 0.0) return null
        val units = value / 100.0
        if (units > Long.MAX_VALUE.toDouble() / 100.0) return null
        return units.roundToLong() * 100L
    }
}
