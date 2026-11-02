package io.github.kevin.visibleclone.ui.history

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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kevin.visibleclone.ui.DashboardState
import io.github.kevin.visibleclone.ui.common.SectionHeader
import io.github.kevin.visibleclone.ui.common.Sparkline
import io.github.kevin.visibleclone.ui.common.VGap

@Composable
fun HistoryScreen(s: DashboardState, onBack: () -> Unit) {
    var days by remember { mutableIntStateOf(7) }
    val window = s.history.takeLast(days)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("History", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(days == 7, { days = 7 }, { Text("7 days") })
                FilterChip(days == 30, { days = 30 }, { Text("30 days") })
            }
        }
        if (window.isEmpty()) {
            item { Text("No synced days yet.") }
        } else {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        ChartBlock("Stability (1–5)", window.map { it.stability.toDouble() }, MaterialTheme.colorScheme.primary)
                        ChartBlock("HRV (ms)", window.mapNotNull { it.hrv }, MaterialTheme.colorScheme.tertiary)
                        ChartBlock("Resting HR (bpm)", window.mapNotNull { it.rhr?.toDouble() }, MaterialTheme.colorScheme.error)
                        ChartBlock("Sleep (hours)", window.mapNotNull { it.sleepHours }, MaterialTheme.colorScheme.secondary)
                        ChartBlock("PacePoints", window.map { it.pacePoints }, MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { SectionHeader("Days") }
            items(window.asReversed()) { day ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(day.date, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    day.hrv?.let { "HRV %.0f ms" .format(it) },
                                    day.rhr?.let { "RHR $it" },
                                    day.sleepHours?.let { "sleep %.1f h".format(it) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("${day.stability}/5", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "%.1f / %.0f pts".format(day.pacePoints, day.budget),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChartBlock(title: String, values: List<Double>, color: androidx.compose.ui.graphics.Color) {
    SectionHeader(title)
    if (values.size < 2) {
        Text("Not enough data", style = MaterialTheme.typography.bodySmall)
    } else {
        Sparkline(
            values = values,
            color = color,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            fill = true,
        )
    }
    VGap(8)
}
