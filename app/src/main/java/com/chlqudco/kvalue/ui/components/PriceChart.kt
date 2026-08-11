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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chlqudco.kvalue.R
import com.chlqudco.kvalue.common.NumberFormatter
import com.chlqudco.kvalue.domain.model.PricePoint

@Composable
fun PriceChart(
    points: List<PricePoint>,
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
                ChartContent(points)
            }
        }
    }
}

@Composable
private fun ChartContent(points: List<PricePoint>) {
    val minimum = points.minOf { it.close }
    val maximum = points.maxOf { it.close }
    val change = (points.last().close.toDouble() / points.first().close - 1.0) * 100.0
    val changeText = NumberFormatter.signedPercentage(change)
    val description = stringResource(
        R.string.chart_content_description,
        points.size,
        NumberFormatter.won(minimum),
        NumberFormatter.won(maximum),
        changeText
    )
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description }
    ) {
        val priceRange = (maximum - minimum).coerceAtLeast(1L).toFloat()
        val xStep = size.width / points.lastIndex
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = xStep * index
            val normalized = (point.close - minimum).toFloat() / priceRange
            val y = size.height - normalized * size.height
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
