package io.github.kevin.visibleclone.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

// ---------- WHOOP-derived facts (one row per API record) ----------

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey val id: String,
    val start: String,
    val end: String?,
    val strain: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val kilojoule: Double?,
)

@Entity(tableName = "recoveries")
data class RecoveryEntity(
    @PrimaryKey val cycleId: String,
    val sleepId: String?,
    val recoveryScore: Double?,
    val restingHeartRate: Int?,
    val hrvRmssd: Double?,
    val spo2: Double?,
    val skinTempCelsius: Double?,
    val createdAt: String,
)

@Entity(tableName = "sleeps")
data class SleepEntity(
    @PrimaryKey val id: String,
    val cycleId: String?,
    val start: String,
    val end: String,
    val nap: Boolean,
    val inBedMs: Long?,
    val awakeMs: Long?,
    val lightMs: Long?,
    val deepMs: Long?,
    val remMs: Long?,
    val disturbanceCount: Int?,
    val respiratoryRate: Double?,
    val performancePct: Double?,
    val efficiencyPct: Double?,
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val start: String,
    val end: String,
    val sportName: String?,
    val strain: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    /** WHOOP zones 0..5, milliseconds in each. Real zone data for logged activities. */
    val zone0Ms: Long? = null,
    val zone1Ms: Long? = null,
    val zone2Ms: Long? = null,
    val zone3Ms: Long? = null,
    val zone4Ms: Long? = null,
    val zone5Ms: Long? = null,
) {
    fun zoneDurationsMs(): List<Long?> =
        listOf(zone0Ms, zone1Ms, zone2Ms, zone3Ms, zone4Ms, zone5Ms)

    val hasZoneData: Boolean get() = zoneDurationsMs().any { (it ?: 0L) > 0L }
}

// ---------- Our own data ----------

@Entity(tableName = "hr_samples", primaryKeys = ["timestampMs", "source"])
data class HrSampleEntity(
    val timestampMs: Long,
    val source: String, // "ble" | "health"
    val bpm: Int,
)

/** One row per local date (yyyy-MM-dd) — the morning check-in. */
@Entity(tableName = "checkins")
data class CheckinEntity(
    @PrimaryKey val date: String,
    val sleepQuality: Int,
    val symptomSeverity: Int,
    val morningEnergy: Int?,
    val note: String?,
    val updatedAtMs: Long,
)

enum class LogKind { ACTIVITY, SYMPTOM, MEDICATION }

@Entity(tableName = "logs")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: LogKind,
    val date: String,
    val name: String,
    val startMs: Long,
    val endMs: Long?,
    val severity: Int?,
    val note: String?,
)

@Entity(tableName = "monthly_checkins")
data class MonthlyCheckinEntity(
    @PrimaryKey val month: String, // yyyy-MM
    val worstDays: Int,
    val bestDays: Int,
    val medicationChanged: Boolean,
    val note: String?,
    val updatedAtMs: Long,
)

@Dao
interface HealthDao {
    // WHOOP
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCycles(rows: List<CycleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecoveries(rows: List<RecoveryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleeps(rows: List<SleepEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkouts(rows: List<WorkoutEntity>)

    @Query("SELECT * FROM cycles ORDER BY start")
    suspend fun allCycles(): List<CycleEntity>

    @Query("SELECT * FROM recoveries ORDER BY createdAt")
    suspend fun allRecoveries(): List<RecoveryEntity>

    @Query("SELECT * FROM recoveries WHERE cycleId = :cycleId")
    suspend fun recoveryFor(cycleId: String): RecoveryEntity?

    @Query("SELECT * FROM sleeps ORDER BY start")
    suspend fun allSleeps(): List<SleepEntity>

    @Query("SELECT * FROM workouts ORDER BY start")
    suspend fun allWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE start >= :fromIso AND start < :toIso ORDER BY start")
    suspend fun workoutsBetween(fromIso: String, toIso: String): List<WorkoutEntity>

    @Query("SELECT * FROM sleeps WHERE start >= :fromIso AND start < :toIso AND nap = 0 ORDER BY start")
    suspend fun sleepsBetween(fromIso: String, toIso: String): List<SleepEntity>

    @Query("SELECT COUNT(*) FROM recoveries WHERE restingHeartRate IS NULL")
    suspend fun recoveriesMissingRhr(): Int

    /** Workouts stored before zone-duration capture, or not yet scored by WHOOP. */
    @Query("SELECT COUNT(*) FROM workouts WHERE zone2Ms IS NULL AND zone3Ms IS NULL AND zone4Ms IS NULL")
    suspend fun workoutsMissingZones(): Int

    @Query("SELECT MAX(start) FROM cycles")
    suspend fun lastCycleStart(): String?

    // HR
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHr(rows: List<HrSampleEntity>)

    @Query("SELECT * FROM hr_samples WHERE timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs")
    suspend fun hrBetween(fromMs: Long, toMs: Long): List<HrSampleEntity>

    @Query("DELETE FROM hr_samples WHERE timestampMs < :beforeMs")
    suspend fun pruneHrBefore(beforeMs: Long)

    // Check-ins and logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckin(row: CheckinEntity)

    @Query("SELECT * FROM checkins WHERE date = :date")
    suspend fun checkin(date: String): CheckinEntity?

    @Query("SELECT * FROM checkins ORDER BY date")
    suspend fun allCheckins(): List<CheckinEntity>

    @Insert
    suspend fun insertLog(row: LogEntryEntity): Long

    @Query("SELECT * FROM logs WHERE date = :date ORDER BY startMs")
    suspend fun logsFor(date: String): List<LogEntryEntity>

    @Query("SELECT * FROM logs ORDER BY startMs DESC")
    suspend fun allLogs(): List<LogEntryEntity>

    @Query("DELETE FROM logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonthly(row: MonthlyCheckinEntity)

    @Query("SELECT * FROM monthly_checkins WHERE month = :month")
    suspend fun monthly(month: String): MonthlyCheckinEntity?
}
