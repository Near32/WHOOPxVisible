package io.github.kevin.visibleclone.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kevin.visibleclone.domain.PacePoints
import io.github.kevin.visibleclone.ui.DashboardState
import io.github.kevin.visibleclone.ui.DashboardViewModel
import io.github.kevin.visibleclone.ui.common.ScoreRing
import io.github.kevin.visibleclone.ui.common.SectionHeader
import io.github.kevin.visibleclone.ui.common.Sparkline
import io.github.kevin.visibleclone.ui.common.VGap
import io.github.kevin.visibleclone.ui.theme.zoneColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: DashboardViewModel,
    s: DashboardState,
    onOpenCheckin: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenMonthly: () -> Unit,
) {
    var showWhy by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState()
    val live = s.hr.connected && !s.hr.isStale()
    val zone = s.hr.bpm?.let { PacePoints.zoneAt(it, s.rhr ?: 60, s.maxHeartRate, s.config).name } ?: "RESTING"

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Today", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (s.syncing) {
                    CircularProgressIndicator(Modifier.padding(12.dp).height(22.dp))
                } else {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Sync") }
                }
            }
        }

        if (!s.connectedToWhoop) {
            item {
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Connect WHOOP to get your scores", fontWeight = FontWeight.SemiBold)
                        VGap(4)
                        Text(
                            "Sign in once in Settings; overnight HRV and resting heart rate drive the Stability Score.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        VGap(8)
                        Button(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                }
            }
        }

        // ---- Stability ----
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    val stability = s.stability
                    if (stability == null || stability.calibrating) {
                        Text("Stability", style = MaterialTheme.typography.titleMedium)
                        VGap(4)
                        Text(stability?.interpretation ?: "No overnight data yet")
                        if (stability != null) {
                            VGap(4)
                            Text(
                                "${stability.baselineNights} of ${s.config.minBaselineNights} nights collected",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ScoreRing(
                                fraction = stability.score / 5f,
                                diameter = 96,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text("${stability.score}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                Text("/5", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.padding(8.dp))
                            Column {
                                Text("Stability", style = MaterialTheme.typography.titleMedium)
                                Text(stability.interpretation, style = MaterialTheme.typography.bodyLarge)
                                VGap(6)
                                FilledTonalButton(onClick = onOpenCheckin) {
                                    Text(if (s.checkinDone) "Edit check-in" else "Morning check-in")
                                }
                            }
                        }
                        VGap(12)
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Metric("HRV", s.hrv?.let { "%.0f ms".format(it) } ?: "—")
                            Metric("RHR", s.rhr?.let { "$it bpm" } ?: "—")
                            Metric("Sleep", s.sleepHours?.let { "%.1f h".format(it) } ?: "—")
                        }
                        VGap(8)
                        FilledTonalButton(onClick = { showWhy = true }) { Text("Explain score") }
                    }
                }
            }
        }

        // ---- PacePoints ----
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("PacePoints", style = MaterialTheme.typography.titleMedium)
                            Text("%.1f of %.0f used".format(s.pacing.points, s.budget))
                        }
                        ScoreRing(
                            fraction = (s.pacing.points / s.budget).toFloat(),
                            diameter = 76,
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                "%.0f%%".format(100 * (s.pacing.points / s.budget).coerceIn(0.0, 1.0)),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    VGap(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Metric("Activity", "${s.pacing.activityMinutes} min")
                        Metric("Exertion", "${s.pacing.exertionMinutes} min")
                        Metric("Peak", if (s.pacing.peakBpm > 0) "${s.pacing.peakBpm} bpm" else "—")
                    }
                    VGap(10)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.background(zoneColor(zone, false), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (live && s.hr.bpm != null) "${s.hr.bpm} bpm · ${zone.lowercase()}" else "Live HR off",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.padding(4.dp))
                        FilledTonalButton(onClick = if (s.hr.connected) vm::stopLive else vm::startLive) {
                            Text(if (s.hr.connected) "Stop live" else "Start live HR")
                        }
                    }
                    if (!live) {
                        VGap(6)
                        Text(
                            "Turn on Broadcast Heart Rate in the WHOOP app, then start live HR here.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // ---- History preview ----
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    SectionHeader("Last 7 days", trailing = "Stability")
                    if (s.history.size < 2) {
                        Text("History appears after your first sync.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Sparkline(
                            values = s.history.takeLast(7).map { it.stability.toDouble() },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            fill = true,
                        )
                        VGap(12)
                        Sparkline(
                            values = s.history.takeLast(7).map { it.pacePoints },
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            fill = true,
                        )
                        VGap(8)
                        FilledTonalButton(onClick = onOpenHistory) { Text("Open history") }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenLive, modifier = Modifier.weight(1f)) { Text("Pacing screen") }
                FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpenLogs, modifier = Modifier.weight(1f)) { Text("Logs") }
                FilledTonalButton(onClick = onOpenMonthly, modifier = Modifier.weight(1f)) { Text("Monthly check-in") }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showWhy) {
        ModalBottomSheet(onDismissRequest = { showWhy = false }, sheetState = sheet) {
            val st = s.stability
            Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
                Text("Why this score", style = MaterialTheme.typography.titleLarge)
                VGap(12)
                if (st == null) {
                    Text("No overnight data.")
                } else {
                    WhyRow("HRV z-score", st.zHrv?.let { "%.2f".format(it) } ?: "—",
                        "Negative is better — HRV above your normal range")
                    WhyRow("Resting HR z-score", st.zRhr?.let { "%.2f".format(it) } ?: "—",
                        "Positive is worse — RHR above your normal range")
                    WhyRow("Sleep quality", "%.2f".format(st.sleepTerm), "From your morning check-in")
                    WhyRow("Symptoms", "%.2f".format(st.symptomTerm), "From your morning check-in")
                    VGap(8)
                    Text(
                        "Combined deviation %.2f over %d nights of baseline".format(st.deviation, st.baselineNights),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    VGap(8)
                    Text(
                        "Weights and cut points are yours to tune in Settings — Visible does not publish its formula.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun WhyRow(label: String, value: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
