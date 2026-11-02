package io.github.kevin.visibleclone.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kevin.visibleclone.data.health.HealthConnectSource
import io.github.kevin.visibleclone.domain.PacingConfig
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cfg by remember(s.config) { mutableStateOf(s.config) }

    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { scope.launch { vm.refreshHealthConnect() } }

    // Clear transient messages after a moment.
    LaunchedEffect(s.message) {
        if (s.message != null) {
            kotlinx.coroutines.delay(4000)
            vm.clearMessage()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }
        }

        s.message?.let { msg ->
            item {
                Card {
                    Text(msg, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ---- WHOOP ----
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("WHOOP", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (s.connected) {
                        Text("Connected as ${s.profileName ?: "WHOOP user"}")
                        s.body?.maxHeartRate?.let {
                            Text("Max HR from WHOOP: $it bpm", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = vm::syncNow, enabled = !s.busy) { Text("Sync now") }
                            OutlinedButton(onClick = vm::signOut) { Text("Sign out") }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val url = vm.beginAuth()
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            },
                            enabled = !s.busy,
                        ) { Text("Sign in again") }
                    } else {
                        Text(
                            "Sign in with your WHOOP account. Overnight HRV, resting HR and sleep come from the API.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val url = vm.beginAuth()
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            },
                            enabled = !s.busy,
                        ) { Text("Sign in to WHOOP") }
                    }
                    if (s.busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).height(20.dp))
                }
            }
        }

        // ---- Live HR ----
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Live heart rate", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Primary source: WHOOP's Broadcast Heart Rate (Settings → Device Settings in the WHOOP app). " +
                            "The strap then acts as a standard Bluetooth heart-rate sensor.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Fallback: Health Connect. Status: ${s.hcAvailability.name.lowercase()}" +
                            if (s.hcGranted) " · permission granted" else " · no permission",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (s.hcAvailability == HealthConnectSource.Availability.AVAILABLE) {
                        FilledTonalButton(
                            onClick = { hcLauncher.launch(HealthConnectSource.PERMISSIONS) }
                        ) { Text(if (s.hcGranted) "Review Health Connect access" else "Allow Health Connect") }
                    }
                }
            }
        }

        // ---- Zone calibration ----
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Zone calibration", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "WHOOP publishes per-activity time-in-zone but not the bpm boundaries. " +
                            "This infers them from your own workouts: if a workout peaking at X reached " +
                            "zone 2, the boundary is at or below X.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = vm::calibrateZones, enabled = !s.busy) {
                        Text("Calibrate from my workouts")
                    }
                    s.calibration?.let { cal ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${cal.workoutsUsed} workouts used",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        cal.estimates.forEach { e ->
                            val range = s.zoneBounds.getOrNull(e.zone)?.let { (lo, hi) -> "$lo–$hi bpm" } ?: "—"
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Zone ${e.zone}" + if (!e.confident) " (default)" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(range, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Zone 0 is everything below the first boundary.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        // ---- Thresholds ----
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Pacing thresholds", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "These are our approximations of Visible's scoring; tune them to you.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    NumberField("Activity offset above RHR (bpm)", cfg.activityOffset.toDouble()) {
                        cfg = cfg.copy(activityOffset = it.toInt())
                    }
                    NumberField("Exertion fraction of HR reserve", cfg.exertionFraction) {
                        cfg = cfg.copy(exertionFraction = it)
                    }
                    // Visible's guidance is to tune your own zones before touching the budget, so
                    // these take precedence over the derived values.
                    OptionalNumberField(
                        label = "Fixed activity threshold (bpm)",
                        value = cfg.activityBpmOverride,
                        onChange = { cfg = cfg.copy(activityBpmOverride = it) },
                    )
                    OptionalNumberField(
                        label = "Fixed exertion threshold (bpm)",
                        value = cfg.exertionBpmOverride,
                        onChange = { cfg = cfg.copy(exertionBpmOverride = it) },
                    )
                    NumberField("Threshold softness (bpm)", cfg.thresholdRampBpm) {
                        cfg = cfg.copy(thresholdRampBpm = it.coerceIn(0.0, 20.0))
                    }
                    NumberField("Activity points per minute", cfg.c1) { cfg = cfg.copy(c1 = it) }
                    NumberField("Exertion base rate", cfg.c2) { cfg = cfg.copy(c2 = it) }
                    NumberField("Exertion exponential alpha", cfg.alpha) { cfg = cfg.copy(alpha = it) }
                    NumberField("Points scale (per minute)", cfg.zoneScale) { cfg = cfg.copy(zoneScale = it) }
                    NumberField("Budget at Stability 1", cfg.budgetAtStability1) {
                        cfg = cfg.copy(budgetAtStability1 = it)
                    }
                    NumberField("Budget at Stability 5", cfg.budgetAtStability5) {
                        cfg = cfg.copy(budgetAtStability5 = it)
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Stability Score", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    NumberField("Baseline window (days)", cfg.baselineDays.toDouble()) {
                        cfg = cfg.copy(baselineDays = it.toInt().coerceIn(7, 90))
                    }
                    NumberField("Minimum nights before scoring", cfg.minBaselineNights.toDouble()) {
                        cfg = cfg.copy(minBaselineNights = it.toInt().coerceIn(2, 60))
                    }
                    NumberField("HRV weight", cfg.wHrv) { cfg = cfg.copy(wHrv = it) }
                    NumberField("Resting HR weight", cfg.wRhr) { cfg = cfg.copy(wRhr = it) }
                    NumberField("Sleep weight", cfg.wSleep) { cfg = cfg.copy(wSleep = it) }
                    NumberField("Symptom weight", cfg.wSymptom) { cfg = cfg.copy(wSymptom = it) }
                }
            }
        }

        item {
            Button(onClick = { vm.saveConfig(cfg) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save thresholds")
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Privacy", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "All wearable data stays in this app's local database. The only network calls are to WHOOP's API. " +
                            "No analytics, no crash reporting, no ads.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This is a pacing aid, not a medical device. It does not diagnose or predict crashes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

/** Blank means "derive it"; entering a number pins the threshold. */
@Composable
private fun OptionalNumberField(
    label: String,
    value: Int?,
    onChange: (Int?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            onChange(t.trim().toIntOrNull())
        },
        label = { Text(label) },
        supportingText = { Text("Blank = derived from resting and max HR") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun NumberField(label: String, value: Double, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(trimNumber(value)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                it.toDoubleOrNull()?.let(onChange)
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(6.dp))
}

private fun trimNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
