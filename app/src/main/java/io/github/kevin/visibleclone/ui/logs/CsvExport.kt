package io.github.kevin.visibleclone.ui.logs

import io.github.kevin.visibleclone.data.local.CheckinEntity
import io.github.kevin.visibleclone.data.local.CycleEntity
import io.github.kevin.visibleclone.data.local.LogEntryEntity
import io.github.kevin.visibleclone.data.local.RecoveryEntity

/** Plain CSV so the data can be opened in a spreadsheet or handed to a clinician. */
object CsvExport {

    fun build(
        cycles: List<CycleEntity>,
        recoveries: List<RecoveryEntity>,
        checkins: List<CheckinEntity>,
        logs: List<LogEntryEntity>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("date,hrv_rmssd_ms,resting_hr,recovery_score,strain,sleep_quality,symptom_severity,note,logs")
        val recByCycleDate = recoveries.associateBy { it.createdAt.take(10) }
        val checkByDate = checkins.associateBy { it.date }
        val logsByDate = logs.groupBy { it.date }

        cycles.forEach { cycle ->
            val date = cycle.start.take(10)
            val rec = recByCycleDate[date]
            val check = checkByDate[date]
            val dayLogs = logsByDate[date].orEmpty().joinToString(" | ") {
                "${it.kind.name.lowercase()}:${it.name}" + (it.severity?.let { s -> "($s)" } ?: "")
            }
            sb.appendLine(
                listOf(
                    date,
                    rec?.hrvRmssd?.toString() ?: "",
                    rec?.restingHeartRate?.toString() ?: "",
                    rec?.recoveryScore?.toString() ?: "",
                    cycle.strain?.toString() ?: "",
                    check?.sleepQuality?.toString() ?: "",
                    check?.symptomSeverity?.toString() ?: "",
                    escape(check?.note ?: ""),
                    escape(dayLogs),
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun escape(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) '"' + s.replace("\"", "\"\"") + '"' else s
}
