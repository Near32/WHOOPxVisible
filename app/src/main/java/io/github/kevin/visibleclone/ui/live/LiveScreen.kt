package io.github.kevin.visibleclone.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kevin.visibleclone.domain.PacePoints
import io.github.kevin.visibleclone.ui.DashboardState
import io.github.kevin.visibleclone.ui.DashboardViewModel
import io.github.kevin.visibleclone.ui.common.ScoreRing
import io.github.kevin.visibleclone.ui.common.VGap
import io.github.kevin.visibleclone.ui.theme.ZonePalette

@Composable
fun LiveScreen(vm: DashboardViewModel, s: DashboardState, onBack: () -> Unit) {
    val rhr = s.rhr ?: 60
    val bpm = s.hr.bpm
    val zone = bpm?.let { PacePoints.zoneAt(it, rhr, s.maxHeartRate, s.config) }
    val color = ZonePalette.color(zone?.name ?: "RESTING")
    val live = s.hr.connected && !s.hr.isStale()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Live pacing", style = MaterialTheme.typography.headlineSmall)
        }

        ScoreRing(
            fraction = (s.pacing.points / s.budget).toFloat(),
            diameter = 240,
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                bpm?.toString() ?: "—",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = if (live) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("bpm", style = MaterialTheme.typography.labelLarge)
            VGap(8)
            Text(
                when {
                    !live -> s.hr.status
                    zone == PacePoints.Zone.EXERTION -> "Exertion — ease off"
                    zone == PacePoints.Zone.ACTIVITY -> "Activity"
                    else -> "Resting"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Column {
            Text(
                "%.1f of %.0f PacePoints".format(s.pacing.points, s.budget),
                style = MaterialTheme.typography.titleMedium,
            )
            VGap(6)
            LinearProgressIndicator(
                progress = { (s.pacing.points / s.budget).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = color,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Stat("Resting", "${s.pacing.restingMinutes}m")
            Stat("Activity", "${s.pacing.activityMinutes}m")
            Stat("Exertion", "${s.pacing.exertionMinutes}m")
        }

        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Thresholds", style = MaterialTheme.typography.titleSmall)
                VGap(4)
                Text(
                    "Activity from ${s.config.activityThreshold(rhr)} bpm · " +
                        "Exertion from ${s.config.exertionThreshold(rhr, s.maxHeartRate)} bpm",
                    style = MaterialTheme.typography.bodySmall,
                )
                VGap(2)
                Text(
                    "Rate right now: %.1f pt/min".format(
                        bpm?.let { PacePoints.rateAt(it, rhr, s.maxHeartRate, s.config) } ?: 0.0
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = if (s.hr.connected) vm::stopLive else vm::startLive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (s.hr.connected) "Stop listening" else "Connect to WHOOP broadcast")
        }
        Text(
            "Alerts fire from the foreground service even when this screen is closed.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
