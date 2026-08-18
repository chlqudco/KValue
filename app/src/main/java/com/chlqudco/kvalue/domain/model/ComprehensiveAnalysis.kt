package com.chlqudco.kvalue.domain.model

import java.time.LocalDate

enum class TechnicalDirection {
    POSITIVE,
    NEUTRAL,
    NEGATIVE
}

enum class RsiState {
    OVERBOUGHT,
    POSITIVE,
    NEUTRAL,
    NEGATIVE,
    OVERSOLD
}

data class PriceZone(
    val lower: Long,
    val upper: Long,
    val touchCount: Int
)

data class TechnicalAnalysisResult(
    val asOf: LocalDate,
    val sampleSize: Int,
    val movingAverage5: Double?,
    val movingAverage20: Double,
    val movingAverage60: Double?,
    val rsi14: Double,
    val rsiState: RsiState,
    val macd: Double?,
    val macdSignal: Double?,
    val macdHistogram: Double?,
    val bollingerLower: Double,
    val bollingerMiddle: Double,
    val bollingerUpper: Double,
    val annualizedVolatilityPercent: Double?,
    val volumeRatio20: Double?,
    val supportZones: List<PriceZone>,
    val resistanceZones: List<PriceZone>,
    val direction: TechnicalDirection,
    val score: Int
)

enum class FinancialTrend {
    IMPROVING,
    MIXED,
    WEAKENING,
    UNAVAILABLE
}

data class FinancialTrendResult(
    val trend: FinancialTrend,
    val risingMetricCount: Int,
    val fallingMetricCount: Int,
    val comparableMetricCount: Int
)

enum class ReferenceMethod {
    PER,
    SRIM
}

data class CompositeReferenceResult(
    val lower: Long,
    val base: Long,
    val upper: Long,
    val baseGapPercent: Double,
    val methods: Set<ReferenceMethod>
)

enum class ComprehensiveView {
    POSITIVE,
    BALANCED,
    CAUTIOUS
}

data class ComprehensiveAnalysisResult(
    val reference: CompositeReferenceResult?,
    val technical: TechnicalAnalysisResult?,
    val financialTrend: FinancialTrendResult,
    val view: ComprehensiveView,
    val score: Int
)
