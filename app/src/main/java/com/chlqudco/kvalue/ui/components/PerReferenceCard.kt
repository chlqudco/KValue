package com.chlqudco.kvalue.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chlqudco.kvalue.R
import com.chlqudco.kvalue.common.NumberFormatter
import com.chlqudco.kvalue.domain.model.PerReferenceBand
import com.chlqudco.kvalue.domain.model.PerReferenceResult
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.ui.PerInputError
import com.chlqudco.kvalue.ui.PerInputFields
import com.chlqudco.kvalue.ui.PerScenario
import kotlin.math.abs

@Composable
fun PerReferenceCard(
    analysis: StockAnalysis,
    inputs: PerInputFields,
    result: PerReferenceResult?,
    inputError: PerInputError?,
    onPerChanged: (PerScenario, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val calculationEnabled = analysis.ratios.eps?.let { it > 0.0 } == true
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.per_reference_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.per_assumptions),
                style = MaterialTheme.typography.labelLarge
            )
            PerFields(
                inputs = inputs,
                enabled = calculationEnabled,
                hasError = inputError != null,
                onPerChanged = onPerChanged
            )
            inputError?.let {
                Text(
                    text = when (it) {
                        PerInputError.INVALID_VALUE -> stringResource(R.string.per_error_value)
                        PerInputError.INVALID_ORDER -> stringResource(R.string.per_error_order)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (result == null) {
                Text(
                    text = if (analysis.ratios.eps == null) {
                        stringResource(R.string.per_reference_unavailable_missing)
                    } else {
                        stringResource(R.string.per_reference_unavailable_non_positive)
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                PerReferenceResults(result)
            }
            Text(
                text = stringResource(R.string.per_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PerFields(
    inputs: PerInputFields,
    enabled: Boolean,
    hasError: Boolean,
    onPerChanged: (PerScenario, String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemWidth = if (maxWidth >= 320.dp) {
            (maxWidth - 24.dp) / 3
        } else {
            maxWidth
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 3
        ) {
            PerField(
                label = stringResource(R.string.scenario_conservative),
                value = inputs.conservative,
                enabled = enabled,
                isError = hasError,
                onValueChange = { onPerChanged(PerScenario.CONSERVATIVE, it) },
                modifier = Modifier.width(itemWidth)
            )
            PerField(
                label = stringResource(R.string.scenario_base),
                value = inputs.base,
                enabled = enabled,
                isError = hasError,
                onValueChange = { onPerChanged(PerScenario.BASE, it) },
                modifier = Modifier.width(itemWidth)
            )
            PerField(
                label = stringResource(R.string.scenario_optimistic),
                value = inputs.optimistic,
                enabled = enabled,
                isError = hasError,
                onValueChange = { onPerChanged(PerScenario.OPTIMISTIC, it) },
                modifier = Modifier.width(itemWidth)
            )
        }
    }
}

@Composable
private fun PerField(
    label: String,
    value: String,
    enabled: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        singleLine = true,
        label = { Text(stringResource(R.string.per_field_label, label)) },
        suffix = { Text(stringResource(R.string.per_unit)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PerReferenceResults(result: PerReferenceResult) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemWidth = if (maxWidth >= 320.dp) {
            (maxWidth - 24.dp) / 3
        } else {
            maxWidth
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 3
        ) {
            ScenarioValue(
                stringResource(R.string.scenario_conservative),
                result.conservative,
                Modifier.width(itemWidth)
            )
            ScenarioValue(
                stringResource(R.string.scenario_base),
                result.base,
                Modifier.width(itemWidth)
            )
            ScenarioValue(
                stringResource(R.string.scenario_optimistic),
                result.optimistic,
                Modifier.width(itemWidth)
            )
        }
    }
    Text(
        text = when {
            result.baseGapPercent > 0.0 -> stringResource(
                R.string.per_reference_gap_lower,
                NumberFormatter.percentage(abs(result.baseGapPercent))
            )
            result.baseGapPercent < 0.0 -> stringResource(
                R.string.per_reference_gap_higher,
                NumberFormatter.percentage(abs(result.baseGapPercent))
            )
            else -> stringResource(R.string.per_reference_gap_equal)
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
    Text(
        text = when (result.band) {
            PerReferenceBand.BELOW_CONSERVATIVE -> stringResource(R.string.band_below_conservative)
            PerReferenceBand.BETWEEN_CONSERVATIVE_AND_BASE ->
                stringResource(R.string.band_conservative_to_base)
            PerReferenceBand.BETWEEN_BASE_AND_OPTIMISTIC ->
                stringResource(R.string.band_base_to_optimistic)
            PerReferenceBand.ABOVE_OPTIMISTIC -> stringResource(R.string.band_above_optimistic)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
}

@Composable
private fun ScenarioValue(
    label: String,
    value: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = NumberFormatter.won(value),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
