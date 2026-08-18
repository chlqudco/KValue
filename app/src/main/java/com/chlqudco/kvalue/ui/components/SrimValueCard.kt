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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chlqudco.kvalue.R
import com.chlqudco.kvalue.common.NumberFormatter
import com.chlqudco.kvalue.domain.model.SrimValueResult
import com.chlqudco.kvalue.domain.model.StockAnalysis
import com.chlqudco.kvalue.ui.SrimInputError
import com.chlqudco.kvalue.ui.SrimInputField
import com.chlqudco.kvalue.ui.SrimInputFields
import kotlin.math.abs

@Composable
fun SrimValueCard(
    analysis: StockAnalysis,
    inputs: SrimInputFields,
    result: SrimValueResult?,
    inputError: SrimInputError?,
    onInputChanged: (SrimInputField, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bps = analysis.ratios.bps?.takeIf { it.isFinite() && it > 0.0 }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("srim_value_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.srim_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.srim_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = bps?.let {
                    stringResource(R.string.srim_bps_value, NumberFormatter.won(it))
                } ?: stringResource(R.string.srim_bps_missing),
                style = MaterialTheme.typography.labelLarge
            )
            SrimFields(
                inputs = inputs,
                enabled = bps != null,
                inputError = inputError,
                onInputChanged = onInputChanged
            )
            Text(
                text = stringResource(R.string.srim_required_return_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            inputError?.let {
                Text(
                    text = when (it) {
                        SrimInputError.INVALID_ROE -> stringResource(R.string.srim_error_roe)
                        SrimInputError.INVALID_REQUIRED_RETURN -> {
                            stringResource(R.string.srim_error_required_return)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (result == null) {
                Text(
                    text = when {
                        bps == null -> stringResource(R.string.srim_unavailable_bps)
                        inputs.returnOnEquity.isBlank() -> {
                            stringResource(R.string.srim_unavailable_roe)
                        }
                        else -> stringResource(R.string.srim_unavailable_input)
                    },
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                SrimResults(result)
            }
            analysis.ratios.reportingPeriod?.let {
                Text(
                    text = stringResource(R.string.srim_financial_period, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = stringResource(R.string.srim_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SrimFields(
    inputs: SrimInputFields,
    enabled: Boolean,
    inputError: SrimInputError?,
    onInputChanged: (SrimInputField, String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemWidth = if (maxWidth >= 280.dp) {
            (maxWidth - 12.dp) / 2
        } else {
            maxWidth
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2
        ) {
            SrimField(
                label = stringResource(R.string.srim_roe_label),
                value = inputs.returnOnEquity,
                enabled = enabled,
                isError = inputError == SrimInputError.INVALID_ROE,
                onValueChange = {
                    onInputChanged(SrimInputField.RETURN_ON_EQUITY, it)
                },
                testTag = "srim_roe_input",
                modifier = Modifier.width(itemWidth)
            )
            SrimField(
                label = stringResource(R.string.srim_required_return_label),
                value = inputs.requiredReturn,
                enabled = enabled,
                isError = inputError == SrimInputError.INVALID_REQUIRED_RETURN,
                onValueChange = {
                    onInputChanged(SrimInputField.REQUIRED_RETURN, it)
                },
                testTag = "srim_required_return_input",
                modifier = Modifier.width(itemWidth)
            )
        }
    }
}

@Composable
private fun SrimField(
    label: String,
    value: String,
    enabled: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag(testTag),
        enabled = enabled,
        isError = isError,
        singleLine = true,
        label = { Text(label) },
        suffix = { Text(stringResource(R.string.percent_unit)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SrimResults(result: SrimValueResult) {
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
            SrimScenarioValue(
                label = stringResource(R.string.srim_fast_fade),
                value = result.fastFade,
                modifier = Modifier.width(itemWidth)
            )
            SrimScenarioValue(
                label = stringResource(R.string.srim_gradual_fade),
                value = result.gradualFade,
                modifier = Modifier.width(itemWidth)
            )
            SrimScenarioValue(
                label = stringResource(R.string.srim_persistent),
                value = result.persistent,
                modifier = Modifier.width(itemWidth)
            )
        }
    }
    Text(
        text = stringResource(
            R.string.srim_excess_return,
            NumberFormatter.signedPercentage(result.excessReturnPercent)
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer
    )
    Text(
        text = when {
            result.gradualGapPercent > 0.0 -> stringResource(
                R.string.srim_gap_lower,
                NumberFormatter.percentage(abs(result.gradualGapPercent))
            )
            result.gradualGapPercent < 0.0 -> stringResource(
                R.string.srim_gap_higher,
                NumberFormatter.percentage(abs(result.gradualGapPercent))
            )
            else -> stringResource(R.string.srim_gap_equal)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

@Composable
private fun SrimScenarioValue(
    label: String,
    value: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = NumberFormatter.won(value),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
