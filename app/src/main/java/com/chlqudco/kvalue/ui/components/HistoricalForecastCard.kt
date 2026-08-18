package com.chlqudco.kvalue.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
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
import com.chlqudco.kvalue.domain.model.DirectionForecast
import com.chlqudco.kvalue.domain.model.ForecastConfidence
import com.chlqudco.kvalue.domain.model.ForecastDirection
import com.chlqudco.kvalue.domain.model.ForecastUnavailableReason
import com.chlqudco.kvalue.domain.model.ForecastValidation
import com.chlqudco.kvalue.domain.model.HistoricalForecast
import com.chlqudco.kvalue.domain.model.HistoricalForecastResult

@Composable
fun HistoricalForecastCard(result: HistoricalForecastResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("historical_forecast_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.forecast_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.forecast_experimental),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = stringResource(R.string.forecast_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (result) {
                is HistoricalForecastResult.Available -> AvailableForecast(result.forecast)
                is HistoricalForecastResult.Unavailable -> UnavailableForecast(result)
            }
            Text(
                text = stringResource(R.string.forecast_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AvailableForecast(forecast: HistoricalForecast) {
    Text(
        text = stringResource(
            R.string.forecast_range_available,
            forecast.horizonTradingDays
        ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    ForecastMetric(
        label = stringResource(R.string.forecast_price_range),
        value = stringResource(
            R.string.forecast_range_value,
            NumberFormatter.won(forecast.lowerPrice),
            NumberFormatter.won(forecast.upperPrice)
        ),
        modifier = Modifier.testTag("forecast_price_range")
    )
    ForecastMetric(
        label = stringResource(R.string.forecast_return_range),
        value = stringResource(
            R.string.forecast_range_value,
            NumberFormatter.signedPercentage(forecast.lowerReturnPercent),
            NumberFormatter.signedPercentage(forecast.upperReturnPercent)
        )
    )
    ForecastMetric(
        label = stringResource(R.string.forecast_range_confidence),
        value = when (forecast.confidence) {
            ForecastConfidence.LIMITED -> stringResource(R.string.forecast_confidence_limited)
            ForecastConfidence.MODERATE -> stringResource(R.string.forecast_confidence_moderate)
        }
    )
    forecast.direction?.let {
        DirectionDetails(it, forecast.horizonTradingDays)
    } ?: UnverifiedDirection()
    Text(
        text = stringResource(
            R.string.forecast_history_summary,
            forecast.observationCount,
            forecast.historyStartDate.toString(),
            forecast.historyEndDate.toString(),
            forecast.rangeSampleCount
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ValidationDetails(forecast.validation)
}

@Composable
private fun DirectionDetails(direction: DirectionForecast, horizonTradingDays: Int) {
    Text(
        text = stringResource(R.string.forecast_direction_validated),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("forecast_direction_status")
    )
    ForecastMetric(
        label = stringResource(R.string.forecast_direction_label),
        value = when (direction.direction) {
            ForecastDirection.UPWARD -> stringResource(R.string.forecast_direction_upward)
            ForecastDirection.NEUTRAL -> stringResource(R.string.forecast_direction_neutral)
            ForecastDirection.DOWNWARD -> stringResource(R.string.forecast_direction_downward)
        }
    )
    ForecastMetric(
        label = stringResource(
            R.string.forecast_direction_probabilities,
            horizonTradingDays
        ),
        value = stringResource(
            R.string.forecast_probability_value,
            NumberFormatter.percentage(direction.upwardProbabilityPercent),
            NumberFormatter.percentage(direction.neutralProbabilityPercent),
            NumberFormatter.percentage(direction.downwardProbabilityPercent)
        )
    )
    ForecastMetric(
        label = stringResource(R.string.forecast_direction_median_return),
        value = NumberFormatter.signedPercentage(direction.medianReturnPercent)
    )
    ForecastMetric(
        label = stringResource(R.string.forecast_direction_median_price),
        value = NumberFormatter.won(direction.medianPrice)
    )
    Text(
        text = stringResource(
            R.string.forecast_direction_analog_count,
            direction.analogCount
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun UnverifiedDirection() {
    Text(
        text = stringResource(R.string.forecast_direction_unverified_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.testTag("forecast_direction_status")
    )
    Text(
        text = stringResource(R.string.forecast_direction_unverified),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun UnavailableForecast(result: HistoricalForecastResult.Unavailable) {
    val title = when (result.reason) {
        ForecastUnavailableReason.INSUFFICIENT_HISTORY -> {
            stringResource(R.string.forecast_unavailable_insufficient_title)
        }
        ForecastUnavailableReason.INVALID_PRICE_HISTORY -> {
            stringResource(R.string.forecast_unavailable_invalid_title)
        }
    }
    val description = when (result.reason) {
        ForecastUnavailableReason.INSUFFICIENT_HISTORY -> stringResource(
            R.string.forecast_unavailable_insufficient,
            result.observationCount,
            result.requiredObservationCount
        )
        ForecastUnavailableReason.INVALID_PRICE_HISTORY -> {
            stringResource(R.string.forecast_unavailable_invalid)
        }
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.error
    )
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ValidationDetails(validation: ForecastValidation) {
    Text(
        text = stringResource(R.string.forecast_interval_validation_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = if (validation.intervalCoveragePercent != null) {
            stringResource(
                R.string.forecast_interval_validation,
                validation.intervalPredictionCount,
                NumberFormatter.percentage(validation.targetIntervalCoveragePercent),
                NumberFormatter.percentage(validation.intervalCoveragePercent)
            )
        } else {
            stringResource(R.string.forecast_validation_not_available)
        },
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        text = stringResource(R.string.forecast_direction_validation_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    if (
        validation.directionPredictionCount > 0 &&
        validation.directionAccuracyPercent != null &&
        validation.modelBrierScore != null &&
        validation.baselineBrierScore != null
    ) {
        Text(
            text = stringResource(
                R.string.forecast_validation_direction,
                validation.directionPredictionCount,
                NumberFormatter.percentage(validation.directionAccuracyPercent)
            ),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(
                R.string.forecast_validation_brier,
                NumberFormatter.number(validation.modelBrierScore),
                NumberFormatter.number(validation.baselineBrierScore)
            ),
            style = MaterialTheme.typography.bodySmall
        )
        validation.brierSkillPercent?.let { skill ->
            Text(
                text = stringResource(
                    R.string.forecast_validation_skill,
                    NumberFormatter.signedPercentage(skill)
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        Text(
            text = stringResource(R.string.forecast_direction_validation_unavailable),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ForecastMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
