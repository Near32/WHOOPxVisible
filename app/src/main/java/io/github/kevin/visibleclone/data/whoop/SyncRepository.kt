package io.github.kevin.visibleclone.data.whoop

import io.github.kevin.visibleclone.data.local.HealthDao
import io.github.kevin.visibleclone.data.local.SettingsStore
import java.time.Duration
import java.time.Instant

/**
 * Incremental pull from WHOOP. First run backfills 90 days (Visible's stated baseline window);
 * later runs start a day before the last cycle to catch late-arriving night data.
 */
class SyncRepository(
    private val dao: HealthDao,
    private val api: WhoopApiClient,
    private val settings: SettingsStore,
) {
    data class SyncResult(val cycles: Int, val recoveries: Int, val sleeps: Int, val workouts: Int)

    suspend fun sync(): SyncResult {
        val start = if (dao.recoveriesMissingRhr() > 0) {
            // A partial or older sync left gaps; refetch the whole baseline window.
            android.util.Log.i("WhoopSync", "incomplete recovery rows found — full backfill")
            Instant.now().minus(Duration.ofDays(90))
        } else {
            lastSyncStart()
        }
        val end = Instant.now()
        android.util.Log.i("WhoopSync", "sync from $start to $end")

        val cycles = api.cycles(start, end)
        val recoveries = api.recoveries(start, end)
        val sleeps = api.sleeps(start, end)
        val workouts = api.workouts(start, end)
        android.util.Log.i(
            "WhoopSync",
            "fetched cycles=${cycles.size} recoveries=${recoveries.size} sleeps=${sleeps.size} workouts=${workouts.size}",
        )

        dao.upsertCycles(cycles)
        dao.upsertRecoveries(recoveries)
        dao.upsertSleeps(sleeps)
        dao.upsertWorkouts(workouts)

        return SyncResult(cycles.size, recoveries.size, sleeps.size, workouts.size)
    }

    /** Refresh profile + body measurement (max HR). */
    suspend fun syncProfile() {
        settings.saveProfile(api.profileName())
        settings.saveBody(api.bodyMeasurement())
    }

    private suspend fun lastSyncStart(): Instant {
        val last = dao.lastCycleStart()
        return if (last.isNullOrBlank()) {
            Instant.now().minus(Duration.ofDays(90))
        } else {
            runCatching { Instant.parse(last).minus(Duration.ofDays(1)) }
                .getOrElse { Instant.now().minus(Duration.ofDays(90)) }
        }
    }
}
