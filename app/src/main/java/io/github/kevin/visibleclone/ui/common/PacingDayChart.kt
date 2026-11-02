package io.github.kevin.visibleclone.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Cumulative PacePoints across the day, with hour ticks and a scrubbable info line.
 *
 * [cumulative] holds one value per 15-minute slot (96 entries), starting at local midnight.
 * Values are cumulative, so a flat stretch means resting and a steep stretch means exertion —
 * the same reading Visible's pacing-day chart gives.
 */
@Composable
fun PacingDayChart(
    cumulative: List<Float>,
    budget: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    guideColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    nowSlot: Int? = null,
) {
    var scrubSlot by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        val maxValue = maxOf(budget, cumulative.maxOrNull() ?: 1f).coerceAtLeast(1f)

        Canvas(
            Modifier.fillMaxWidth().height(150.dp)
                .pointerInput(cumulative) {
                    detectTapGestures { offset ->
                        scrubSlot = slotFor(offset.x, size.width, cumulative.size)
                    }
                }
                .pointerInput(cumulative) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> scrubSlot = slotFor(offset.x, size.width, cumulative.size) },
                        onDragEnd = { scrubSlot = null },
                        onDragCancel = { scrubSlot = null },
                    ) { change, _ ->
                        scrubSlot = slotFor(change.position.x, size.width, cumulative.size)
                    }
                },
        ) {
            if (cumulative.size < 2) return@Canvas

            val leftPad = 4.dp.toPx()
            val bottomPad = 18.dp.toPx()
            val topPad = 8.dp.toPx()
            val usableW = size.width - leftPad * 2
            val usableH = size.height - bottomPad - topPad

            fun xFor(i: Int) = leftPad + usableW * i / (cumulative.size - 1).toFloat()
            fun yFor(v: Float) = topPad + usableH * (1f - (v / maxValue).coerceIn(0f, 1f))

            // Budget line
            drawLine(
                color = guideColor.copy(alpha = 0.35f),
                start = Offset(leftPad, yFor(budget)),
                end = Offset(size.width - leftPad, yFor(budget)),
                strokeWidth = 1.dp.toPx(),
            )

            // Hour ticks every 6 hours
            listOf(0, 6, 12, 18, 23).forEach { hour ->
                val idx = (hour * 4).coerceAtMost(cumulative.size - 1)
                val x = xFor(idx)
                drawLine(
                    color = guideColor.copy(alpha = 0.18f),
                    start = Offset(x, topPad),
                    end = Offset(x, size.height - bottomPad),
                    strokeWidth = 1.dp.toPx(),
                )
                val label = when (hour) {
                    0 -> "12a"
                    6 -> "6a"
                    12 -> "12p"
                    18 -> "6p"
                    else -> "11p"
                }
                val layout = textMeasurer.measure(label, labelStyle)
                drawText(
                    layout,
                    color = labelColor,
                    topLeft = Offset(
                        (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width),
                        size.height - bottomPad + 3.dp.toPx(),
                    ),
                )
            }

            // Cumulative line, with area fill
            val path = Path()
            cumulative.forEachIndexed { i, v ->
                if (i == 0) path.moveTo(xFor(i), yFor(v)) else path.lineTo(xFor(i), yFor(v))
            }
            val area = Path().apply {
                addPath(path)
                lineTo(xFor(cumulative.size - 1), size.height - bottomPad)
                lineTo(xFor(0), size.height - bottomPad)
                close()
            }
            drawPath(area, lineColor.copy(alpha = 0.14f))
            drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

            // "Now" marker
            nowSlot?.let { slot ->
                val idx = slot.coerceIn(0, cumulative.size - 1)
                drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(xFor(idx), yFor(cumulative[idx])))
            }

            // Scrub line
            scrubSlot?.let { slot ->
                val idx = slot.coerceIn(0, cumulative.size - 1)
                val x = xFor(idx)
                drawLine(
                    color = guideColor,
                    start = Offset(x, topPad),
                    end = Offset(x, size.height - bottomPad),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(x, yFor(cumulative[idx])))
            }
        }

        val slot = scrubSlot
        if (slot != null && slot in cumulative.indices) {
            val time = formatSlotTime(slot * 15)
            Text(
                "$time · %.1f PacePoints".format(cumulative[slot]),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                "Drag across the chart to read any time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun slotFor(x: Float, width: Int, slots: Int): Int {
    if (width <= 0 || slots <= 0) return 0
    val fraction = (x / width).coerceIn(0f, 1f)
    return (fraction * (slots - 1)).roundToInt()
}

/**
 * Scrubbable line for any series with a label per point (e.g. one date per day).
 * Shows a vertical info line and the value under the finger.
 */
@Composable
fun ScrubSeries(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    valueFormat: (Float) -> String = { "%.1f".format(it) },
    height: Int = 120,
) {
    var scrub by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (values.size < 2) return

    Column(modifier) {
        val maxValue = (values.maxOrNull() ?: 1f).coerceAtLeast(0.1f)
        val minValue = minOf(0f, values.minOrNull() ?: 0f)
        val range = (maxValue - minValue).coerceAtLeast(0.1f)

        Canvas(
            Modifier.fillMaxWidth().height(height.dp)
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        scrub = slotFor(offset.x, size.width, values.size)
                    }
                }
                .pointerInput(values) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> scrub = slotFor(o.x, size.width, values.size) },
                        onDragEnd = { scrub = null },
                        onDragCancel = { scrub = null },
                    ) { change, _ -> scrub = slotFor(change.position.x, size.width, values.size) }
                },
        ) {
            val leftPad = 4.dp.toPx()
            val bottomPad = 18.dp.toPx()
            val topPad = 6.dp.toPx()
            val usableW = size.width - leftPad * 2
            val usableH = size.height - bottomPad - topPad
            fun xFor(i: Int) = leftPad + usableW * i / (values.size - 1).toFloat()
            fun yFor(v: Float) = topPad + usableH * (1f - ((v - minValue) / range).coerceIn(0f, 1f))

            // First and last labels only, to stay legible.
            listOf(0, values.size - 1).forEach { i ->
                val layout = textMeasurer.measure(labels.getOrElse(i) { "" }, labelStyle)
                drawText(
                    layout,
                    color = labelColor,
                    topLeft = Offset(
                        (xFor(i) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width),
                        size.height - bottomPad + 3.dp.toPx(),
                    ),
                )
            }

            val path = Path()
            values.forEachIndexed { i, v ->
                if (i == 0) path.moveTo(xFor(i), yFor(v)) else path.lineTo(xFor(i), yFor(v))
            }
            val area = Path().apply {
                addPath(path)
                lineTo(xFor(values.size - 1), size.height - bottomPad)
                lineTo(xFor(0), size.height - bottomPad)
                close()
            }
            drawPath(area, lineColor.copy(alpha = 0.14f))
            drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

            scrub?.let { raw ->
                val i = raw.coerceIn(0, values.size - 1)
                drawLine(
                    color = guideColor,
                    start = Offset(xFor(i), topPad),
                    end = Offset(xFor(i), size.height - bottomPad),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(xFor(i), yFor(values[i])))
            }
        }

        val i = scrub
        if (i != null && i in values.indices) {
            Text(
                "${labels.getOrElse(i) { "" }} · ${valueFormat(values[i])}",
                style = MaterialTheme.typography.labelMedium,
            )
        } else {
            Text(
                "Drag to scrub",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


/** "13:30" slot minutes -> "1:30pm", matching the chart's tick style. */
internal fun formatSlotTime(minutesOfDay: Int): String {
    val hour24 = (minutesOfDay / 60) % 24
    val mins = minutesOfDay % 60
    val suffix = if (hour24 < 12) "am" else "pm"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return "$hour12:" + "%02d".format(mins) + suffix
}
