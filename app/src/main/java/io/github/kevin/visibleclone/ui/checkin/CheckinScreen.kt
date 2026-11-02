package io.github.kevin.visibleclone.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kevin.visibleclone.ui.DashboardViewModel
import io.github.kevin.visibleclone.ui.common.VGap

@Composable
fun CheckinScreen(vm: DashboardViewModel, onDone: () -> Unit) {
    var sleep by remember { mutableStateOf(3) }
    var symptom by remember { mutableStateOf(3) }
    var energy by remember { mutableStateOf(3) }
    var note by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Morning check-in", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "How did the night and the first minutes this morning feel?",
            style = MaterialTheme.typography.bodyMedium,
        )

        Rating("Sleep quality", sleep, "1 poor" to "5 great") { sleep = it }
        Rating("Symptoms", symptom, "1 none" to "5 severe") { symptom = it }
        Rating("Morning energy", energy, "1 empty" to "5 good") { energy = it }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Anything else? (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.saveCheckin(sleep, symptom, energy, note); onDone() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save check-in") }

        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Why we ask", style = MaterialTheme.typography.titleSmall)
                VGap(4)
                Text(
                    "Visible feeds sleep quality and symptoms into the Stability Score alongside overnight HRV and resting HR. " +
                        "Your answers shift today's score and the PacePoints budget.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Rating(
    label: String,
    value: Int,
    anchors: Pair<String, String>,
    onChange: (Int) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        VGap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n ->
                FilterChip(selected = value == n, onClick = { onChange(n) }, label = { Text("$n") })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(anchors.first, style = MaterialTheme.typography.labelSmall)
            Text(anchors.second, style = MaterialTheme.typography.labelSmall)
        }
    }
}
