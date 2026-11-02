package io.github.kevin.visibleclone.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visible's signature element: a semicircular arc that fills as PacePoints are spent, with a white
 * PaceSetter dot marking where an evenly-paced day would put you right now.
 *
 * @param spent fraction of the daily budget already used, 0..1 (values above 1 are clamped for the
 *   arc but reported honestly by the caller's text).
 * @param paceSetter fraction of the day elapsed, 0..1.
 */
@Composable
fun PaceArc(
    spent: Float,
    paceSetter: Float,
    modifier: Modifier = Modifier,
    height: Int = 200,
    spentColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    center: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val stroke = size.minDimension * 0.13f
            val diameter = size.width * 0.82f
            val topLeft = Offset((size.width - diameter) / 2, size.height * 0.12f)
            val arcSize = Size(diameter, diameter)

            // 180° sweep starting from the left (180 deg) round to the right (360 deg).
            drawArc(
                color = trackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            drawArc(
                brush = Brush.horizontalGradient(listOf(spentColor.copy(alpha = 0.75f), spentColor)),
                startAngle = 180f,
                sweepAngle = 180f * spent.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            // PaceSetter: a white dot riding on the arc.
            val radius = diameter / 2f
            val cx = topLeft.x + radius
            val cy = topLeft.y + radius
            val angleDeg = 180f + 180f * paceSetter.coerceIn(0f, 1f)
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val dot = Offset(
                x = cx + (radius * cos(angleRad)).toFloat(),
                y = cy + (radius * sin(angleRad)).toFloat(),
            )
            drawCircle(Color.White, radius = stroke * 0.62f, center = dot)
            drawCircle(spentColor, radius = stroke * 0.30f, center = dot)
        }

        // Content sits slightly below the arc's centre so it visually nests inside the semicircle.
        Column(
            Modifier.padding(top = height.dp * 0.22f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { center() }
    }
}

/** A rounded card used for every surface, matching Visible's soft 24 dp corners. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = color),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

/** Heart-rate line drawn with coloured zone bands behind it. */
@Composable
fun ZoneHrLine(
    values: List<Int>,
    activityBpm: Int,
    exertionBpm: Int,
    modifier: Modifier = Modifier,
    restColor: Color,
    activityColor: Color,
    exertionColor: Color,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = (values.min() - 5).coerceAtLeast(40)
        val max = (values.max() + 5).coerceAtMost(220)
        val range = (max - min).coerceAtLeast(1)

        fun yFor(bpm: Int): Float = size.height - ((bpm - min).toFloat() / range) * size.height

        // Zone bands
        val actY = yFor(activityBpm)
        val excY = yFor(exertionBpm)
        drawRect(restColor.copy(alpha = 0.15f), topLeft = Offset(0f, actY), size = Size(size.width, size.height - actY))
        drawRect(activityColor.copy(alpha = 0.15f), topLeft = Offset(0f, excY), size = Size(size.width, actY - excY))
        drawRect(exertionColor.copy(alpha = 0.15f), topLeft = Offset(0f, 0f), size = Size(size.width, excY))

        val stepX = size.width / (values.size - 1)
        val path = Path().apply {
            moveTo(0f, yFor(values[0]))
            for (i in 1 until values.size) lineTo(i * stepX, yFor(values[i]))
        }
        drawPath(path, restColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
