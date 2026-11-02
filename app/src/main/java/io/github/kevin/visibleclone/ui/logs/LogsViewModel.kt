package io.github.kevin.visibleclone.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kevin.visibleclone.App
import io.github.kevin.visibleclone.data.local.LogEntryEntity
import io.github.kevin.visibleclone.data.local.LogKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class LogsViewModel(private val app: App) : ViewModel() {

    private val local = MutableStateFlow<List<LogEntryEntity>>(emptyList())
    val logs: StateFlow<List<LogEntryEntity>> = local
    private val today = LocalDate.now().toString()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { local.value = app.db.dao().allLogs() }
    }

    fun add(kind: LogKind, name: String, severity: Int?, note: String?, startMs: Long = System.currentTimeMillis(), endMs: Long? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            app.journal.addLog(kind, today, name.trim(), startMs, endMs, severity, note)
            refresh()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            app.journal.deleteLog(id)
            refresh()
        }
    }
}
