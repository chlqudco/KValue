package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.AnnualFinancial
import com.chlqudco.kvalue.domain.model.CompositeReferenceResult
import com.chlqudco.kvalue.domain.model.ComprehensiveAnalysisResult
import com.chlqudco.kvalue.domain.model.ComprehensiveView
import com.chlqudco.kvalue.domain.model.PerReferenceResult
import com.chlqudco.kvalue.domain.model.FinancialTrend
import com.chlqudco.kvalue.domain.model.FinancialTrendResult
import com.chlqudco.kvalue.domain.model.ReferenceMethod
import com.chlqudco.kvalue.domain.model.SrimValueResult
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.domain.model.TechnicalDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

object ComprehensiveAnalysisCalculator {
    fun calculate(
        analysis: StockAnalysis,
        perReference: PerReferenceResult?,
        srimReference: SrimValueResult?
    ): ComprehensiveAnalysisResult {
        val technical = TechnicalAnalysisCalculator.calculate(
            priceHistory = analysis.priceHistory,
            currentPrice = analysis.price.currentPrice
        )
        val financialTrend = financialTrend(analysis.annualFinancials)
        val reference = compositeReference(
            perReference = perReference,
            srimReference = srimReference,
            currentPrice = analysis.price.currentPrice
        )
        var score = when {
            reference == null -> 0
            reference.baseGapPercent >= 20.0 -> 2
            reference.baseGapPercent >= 5.0 -> 1
            reference.baseGapPercent <= -20.0 -> -2
            reference.baseGapPercent <= -5.0 -> -1
            else -> 0
        }
        score += when (technical?.direction) {
            TechnicalDirection.POSITIVE -> 2
            TechnicalDirection.NEGATIVE -> -2
            TechnicalDirection.NEUTRAL, null -> 0
        }
        score += when (financialTrend.trend) {
            FinancialTrend.IMPROVING -> 1
            FinancialTrend.WEAKENING -> -1
            FinancialTrend.MIXED, FinancialTrend.UNAVAILABLE -> 0
        }
        return ComprehensiveAnalysisResult(
            reference = reference,
            technical = technical,
            financialTrend = financialTrend,
            view = when {
                score >= 2 -> ComprehensiveView.POSITIVE
                score <= -2 -> ComprehensiveView.CAUTIOUS
                else -> ComprehensiveView.BALANCED
            },
            score = score
        )
    }

    fun compositeReference(
        perReference: PerReferenceResult?,
        srimReference: SrimValueResult?,
        currentPrice: Long
    ): CompositeReferenceResult? {
        if (currentPrice <= 0L || perReference == null && srimReference == null) return null
        val srimValues = srimReference?.let {
            listOf(it.fastFade, it.gradualFade, it.persistent).sorted()
        }
        val lower = combine(perReference?.conservative, srimValues?.get(0)) ?: return null
        val base = combine(perReference?.base, srimValues?.get(1)) ?: return null
        val upper = combine(perReference?.optimistic, srimValues?.get(2)) ?: return null
        val gap = (base.toDouble() / currentPrice - 1.0) * 100.0
        if (!gap.isFinite()) return null
        val methods = buildSet {
            if (perReference != null) add(ReferenceMethod.PER)
            if (srimReference != null) add(ReferenceMethod.SRIM)
        }
        return CompositeReferenceResult(
            lower = lower,
            base = base,
            upper = upper,
            baseGapPercent = gap,
            methods = methods
        )
    }

    fun financialTrend(annualFinancials: List<AnnualFinancial>): FinancialTrendResult {
        val rows = annualFinancials.sortedBy(AnnualFinancial::fiscalYear)
        val changes = listOf(
            rows.mapNotNull { it.revenue },
            rows.mapNotNull { it.operatingIncome },
            rows.mapNotNull { it.netIncome }
        ).mapNotNull(::direction)
        if (changes.isEmpty()) {
            return FinancialTrendResult(FinancialTrend.UNAVAILABLE, 0, 0, 0)
        }
        val rising = changes.count { it > 0 }
        val falling = changes.count { it < 0 }
        val trend = when {
            rising >= 2 && rising > falling -> FinancialTrend.IMPROVING
            falling >= 2 && falling > rising -> FinancialTrend.WEAKENING
            else -> FinancialTrend.MIXED
        }
        return FinancialTrendResult(
            trend = trend,
            risingMetricCount = rising,
            fallingMetricCount = falling,
            comparableMetricCount = changes.size
        )
    }

    private fun direction(values: List<Long>): Int? {
        if (values.size < 2) return null
        val first = values.first().toDouble()
        val last = values.last().toDouble()
        val scale = max(max(abs(first), abs(last)), 1.0)
        val change = (last - first) / scale
        return when {
            change > FINANCIAL_CHANGE_THRESHOLD -> 1
            change < -FINANCIAL_CHANGE_THRESHOLD -> -1
            else -> 0
        }
    }

    private fun combine(first: Long?, second: Long?): Long? {
        val value = when {
            first != null && second != null -> (first.toDouble() + second.toDouble()) / 2.0
            first != null -> first.toDouble()
            second != null -> second.toDouble()
            else -> return null
        }
        if (!value.isFinite() || value <= 0.0) return null
        val units = value / 100.0
        if (units > Long.MAX_VALUE.toDouble() / 100.0) return null
        return units.roundToLong() * 100L
    }

    private const val FINANCIAL_CHANGE_THRESHOLD = 0.05
}
