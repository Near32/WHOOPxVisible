package io.github.kevin.visibleclone.ui.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kevin.visibleclone.data.local.LogKind
import io.github.kevin.visibleclone.domain.HrSample
import io.github.kevin.visibleclone.domain.PacePoints
import io.github.kevin.visibleclone.ui.DashboardViewModel
import io.github.kevin.visibleclone.ui.common.SectionHeader
import io.github.kevin.visibleclone.ui.common.VGap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Activity / symptom / medication log. Activities show the PacePoints they cost, computed from the
 * stored HR samples in their time window — this is the point of the feature: "showering cost 1.2 pts".
 */
@Composable
fun LogsScreen(
    vm: LogsViewModel,
    dash: DashboardViewModel,
    s: io.github.kevin.visibleclone.ui.DashboardState,
    todayHr: List<HrSample>,
    onBack: () -> Unit,
) {
    val logs by vm.logs.collectAsState()
    var kind by remember { mutableStateOf(LogKind.ACTIVITY) }
    var name by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(3) }

    val suggestions = listOf("Showering", "Cooking", "Walking", "Shopping", "Housework", "Work call")

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Logs", style = MaterialTheme.typography.headlineSmall)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.name.lowercase()) })
                }
            }
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (kind == LogKind.ACTIVITY) "Activity" else kind.name.lowercase()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (kind == LogKind.SYMPTOM) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Severity", Modifier.padding(top = 12.dp))
                    (1..5).forEach { n ->
                        FilterChip(selected = severity == n, onClick = { severity = n }, label = { Text("$n") })
                    }
                }
            }
        }

        if (kind == LogKind.ACTIVITY) {
            item {
                Column {
                    Text("Quick add", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.take(3).forEach { tag ->
                            AssistChip(onClick = { name = tag }, label = { Text(tag) })
                        }
                    }
                    VGap(4)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.drop(3).forEach { tag ->
                            AssistChip(onClick = { name = tag }, label = { Text(tag) })
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    vm.add(kind, name, if (kind == LogKind.SYMPTOM) severity else null, null)
                    name = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add ${kind.name.lowercase()}") }
        }

        item { SectionHeader("Recent") }
        items(logs) { entry ->
            val cost = if (entry.kind == LogKind.ACTIVITY) {
                activityCost(entry.startMs, entry.endMs, todayHr, s)
            } else null
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${entry.date} · ${entry.kind.name.lowercase()}" +
                                (entry.severity?.let { " · severity $it" } ?: "") +
                                (cost?.let { " · %.1f pts".format(it) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { vm.delete(entry.id) }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Points accumulated by HR samples between start and end; falls back to 30 minutes from start. */
private fun activityCost(
    startMs: Long,
    endMs: Long?,
    samples: List<HrSample>,
    s: io.github.kevin.visibleclone.ui.DashboardState,
): Double {
    val end = endMs ?: (startMs + 30 * 60_000)
    val window = samples.filter { it.timestampMs in startMs..end }
    if (window.isEmpty()) return 0.0
    val rhr = s.rhr ?: 60
    return PacePoints.compute(window, rhr, s.maxHeartRate, s.config).points
}
