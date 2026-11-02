package io.github.kevin.visibleclone.ui.monthly

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kevin.visibleclone.ui.DashboardViewModel
import io.github.kevin.visibleclone.ui.common.VGap
import java.time.YearMonth

@Composable
fun MonthlyScreen(vm: DashboardViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val month = YearMonth.now().toString()
    var worst by remember { mutableStateOf(3) }
    var best by remember { mutableStateOf(3) }
    var medChanged by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Monthly check-in", style = MaterialTheme.typography.headlineSmall)
        }
        Text(month, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Looking back over the month: how many bad days, how many good ones, and did anything change?",
            style = MaterialTheme.typography.bodySmall,
        )

        CountPicker("Bad days this month", worst) { worst = it }
        CountPicker("Good days this month", best) { best = it }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Medication changed")
            Switch(checked = medChanged, onCheckedChange = { medChanged = it })
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("What else happened?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )

        Button(
            onClick = { vm.saveMonthly(month, worst, best, medChanged, note); onSaved() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save monthly check-in") }

        Card {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Visible uses the monthly check-in to spot longer patterns that daily data hides. " +
                        "It is stored locally with your other logs.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CountPicker(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        VGap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..5).forEach { n ->
                FilterChip(selected = value == n, onClick = { onChange(n) }, label = { Text("$n") })
            }
        }
        if (value == 5) Text("5+", style = MaterialTheme.typography.labelSmall)
    }
}
