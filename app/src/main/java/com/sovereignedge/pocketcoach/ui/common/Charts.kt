package com.sovereignedge.pocketcoach.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Hand-drawn line chart: values in order, with the min/max labelled. No dependencies. */
@Composable
fun LineChart(values: List<Double>, labelStart: String, labelEnd: String, formatValue: (Double) -> String, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text(formatValue(max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(height).padding(vertical = 4.dp)) {
            val w = size.width
            val h = size.height
            drawLine(grid, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
            drawLine(grid, Offset(0f, h), Offset(w, h), strokeWidth = 1f)
            if (values.size < 2) {
                if (values.size == 1) drawCircle(color, 6f, Offset(w / 2, h / 2))
                return@Canvas
            }
            val range = (max - min).takeIf { it > 0 } ?: 1.0
            val pts = values.mapIndexed { i, v ->
                Offset(w * i / (values.size - 1), (h - h * ((v - min) / range)).toFloat().coerceIn(4f, h - 4f))
            }
            val path = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
            pts.forEach { drawCircle(color, 7f, it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text(labelStart, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatValue(min), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(labelEnd, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Bars for one value per period; the tallest is labelled. */
@Composable
fun BarChart(values: List<Double>, labels: List<String>, formatValue: (Double) -> String, modifier: Modifier = Modifier, height: Dp = 140.dp) {
    val color = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.surfaceVariant
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        Text(formatValue(max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(height).padding(vertical = 4.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.6f
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - barW) / 2
                drawRoundRect(faint, Offset(x, 0f), androidx.compose.ui.geometry.Size(barW, size.height), androidx.compose.ui.geometry.CornerRadius(6f))
                val bh = (size.height * (v / max)).toFloat()
                if (bh > 0) drawRoundRect(color, Offset(x, size.height - bh), androidx.compose.ui.geometry.Size(barW, bh), androidx.compose.ui.geometry.CornerRadius(6f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
