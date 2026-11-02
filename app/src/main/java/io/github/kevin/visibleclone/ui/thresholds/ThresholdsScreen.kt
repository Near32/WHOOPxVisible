package io.github.kevin.visibleclone.ui.thresholds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kevin.visibleclone.ui.DashboardState
import io.github.kevin.visibleclone.ui.common.ScrubSeries
import io.github.kevin.visibleclone.ui.common.SoftCard
import io.github.kevin.visibleclone.ui.common.VGap
import io.github.kevin.visibleclone.ui.theme.Visible

/**
 * How the Rest / Activity / Exertion boundaries move over time.
 *
 * They are derived from resting HR (and max HR), so they drift as your physiology changes. Visible
 * personalises zones the same way and recommends adjusting zones before touching the budget.
 */
@Composable
fun ThresholdsScreen(s: DashboardState, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val days = s.thresholdDays
    val window = days.takeLast(30)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Heart-rate zones", style = MaterialTheme.typography.headlineSmall)
        }

        SoftCard {
            Text("Today", style = MaterialTheme.typography.labelLarge)
            VGap(8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ZoneBlock("Rest", "below ${s.activityThresholdBpm}", Visible.rest)
                ZoneBlock("Activity", "${s.activityThresholdBpm}–${s.exertionThresholdBpm}", Visible.activity)
                ZoneBlock("Exertion", "${s.exertionThresholdBpm}+", Visible.exertion)
            }
            VGap(10)
            Text(
                "Derived from resting HR ${s.rhr ?: "—"} and max HR ${s.maxHeartRate} bpm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VGap(6)
            androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                Text("Adjust zones in Settings")
            }
        }

        if (window.size >= 2) {
            SoftCard {
                Text("Activity threshold over time", style = MaterialTheme.typography.titleMedium)
                VGap(2)
                Text(
                    "Moves with your resting HR — ${window.first().activityBpm} → ${window.last().activityBpm} bpm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VGap(8)
                ScrubSeries(
                    values = window.map { it.activityBpm.toFloat() },
                    labels = window.map { it.date.takeLast(5) },
                    lineColor = Visible.activity,
                    valueFormat = { "%.0f bpm".format(it) },
                )
                VGap(14)
                Text("Exertion threshold over time", style = MaterialTheme.typography.titleMedium)
                VGap(2)
                Text(
                    "${window.first().exertionBpm} → ${window.last().exertionBpm} bpm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VGap(8)
                ScrubSeries(
                    values = window.map { it.exertionBpm.toFloat() },
                    labels = window.map { it.date.takeLast(5) },
                    lineColor = Visible.exertion,
                    valueFormat = { "%.0f bpm".format(it) },
                )
            }

            SoftCard {
                Text("Resting HR", style = MaterialTheme.typography.titleMedium)
                VGap(8)
                ScrubSeries(
                    values = window.map { it.restingHr.toFloat() },
                    labels = window.map { it.date.takeLast(5) },
                    lineColor = Visible.mintDeep,
                    valueFormat = { "%.0f bpm".format(it) },
                )
            }
        } else {
            SoftCard {
                Text(
                    "Threshold history needs a few nights of recovery data.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SoftCard(color = MaterialTheme.colorScheme.surfaceVariant) {
            Text("Why they move", style = MaterialTheme.typography.titleSmall)
            VGap(4)
            Text(
                "Both boundaries are fractions of your heart-rate reserve, so a higher resting HR " +
                    "raises them. Exercising capacity raises max HR and widens the gap. If you would " +
                    "rather fix them at specific bpm values, set overrides in Settings.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ZoneBlock(label: String, range: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape).background(color)
            )
            Spacer(Modifier.size(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(range, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
