/*
 * 최대 100거래일 종가와 계산된 지지·저항선을 Canvas 좌표로 변환해 차트를 그리는 Composable이다.
 * 최저가와 최고가가 같을 때도 0으로 나누지 않도록 가격 범위를 보정하고 좌우 위치를 표본 인덱스로 계산한다.
 * 시각 정보만으로 끝나지 않게 최저·최고가, 기간 변화율과 지지·저항 가격을 텍스트와 contentDescription으로 제공한다.
 * 데이터가 2개 미만이면 임의의 선을 만들지 않고 명시적인 빈 상태 카드를 표시한다.
 */
package com.chlqudco.kvalue.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chlqudco.kvalue.R
import com.chlqudco.kvalue.common.NumberFormatter
import com.chlqudco.kvalue.domain.model.PriceLevel
import com.chlqudco.kvalue.domain.model.PricePoint
import com.chlqudco.kvalue.domain.model.SupportResistanceResult
import com.chlqudco.kvalue.domain.model.SupportResistanceUnavailableReason

// 카드 진입점에서 데이터 개수를 검사하고 2개 미만이면 계산 없이 빈 상태를 선택한다.
@Composable
fun PriceChart(
    points: List<PricePoint>,
    supportResistance: SupportResistanceResult,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.chart_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (points.size < 2) {
                Text(
                    text = stringResource(R.string.chart_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ChartContent(points, supportResistance)
            }
            SupportResistanceSection(supportResistance)
        }
    }
}

/*
 * 종가와 지지·저항선을 Canvas 높이에 맞춰 정규화하고 인덱스를 가로 좌표로 바꿔 Path를 만든다.
 * 같은 데이터에서 최저·최고·기간 변화와 가격선의 접근성 설명도 함께 계산한다.
 */
@Composable
private fun ChartContent(
    points: List<PricePoint>,
    supportResistance: SupportResistanceResult
) {
    val minimum = points.minOf { it.close }
    val maximum = points.maxOf { it.close }
    val change = (points.last().close.toDouble() / points.first().close - 1.0) * 100.0
    val changeText = NumberFormatter.signedPercentage(change)
    val indicator = (supportResistance as? SupportResistanceResult.Available)?.indicator
    val supportLevels = indicator?.supportLevels.orEmpty()
    val resistanceLevels = indicator?.resistanceLevels.orEmpty()
    val noLevelText = stringResource(R.string.support_resistance_none_short)
    val supportDescription = supportLevels
        .joinToString { NumberFormatter.won(it.price) }
        .ifEmpty { noLevelText }
    val resistanceDescription = resistanceLevels
        .joinToString { NumberFormatter.won(it.price) }
        .ifEmpty { noLevelText }
    val description = stringResource(
        R.string.chart_content_description_with_levels,
        points.size,
        NumberFormatter.won(minimum),
        NumberFormatter.won(maximum),
        changeText,
        supportDescription,
        resistanceDescription
    )
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val supportColor = MaterialTheme.colorScheme.tertiary
    val resistanceColor = MaterialTheme.colorScheme.error
    val levelPrices = (supportLevels + resistanceLevels).map(PriceLevel::price)
    val chartMinimum = minOf(minimum, levelPrices.minOrNull() ?: minimum)
    val chartMaximum = maxOf(maximum, levelPrices.maxOrNull() ?: maximum)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .testTag("price_chart")
            .semantics { contentDescription = description }
    ) {
        val priceRange = (chartMaximum - chartMinimum).coerceAtLeast(1L).toFloat()
        val xStep = size.width / points.lastIndex
        fun yFor(price: Long): Float {
            val normalized = (price - chartMinimum).toFloat() / priceRange
            return size.height - normalized * size.height
        }
        val dashEffect = PathEffect.dashPathEffect(
            floatArrayOf(8.dp.toPx(), 6.dp.toPx())
        )
        supportLevels.forEachIndexed { index, level ->
            drawLevel(
                y = yFor(level.price),
                color = supportColor.copy(alpha = if (index == 0) 0.9f else 0.6f),
                dashEffect = dashEffect
            )
        }
        resistanceLevels.forEachIndexed { index, level ->
            drawLevel(
                y = yFor(level.price),
                color = resistanceColor.copy(alpha = if (index == 0) 0.9f else 0.6f),
                dashEffect = dashEffect
            )
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = xStep * index
            val y = yFor(point.close)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(
            color = guideColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.chart_low, NumberFormatter.won(minimum)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.chart_high, NumberFormatter.won(maximum)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        text = stringResource(R.string.chart_change, changeText),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLevel(
    y: Float,
    color: Color,
    dashEffect: PathEffect
) {
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 2.dp.toPx(),
        pathEffect = dashEffect
    )
}

@Composable
private fun SupportResistanceSection(result: SupportResistanceResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_resistance_indicator"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.support_resistance_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        when (result) {
            is SupportResistanceResult.Available -> {
                LevelGroup(
                    label = stringResource(R.string.support_label),
                    levels = result.indicator.supportLevels,
                    referencePrice = result.indicator.referencePrice,
                    color = MaterialTheme.colorScheme.tertiary,
                    tagPrefix = "support_level"
                )
                LevelGroup(
                    label = stringResource(R.string.resistance_label),
                    levels = result.indicator.resistanceLevels,
                    referencePrice = result.indicator.referencePrice,
                    color = MaterialTheme.colorScheme.error,
                    tagPrefix = "resistance_level"
                )
                Text(
                    text = stringResource(
                        R.string.support_resistance_basis,
                        result.indicator.sampleSize,
                        result.indicator.asOf.toString(),
                        result.indicator.pivotWindowSize,
                        NumberFormatter.percentage(
                            result.indicator.clusterTolerancePercent
                        ),
                        NumberFormatter.percentage(
                            result.indicator.minimumLevelDistancePercent
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is SupportResistanceResult.Unavailable -> {
                Text(
                    text = when (result.reason) {
                        SupportResistanceUnavailableReason.INSUFFICIENT_HISTORY -> {
                            stringResource(
                                R.string.support_resistance_insufficient,
                                result.observationCount,
                                result.requiredObservationCount
                            )
                        }
                        SupportResistanceUnavailableReason.INVALID_CURRENT_PRICE -> {
                            stringResource(R.string.support_resistance_invalid_price)
                        }
                        SupportResistanceUnavailableReason.NO_DISTINCT_LEVELS -> {
                            stringResource(R.string.support_resistance_no_distinct_levels)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = stringResource(R.string.support_resistance_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LevelGroup(
    label: String,
    levels: List<PriceLevel>,
    referencePrice: Long,
    color: Color,
    tagPrefix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (levels.isEmpty()) {
            Text(
                text = stringResource(R.string.support_resistance_side_empty, label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            levels.forEachIndexed { index, level ->
                val distancePercent =
                    (level.price.toDouble() / referencePrice - 1.0) * 100.0
                Text(
                    text = stringResource(
                        R.string.support_resistance_level_row,
                        index + 1,
                        label,
                        NumberFormatter.won(level.price),
                        NumberFormatter.signedPercentage(distancePercent),
                        level.touchCount
                    ),
                    modifier = Modifier.testTag("${tagPrefix}_${index + 1}"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
