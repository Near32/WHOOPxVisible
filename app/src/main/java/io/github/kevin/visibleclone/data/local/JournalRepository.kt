package io.github.kevin.visibleclone.data.local

/**
 * Repository for everything the user types: check-ins, logs, monthly retro. WHOOP data has its own
 * sync path; this is deliberately separate.
 */
class JournalRepository(private val dao: HealthDao) {

    suspend fun saveCheckin(date: String, sleep: Int, symptom: Int, energy: Int?, note: String?) {
        dao.upsertCheckin(
            CheckinEntity(
                date = date,
                sleepQuality = sleep,
                symptomSeverity = symptom,
                morningEnergy = energy,
                note = note?.takeIf { it.isNotBlank() },
                updatedAtMs = System.currentTimeMillis(),
            )
        )
    }

    suspend fun checkin(date: String) = dao.checkin(date)

    suspend fun addLog(kind: LogKind, date: String, name: String, startMs: Long, endMs: Long?, severity: Int?, note: String?) =
        dao.insertLog(
            LogEntryEntity(
                kind = kind, date = date, name = name, startMs = startMs, endMs = endMs,
                severity = severity, note = note?.takeIf { it.isNotBlank() },
            )
        )

    suspend fun deleteLog(id: Long) = dao.deleteLog(id)

    suspend fun logsFor(date: String) = dao.logsFor(date)

    suspend fun saveMonthly(month: String, worst: Int, best: Int, medChanged: Boolean, note: String?) =
        dao.upsertMonthly(
            MonthlyCheckinEntity(month, worst, best, medChanged, note?.takeIf { it.isNotBlank() }, System.currentTimeMillis())
        )

    suspend fun monthly(month: String) = dao.monthly(month)
}
