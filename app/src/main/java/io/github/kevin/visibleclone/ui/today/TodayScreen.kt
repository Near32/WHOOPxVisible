package io.github.kevin.visibleclone.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kevin.visibleclone.domain.PacePoints
import io.github.kevin.visibleclone.ui.DashboardState
import io.github.kevin.visibleclone.ui.DashboardViewModel
import io.github.kevin.visibleclone.ui.common.PaceArc
import io.github.kevin.visibleclone.ui.common.SoftCard
import io.github.kevin.visibleclone.ui.common.PacingDayChart
import io.github.kevin.visibleclone.ui.common.ScrubSeries
import io.github.kevin.visibleclone.ui.common.VGap
import io.github.kevin.visibleclone.ui.common.ZoneHrLine
import io.github.kevin.visibleclone.ui.theme.Visible
import io.github.kevin.visibleclone.ui.theme.ZonePalette
import java.time.LocalDate
import java.time.LocalTime
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
    onOpenThresholds: () -> Unit,
) {
    var showWhy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val blePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // Only start once the user has decided; otherwise the service reports the missing permission.
        if (granted.values.all { it }) vm.startLive()
    }
    val needsBlePermission: () -> Boolean = {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }
    val sheet = rememberModalBottomSheetState()
    val live = s.hr.connected && !s.hr.isStale()
    val zone = s.hr.bpm?.let { PacePoints.zoneAt(it, s.rhr ?: 60, s.maxHeartRate, s.config).name }
        ?: "RESTING"
    val spentFraction = (s.pacing.points / s.budget).toFloat()
    val dayElapsed = (LocalTime.now().toSecondOfDay() / 86_400f).coerceIn(0f, 1f)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Today", style = MaterialTheme.typography.headlineSmall)
                }
                if (s.syncing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, "Sync", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Always-visible entry point: sign-in lives here and must never be buried.
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        "Settings and sign-in",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        s.syncError?.let { err ->
            item {
                SoftCard(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        if (s.needsReauth) "Sign-in expired" else "Sync problem",
                        fontWeight = FontWeight.SemiBold,
                    )
                    VGap(2)
                    Text(err, style = MaterialTheme.typography.bodySmall)
                    if (s.needsReauth) {
                        VGap(10)
                        Button(onClick = onOpenSettings) { Text("Sign in again") }
                    }
                }
            }
        }

        // ---- PacePoints arc: the hero element ----
        item {
            SoftCard {
                PaceArc(
                    spent = spentFraction,
                    paceSetter = dayElapsed,
                    height = 210,
                    spentColor = ZonePalette.color(zone),
                ) {
                    Text(
                        "%.1f".format(s.pacing.points),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "of %.0f PacePoints".format(s.budget),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (s.pacingEstimated) {
                    Text(
                        "Estimated from your average heart rate — start live HR for exact pacing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (s.pacingFromZones) {
                    Text(
                        "From ${s.workoutsToday} logged ${if (s.workoutsToday == 1) "activity" else "activities"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                VGap(14)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ZoneStat("Rest", "${s.pacing.restingMinutes}m", Visible.rest)
                    ZoneStat("Activity", "${s.pacing.activityMinutes}m", Visible.activity)
                    ZoneStat("Exertion", "${s.pacing.exertionMinutes}m", Visible.exertion)
                }
            }
        }

        // ---- Pacing day ----
        if (s.pacingDay.size == 96 && s.pacingDay.any { it > 0f }) {
            item {
                SoftCard {
                    Text("Your pacing day", style = MaterialTheme.typography.titleMedium)
                    VGap(2)
                    Text(
                        "Points accumulate through the day. The line is the daily budget.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VGap(8)
                    PacingDayChart(
                        cumulative = s.pacingDay,
                        budget = s.budget.toFloat(),
                        lineColor = MaterialTheme.colorScheme.primary,
                        nowSlot = LocalTime.now().let { (it.hour * 4 + it.minute / 15).coerceIn(0, 95) },
                    )
                }
            }
        }

        // ---- Today's activities: why each cost what it did ----
        if (s.activities.isNotEmpty()) {
            item {
                SoftCard {
                    Text("Today's activities", style = MaterialTheme.typography.titleMedium)
                    VGap(8)
                    s.activities.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.name.replace('-', ' ').replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                val hrLine = buildString {
                                    append("${a.minutes} min")
                                    a.avgHeartRate?.let { append(" · avg $it bpm") }
                                    a.impliedHeartRate?.let { append(" · rest-side $it bpm") }
                                }
                                Text(
                                    hrLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (a.zone == "UNSCORED") "—" else "%.2f".format(a.points),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    when (a.zone) {
                                        "UNSCORED" -> "not scored"
                                        "EXERTION" -> "exertion"
                                        "ACTIVITY" -> "activity"
                                        else -> "rest"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ZonePalette.color(a.zone),
                                )
                            }
                        }
                    }
                    VGap(4)
                    Text(
                        "“rest-side” is the heart rate inferred for the low-zone part of an activity. " +
                            "Unscored activities (meditation, recovery) cost nothing.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Heart rate ----
        item {
            SoftCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (live && s.hr.bpm != null) "${s.hr.bpm}" else "—",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(" bpm", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(8.dp))
                        Box(
                            Modifier.clip(CircleShape).background(ZonePalette.color(zone))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                if (live) ZonePalette.label(zone) else "Off",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (s.history.isNotEmpty()) {
                            Text(
                                "Peak ${s.pacing.peakBpm.takeIf { it > 0 } ?: "—"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Strap battery, read from the standard BLE Battery Service.
                        s.hr.batteryPercent?.let { pct ->
                            Spacer(Modifier.size(3.dp))
                            BatteryPill(pct)
                        }
                    }
                }
                VGap(10)
                // Only plot real intraday readings. Resting HR across days is not "your heart rate
                // today" and would put the line permanently in the rest band.
                if (s.todayHr.size >= 2) {
                    ZoneHrLine(
                        values = s.todayHr,
                        activityBpm = s.config.activityThreshold(s.rhr ?: 60),
                        exertionBpm = s.config.exertionThreshold(s.rhr ?: 60, s.maxHeartRate),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        restColor = Visible.rest,
                        activityColor = Visible.activity,
                        exertionColor = Visible.exertion,
                    )
                    VGap(8)
                }
                Button(
                    onClick = {
                        when {
                            s.hr.connected -> vm.stopLive()
                            needsBlePermission() -> blePermissions.launch(
                                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                            )
                            else -> vm.startLive()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (live) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (live) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) { Text(if (s.hr.connected) "Stop live HR" else "Start live HR") }
                if (live && s.currentRatePerMin > 0.0) {
                    VGap(4)
                    Text(
                        "Accumulating %.2f points/min".format(s.currentRatePerMin),
                        style = MaterialTheme.typography.labelMedium,
                        color = ZonePalette.color(zone),
                    )
                }
                // Always show what the BLE client is doing — a silent revert is unhelpful.
                VGap(6)
                Text(
                    s.hr.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!live && s.hr.status == "Idle") {
                    Text(
                        "Turn on Broadcast Heart Rate in the WHOOP app first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Stability ----
        item {
            SoftCard {
                val stability = s.stability
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Stability",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (stability == null || stability.calibrating) {
                            VGap(2)
                            Text(
                                stability?.interpretation ?: "No overnight data yet",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (stability != null) {
                                Text(
                                    "${stability.baselineNights} of ${s.config.minBaselineNights} nights collected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${stability.score}",
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    " / 5",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    stability.interpretation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                    if (stability != null && !stability.calibrating) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape)
                                .background(stabilityColor(stability.score))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${stability.score}",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                VGap(12)
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Metric("HRV", s.hrv?.let { "%.0f".format(it) } ?: "—", "ms")
                    Metric("Resting HR", s.rhr?.let { "$it" } ?: "—", "bpm")
                    Metric("Sleep", s.sleepHours?.let { "%.1f".format(it) } ?: "—", "h")
                }
                VGap(12)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenCheckin) {
                        Text(if (s.checkinDone) "Edit check-in" else "Check in")
                    }
                    if (stability != null && !stability.calibrating) {
                        FilledTonalButton(onClick = { showWhy = true }) { Text("Why?") }
                    }
                }
            }
        }

        // ---- Trend ----
        if (s.history.size >= 2) {
            item {
                SoftCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Stability",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    VGap(8)
                    val week = s.history.takeLast(7)
                    ScrubSeries(
                        values = week.map { it.stability.toFloat() },
                        labels = week.map { it.date.takeLast(5) },
                        lineColor = MaterialTheme.colorScheme.primary,
                        valueFormat = { "%.0f / 5".format(it) },
                    )
                    VGap(12)
                    Text(
                        "PacePoints",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ScrubSeries(
                        values = week.map { it.pacePoints.toFloat() },
                        labels = week.map { it.date.takeLast(5) },
                        lineColor = Visible.activity,
                        valueFormat = { "%.1f pts".format(it) },
                    )
                    VGap(10)
                    FilledTonalButton(onClick = onOpenHistory) { Text("Open history") }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpenLogs, modifier = Modifier.weight(1f)) { Text("Logs") }
                FilledTonalButton(onClick = onOpenLive, modifier = Modifier.weight(1f)) { Text("Pacing") }
                FilledTonalButton(onClick = onOpenThresholds, modifier = Modifier.weight(1f)) { Text("Zones") }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }

    if (showWhy) {
        ModalBottomSheet(onDismissRequest = { showWhy = false }, sheetState = sheet) {
            val st = s.stability
            Column(Modifier.padding(20.dp).padding(bottom = 28.dp)) {
                Text("Why this score", style = MaterialTheme.typography.titleLarge)
                VGap(14)
                if (st == null) {
                    Text("No overnight data.")
                } else {
                    WhyRow("HRV", st.zHrv?.let { "%+.2f" .format(it) } ?: "—",
                        "Negative is better — HRV above your normal range")
                    WhyRow("Resting HR", st.zRhr?.let { "%+.2f".format(it) } ?: "—",
                        "Positive is worse — RHR above your normal range")
                    WhyRow("Sleep quality", "%+.2f".format(st.sleepTerm), "From your check-in")
                    WhyRow("Symptoms", "%+.2f".format(st.symptomTerm), "From your check-in")
                    VGap(10)
                    Text(
                        "Combined deviation %+.2f over %d nights".format(st.deviation, st.baselineNights),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    VGap(8)
                    Text(
                        "Weights and cut points are yours to tune in Settings — Visible does not publish its formula.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun stabilityColor(score: Int): Color = when {
    score >= 5 -> Visible.mintDeep
    score == 4 -> Color(0xFF6BBF9A)
    score == 3 -> Visible.activity
    score == 2 -> Color(0xFFE0975F)
    else -> Visible.exertion
}

@Composable
private fun ZoneStat(label: String, value: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.size(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(2.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WhyRow(label: String, value: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}


/** Small battery indicator; green above 40%, amber 15-40%, coral below 15%. */
@Composable
private fun BatteryPill(percent: Int) {
    val color = when {
        percent >= 40 -> Visible.mintDeep
        percent >= 15 -> Visible.activity
        else -> Visible.exertion
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 22.dp, height = 11.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.18f))
                .padding(1.5.dp),
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.size(3.dp))
        Text(
            "$percent%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
