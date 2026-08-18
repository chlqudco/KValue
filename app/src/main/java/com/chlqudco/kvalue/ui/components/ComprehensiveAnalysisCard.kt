package com.chlqudco.kvalue.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chlqudco.kvalue.R
import com.chlqudco.kvalue.common.NumberFormatter
import com.chlqudco.kvalue.domain.model.CompositeReferenceResult
import com.chlqudco.kvalue.domain.model.ComprehensiveAnalysisResult
import com.chlqudco.kvalue.domain.model.ComprehensiveView
import com.chlqudco.kvalue.domain.model.FinancialTrend
import com.chlqudco.kvalue.domain.model.FinancialTrendResult
import com.chlqudco.kvalue.domain.model.PriceZone
import com.chlqudco.kvalue.domain.model.ReferenceMethod
import com.chlqudco.kvalue.domain.model.RsiState
import com.chlqudco.kvalue.domain.model.TechnicalAnalysisResult
import com.chlqudco.kvalue.domain.model.TechnicalDirection
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun ComprehensiveAnalysisCard(
    result: ComprehensiveAnalysisResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("comprehensive_analysis_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.comprehensive_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.comprehensive_rule_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = viewTitle(result.view),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = viewDescription(result.view),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(
                    R.string.comprehensive_score,
                    result.score.toSignedText()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            HorizontalDivider()
            CompositeReferenceSection(result.reference)
            HorizontalDivider()
            FinancialTrendSection(result.financialTrend)
            HorizontalDivider()
            TechnicalSection(result.technical)
            Text(
                text = stringResource(R.string.comprehensive_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun CompositeReferenceSection(reference: CompositeReferenceResult?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.composite_reference_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (reference == null) {
            Text(stringResource(R.string.composite_reference_unavailable))
            return@Column
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 340.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReferenceValue(R.string.composite_reference_lower, reference.lower)
                    ReferenceValue(R.string.composite_reference_base, reference.base)
                    ReferenceValue(R.string.composite_reference_upper, reference.upper)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReferenceValue(
                        R.string.composite_reference_lower,
                        reference.lower,
                        Modifier.weight(1f)
                    )
                    ReferenceValue(
                        R.string.composite_reference_base,
                        reference.base,
                        Modifier.weight(1f)
                    )
                    ReferenceValue(
                        R.string.composite_reference_upper,
                        reference.upper,
                        Modifier.weight(1f)
                    )
                }
            }
        }
        Text(
            text = referenceMethodText(reference.methods),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = when {
                reference.baseGapPercent > 0.0 -> stringResource(
                    R.string.composite_gap_lower,
                    NumberFormatter.percentage(abs(reference.baseGapPercent))
                )
                reference.baseGapPercent < 0.0 -> stringResource(
                    R.string.composite_gap_higher,
                    NumberFormatter.percentage(abs(reference.baseGapPercent))
                )
                else -> stringResource(R.string.composite_gap_equal)
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ReferenceValue(
    labelResource: Int,
    value: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(labelResource),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = NumberFormatter.won(value),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FinancialTrendSection(result: FinancialTrendResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.financial_trend_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = when (result.trend) {
                FinancialTrend.IMPROVING -> stringResource(
                    R.string.financial_trend_improving,
                    result.risingMetricCount,
                    result.comparableMetricCount
                )
                FinancialTrend.MIXED -> stringResource(
                    R.string.financial_trend_mixed,
                    result.risingMetricCount,
                    result.fallingMetricCount
                )
                FinancialTrend.WEAKENING -> stringResource(
                    R.string.financial_trend_weakening,
                    result.fallingMetricCount,
                    result.comparableMetricCount
                )
                FinancialTrend.UNAVAILABLE -> stringResource(
                    R.string.financial_trend_unavailable
                )
            }
        )
    }
}

@Composable
private fun TechnicalSection(result: TechnicalAnalysisResult?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.technical_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (result == null) {
            Text(stringResource(R.string.technical_unavailable))
            return@Column
        }
        Text(
            text = technicalDirectionText(result.direction),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(
                R.string.technical_as_of,
                result.sampleSize,
                result.asOf.format(DateTimeFormatter.ISO_LOCAL_DATE)
            ),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(
                R.string.technical_ma_values,
                result.movingAverage5?.let(NumberFormatter::won)
                    ?: stringResource(R.string.data_missing),
                NumberFormatter.won(result.movingAverage20),
                result.movingAverage60?.let(NumberFormatter::won)
                    ?: stringResource(R.string.data_missing)
            )
        )
        Text(
            text = stringResource(
                R.string.technical_rsi,
                NumberFormatter.number(result.rsi14),
                rsiStateText(result.rsiState)
            )
        )
        val macdText = result.macd?.let(NumberFormatter::won)
            ?: stringResource(R.string.data_missing)
        val signalText = result.macdSignal?.let(NumberFormatter::won)
            ?: stringResource(R.string.data_missing)
        val histogramText = result.macdHistogram?.let(NumberFormatter::won)
            ?: stringResource(R.string.data_missing)
        Text(
            text = stringResource(
                R.string.technical_macd,
                macdText,
                signalText,
                histogramText
            )
        )
        Text(
            text = stringResource(
                R.string.technical_bollinger,
                NumberFormatter.won(result.bollingerLower),
                NumberFormatter.won(result.bollingerUpper)
            )
        )
        Text(
            text = stringResource(
                R.string.technical_volatility,
                result.annualizedVolatilityPercent?.let(NumberFormatter::percentage)
                    ?: stringResource(R.string.data_missing)
            )
        )
        result.volumeRatio20?.let {
            Text(
                text = stringResource(
                    R.string.technical_volume_ratio,
                    NumberFormatter.multiple(it)
                )
            )
        }
        PriceZonesSection(
            titleResource = R.string.support_zone_title,
            zones = result.supportZones
        )
        PriceZonesSection(
            titleResource = R.string.resistance_zone_title,
            zones = result.resistanceZones
        )
        ObservationScenarios(result)
    }
}

@Composable
private fun PriceZonesSection(titleResource: Int, zones: List<PriceZone>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(titleResource),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (zones.isEmpty()) {
            Text(
                text = stringResource(R.string.price_zone_unavailable),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            zones.forEachIndexed { index, zone ->
                Text(
                    text = stringResource(
                        R.string.price_zone_row,
                        index + 1,
                        NumberFormatter.won(zone.lower),
                        NumberFormatter.won(zone.upper),
                        zone.touchCount
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ObservationScenarios(result: TechnicalAnalysisResult) {
    val firstResistance = result.resistanceZones.firstOrNull()
    val secondResistance = result.resistanceZones.getOrNull(1)
    val firstSupport = result.supportZones.firstOrNull()
    val secondSupport = result.supportZones.getOrNull(1)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = stringResource(R.string.observation_scenario_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (firstResistance != null) {
            Text(
                text = if (secondResistance != null) {
                    stringResource(
                        R.string.resistance_scenario_with_next,
                        NumberFormatter.won(firstResistance.lower),
                        NumberFormatter.won(firstResistance.upper),
                        NumberFormatter.won(secondResistance.lower),
                        NumberFormatter.won(secondResistance.upper)
                    )
                } else {
                    stringResource(
                        R.string.resistance_scenario,
                        NumberFormatter.won(firstResistance.lower),
                        NumberFormatter.won(firstResistance.upper)
                    )
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (firstSupport != null) {
            Text(
                text = if (secondSupport != null) {
                    stringResource(
                        R.string.support_scenario_with_next,
                        NumberFormatter.won(firstSupport.lower),
                        NumberFormatter.won(firstSupport.upper),
                        NumberFormatter.won(secondSupport.lower),
                        NumberFormatter.won(secondSupport.upper)
                    )
                } else {
                    stringResource(
                        R.string.support_scenario,
                        NumberFormatter.won(firstSupport.lower),
                        NumberFormatter.won(firstSupport.upper)
                    )
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (firstResistance == null && firstSupport == null) {
            Text(stringResource(R.string.observation_scenario_unavailable))
        }
    }
}

@Composable
private fun viewTitle(view: ComprehensiveView): String = when (view) {
    ComprehensiveView.POSITIVE -> stringResource(R.string.comprehensive_view_positive_title)
    ComprehensiveView.BALANCED -> stringResource(R.string.comprehensive_view_balanced_title)
    ComprehensiveView.CAUTIOUS -> stringResource(R.string.comprehensive_view_cautious_title)
}

@Composable
private fun viewDescription(view: ComprehensiveView): String = when (view) {
    ComprehensiveView.POSITIVE -> stringResource(R.string.comprehensive_view_positive)
    ComprehensiveView.BALANCED -> stringResource(R.string.comprehensive_view_balanced)
    ComprehensiveView.CAUTIOUS -> stringResource(R.string.comprehensive_view_cautious)
}

@Composable
private fun referenceMethodText(methods: Set<ReferenceMethod>): String = when (methods) {
    setOf(ReferenceMethod.PER, ReferenceMethod.SRIM) -> {
        stringResource(R.string.composite_method_both)
    }
    setOf(ReferenceMethod.PER) -> stringResource(R.string.composite_method_per)
    setOf(ReferenceMethod.SRIM) -> stringResource(R.string.composite_method_srim)
    else -> stringResource(R.string.composite_reference_unavailable)
}

@Composable
private fun technicalDirectionText(direction: TechnicalDirection): String = when (direction) {
    TechnicalDirection.POSITIVE -> stringResource(R.string.technical_direction_positive)
    TechnicalDirection.NEUTRAL -> stringResource(R.string.technical_direction_neutral)
    TechnicalDirection.NEGATIVE -> stringResource(R.string.technical_direction_negative)
}

@Composable
private fun rsiStateText(state: RsiState): String = when (state) {
    RsiState.OVERBOUGHT -> stringResource(R.string.rsi_overbought)
    RsiState.POSITIVE -> stringResource(R.string.rsi_positive)
    RsiState.NEUTRAL -> stringResource(R.string.rsi_neutral)
    RsiState.NEGATIVE -> stringResource(R.string.rsi_negative)
    RsiState.OVERSOLD -> stringResource(R.string.rsi_oversold)
}

private fun Int.toSignedText(): String = if (this > 0) "+$this" else toString()
