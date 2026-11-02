package io.github.kevin.visibleclone.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Progress ring with the caller's content centred inside it. */
@Composable
fun ScoreRing(
    fraction: Float,
    diameter: Int,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter.dp)) {
            val stroke = size.minDimension * 0.08f
            val inset = stroke / 2
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** Simple sparkline for a series; draws nothing when there is no data. */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val pad = 3.dp.toPx()
        val usable = size.height - pad * 2
        val stepX = size.width / (values.size - 1)
        fun point(i: Int) = Offset(
            x = i * stepX,
            y = size.height - pad - ((values[i] - min) / range).toFloat() * usable,
        )
        val path = Path().apply {
            val p0 = point(0)
            moveTo(p0.x, p0.y)
            for (i in 1 until values.size) { val p = point(i); lineTo(p.x, p.y) }
        }
        if (fill) {
            val area = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, color.copy(alpha = 0.12f))
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
fun VGap(h: Int) = Spacer(Modifier.height(h.dp))
