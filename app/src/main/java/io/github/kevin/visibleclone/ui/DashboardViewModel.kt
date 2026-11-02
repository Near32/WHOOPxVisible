package io.github.kevin.visibleclone.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kevin.visibleclone.App
import io.github.kevin.visibleclone.data.ble.HrBroadcastService
import io.github.kevin.visibleclone.data.ble.HrState
import io.github.kevin.visibleclone.data.local.CycleEntity
import io.github.kevin.visibleclone.data.local.RecoveryEntity
import io.github.kevin.visibleclone.data.local.SleepEntity
import io.github.kevin.visibleclone.domain.HrSample
import io.github.kevin.visibleclone.domain.PaceBreakdown
import io.github.kevin.visibleclone.domain.PacePoints
import io.github.kevin.visibleclone.domain.PacingConfig
import io.github.kevin.visibleclone.domain.StabilityInputs
import io.github.kevin.visibleclone.domain.StabilityResult
import io.github.kevin.visibleclone.domain.StabilityScore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Why one logged activity cost what it did. */
data class ActivityInsight(
    val name: String,
    val startMs: Long,
    val minutes: Int,
    val avgHeartRate: Int?,
    val impliedHeartRate: Int?,
    val zone: String,
    val points: Double,
)

/** Thresholds on a given day — they move with resting HR. */
data class ThresholdDay(
    val date: String,
    val restingHr: Int,
    val activityBpm: Int,
    val exertionBpm: Int,
)

data class DayPoint(
    val date: String,
    val stability: Int,
    val hrv: Double?,
    val rhr: Int?,
    val pacePoints: Double,
    val budget: Double,
    val sleepHours: Double?,
)

data class DashboardState(
    val today: String = LocalDate.now().toString(),
    val stability: StabilityResult? = null,
    val checkinDone: Boolean = false,
    val pacing: PaceBreakdown = PaceBreakdown(0.0, 0, 0, 0, 0),
    val pacingEstimated: Boolean = false,
    val pacingFromZones: Boolean = false,
    val workoutsToday: Int = 0,
    /** Today's intraday HR readings, for the HR chart. Empty when no live source is running. */
    val todayHr: List<Int> = emptyList(),
    /** Cumulative PacePoints per 15-minute slot since local midnight (96 entries). */
    val pacingDay: List<Float> = emptyList(),
    /** Points/minute at the current bpm, so the panel can show accumulation live. */
    val currentRatePerMin: Double = 0.0,
    val activities: List<ActivityInsight> = emptyList(),
    val thresholdDays: List<ThresholdDay> = emptyList(),
    val activityThresholdBpm: Int = 62,
    val exertionThresholdBpm: Int = 120,
    val budget: Double = 14.0,
    val config: PacingConfig = PacingConfig(),
    val sleepHours: Double? = null,
    val hrv: Double? = null,
    val rhr: Int? = null,
    val history: List<DayPoint> = emptyList(),
    val hr: HrState = HrState(),
    val connectedToWhoop: Boolean = false,
    val syncError: String? = null,
    val needsReauth: Boolean = false,
    val syncing: Boolean = false,
    val maxHeartRate: Int = 185,
)

class DashboardViewModel(private val app: App) : ViewModel() {

    private val local = MutableStateFlow(DashboardState())
    private val zone: ZoneId = ZoneId.systemDefault()

    val state: StateFlow<DashboardState> = combine(
        local,
        HrBroadcastService.state,
        app.settings.config,
    ) { d, hr, cfg -> d.copy(hr = hr, config = cfg) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    init {
        refresh()
        viewModelScope.launch {
            app.settings.tokens.collect { toks -> local.update { it.copy(connectedToWhoop = toks != null) } }
        }
        // Live HR changes the numbers continuously; recompute while a session is running.
        viewModelScope.launch {
            while (true) {
                val hr = HrBroadcastService.state.value
                if (hr.connected && !hr.isStale()) recomputePacing()
                kotlinx.coroutines.delay(10_000)
            }
        }
        // Tuning thresholds in Settings should immediately change today's numbers.
        viewModelScope.launch {
            app.settings.config.drop(1).collect { recompute() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(syncing = true, syncError = null) }
            if (app.settings.currentTokens() != null) {
                runCatching { app.sync.sync() }
                    .onFailure { e ->
                        android.util.Log.e("WhoopSync", "sync failed", e)
                        if (isSessionExpired(e)) {
                            // The refresh token is dead; nothing will work until the user signs in.
                            app.settings.clearTokens()
                            local.update {
                                it.copy(
                                    syncError = "Your WHOOP session expired. Sign in again to keep syncing.",
                                    needsReauth = true,
                                    connectedToWhoop = false,
                                )
                            }
                        } else {
                            local.update { it.copy(syncError = e.message) }
                        }
                    }
            }
            recompute()
            local.update { it.copy(syncing = false) }
        }
    }

    /**
     * Per-activity breakdown, so a logged activity can explain itself: what average HR it implied,
     * which zone that landed in, and what it cost.
     */
    private fun insightsFor(
        workouts: List<io.github.kevin.visibleclone.data.local.WorkoutEntity>,
        rhr: Int,
        maxHr: Int,
        cfg: PacingConfig,
    ): List<ActivityInsight> = workouts.mapNotNull { w ->
        val start = runCatching { java.time.Instant.parse(w.start).toEpochMilli() }.getOrNull()
            ?: return@mapNotNull null
        val end = runCatching { java.time.Instant.parse(w.end).toEpochMilli() }.getOrNull()
            ?: start
        val minutes = ((end - start) / 60_000L).toInt().coerceAtLeast(0)

        val implied = if (w.hasZoneData) impliedZoneZero(w.zoneDurationsMs(), w.avgHeartRate, rhr, maxHr, cfg) else null
        val points = if (w.hasZoneData) {
            PacePoints.fromZoneDurations(w.zoneDurationsMs(), rhr, maxHr, cfg, w.avgHeartRate).points
        } else 0.0

        val representative = implied ?: w.avgHeartRate
        val zone = representative?.let {
            PacePoints.zoneAt(it, rhr, maxHr, cfg).name
        } ?: "UNSCORED"

        ActivityInsight(
            name = w.sportName ?: "activity",
            startMs = start,
            minutes = minutes,
            avgHeartRate = w.avgHeartRate,
            impliedHeartRate = implied,
            zone = zone,
            points = points,
        )
    }.sortedByDescending { it.startMs }

    /** Zone-0 intensity recovered from the activity average, or null when not applicable. */
    private fun impliedZoneZero(
        zoneMillis: List<Long?>,
        avgHeartRate: Int?,
        rhr: Int,
        maxHr: Int,
        cfg: PacingConfig,
    ): Int? {
        if (avgHeartRate == null || zoneMillis.size < 6) return null
        val minutes = zoneMillis.map { (it ?: 0L) / 60_000.0 }
        val total = minutes.sum()
        if (total <= 0 || minutes[0] < total * 0.2) return null
        val reserve = (maxHr - rhr).coerceAtLeast(1)
        val edges = listOf(0.0) + cfg.whoopZoneHrr + listOf(1.0)
        val mids = (0..5).map { i -> rhr + ((edges[i] + edges[i + 1]) / 2) * reserve }
        val implied = (avgHeartRate * total - (1..5).sumOf { mids[it] * minutes[it] }) / minutes[0]
        val upper = rhr + cfg.whoopZoneHrr.first() * reserve
        return if (implied in rhr.toDouble()..upper) implied.toInt() else null
    }

    /**
     * Daily thresholds over the last 90 days. They move because they are derived from resting HR,
     * which is why a fixed bpm number would drift away from your physiology.
     */
    private fun thresholdHistory(
        recoveries: List<RecoveryEntity>,
        maxHr: Int,
        cfg: PacingConfig,
    ): List<ThresholdDay> = recoveries.mapNotNull { r ->
        val rhr = r.restingHeartRate ?: return@mapNotNull null
        val date = r.createdAt.take(10)
        if (date.isBlank()) return@mapNotNull null
        ThresholdDay(
            date = date,
            restingHr = rhr,
            activityBpm = cfg.activityThreshold(rhr),
            exertionBpm = cfg.exertionThreshold(rhr, maxHr),
        )
    }

    /**
     * Cumulative PacePoints through the day, in 15-minute slots.
     *
     * Zone durations carry no within-workout timing, so each activity's points are spread evenly
     * across its start..end window. Live HR samples, when present, are used directly.
     */
    private suspend fun buildPacingDay(
        workoutsToday: Int,
        dayStart: Long,
        rhr: Int,
        maxHr: Int,
        cfg: PacingConfig,
        samples: List<HrSample>,
    ): List<Float> {
        val slots = FloatArray(96)
        val dao = app.db.dao()

        if (samples.isNotEmpty()) {
            // Distribute sampled points into the slot each sample falls in.
            val ordered = samples.sortedBy { it.timestampMs }
            for (i in ordered.indices) {
                val spanMs = if (i + 1 < ordered.size) {
                    (ordered[i + 1].timestampMs - ordered[i].timestampMs).coerceIn(0L, 60_000L)
                } else 0L
                val pts = PacePoints.rateAt(ordered[i].bpm, rhr, maxHr, cfg) * (spanMs / 60_000.0)
                val slot = ((ordered[i].timestampMs - dayStart) / (15 * 60_000L)).toInt()
                if (slot in 0..95) slots[slot] += pts.toFloat()
            }
        }

        // Activities always contribute. Where live samples cover the same window, the sample
        // contribution already counted it, so only the uncovered share is added.
        if (workoutsToday > 0) {
            val iso = java.time.Instant.ofEpochMilli(dayStart).toString()
            val endIso = java.time.Instant.ofEpochMilli(dayStart + 86_400_000L).toString()
            val coverage = PacePoints.coverageIntervals(samples, 120_000)
            dao.workoutsBetween(iso, endIso).filter { it.hasZoneData }.forEach { w ->
                val pts = PacePoints.fromZoneDurations(
                    w.zoneDurationsMs(), rhr, maxHr, cfg, w.avgHeartRate,
                ).points
                if (pts <= 0.0) return@forEach
                val s = runCatching { java.time.Instant.parse(w.start).toEpochMilli() }.getOrNull()
                    ?: return@forEach
                val e = runCatching { java.time.Instant.parse(w.end).toEpochMilli() }.getOrNull()
                    ?: (s + 60_000L)
                val duration = (e - s).coerceAtLeast(60_000L)
                val covered = coverage.sumOf { (cs, ce) ->
                    (minOf(ce, e) - maxOf(cs, s)).coerceAtLeast(0L)
                }.coerceAtMost(duration)
                val uncovered = 1.0 - covered.toDouble() / duration
                if (uncovered <= 0.0) return@forEach
                for (i in 0..95) {
                    val slotStart = dayStart + i * 15 * 60_000L
                    val slotEnd = slotStart + 15 * 60_000L
                    val overlap = (minOf(e, slotEnd) - maxOf(s, slotStart)).coerceAtLeast(0L)
                    if (overlap > 0) {
                        slots[i] += (pts * overlap / duration * uncovered).toFloat()
                    }
                }
            }
        }

        var acc = 0f
        return slots.map { acc += it; acc }
    }

    /**
     * Recomputes only the pacing figures from stored HR samples. Cheaper than a full [recompute]
     * (no API, no re-baselining) and is what makes the live panel move on its own.
     */
    suspend fun recomputePacing() {
        val cfg = app.settings.currentConfig()
        val dao = app.db.dao()
        val maxHr = app.settings.currentBody()?.maxHeartRate ?: cfg.fallbackMaxHr
        val rhr = dao.allRecoveries().lastOrNull()?.restingHeartRate ?: 60

        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val samples = dao.hrBetween(dayStart, System.currentTimeMillis())
            .map { HrSample(it.timestampMs, it.bpm) }
        if (samples.isEmpty()) return

        val zonedActivities = dao.workoutsBetween(
            java.time.Instant.ofEpochMilli(dayStart).toString(),
            java.time.Instant.ofEpochMilli(dayStart + 86_400_000L).toString(),
        ).filter { it.hasZoneData }.mapNotNull { w ->
            val a = runCatching { java.time.Instant.parse(w.start).toEpochMilli() }.getOrNull()
            val b = runCatching { java.time.Instant.parse(w.end).toEpochMilli() }.getOrNull()
            if (a == null || b == null) null
            else PacePoints.ZonedActivity(a, b, w.zoneDurationsMs(), w.avgHeartRate)
        }

        val pacing = PacePoints.combine(samples, zonedActivities, rhr, maxHr, cfg)
        val bpm = HrBroadcastService.state.value.bpm
        local.update {
            it.copy(
                pacing = pacing,
                pacingEstimated = false,
                pacingFromZones = zonedActivities.isNotEmpty(),
                currentRatePerMin = bpm?.let { b -> PacePoints.rateAt(b, rhr, maxHr, cfg) } ?: 0.0,
                pacingDay = buildPacingDay(
                    zonedActivities.size, dayStart, rhr, maxHr, cfg, samples,
                ),
            )
        }
    }

    private fun isSessionExpired(e: Throwable): Boolean {
        if (e is io.github.kevin.visibleclone.data.whoop.UnauthorizedException) return true
        val m = e.message.orEmpty().lowercase()
        return "invalid_grant" in m || "401" in m || "unauthorized" in m
    }

    suspend fun recompute() {
        val cfg = app.settings.currentConfig()
        val dao = app.db.dao()
        val recoveries = dao.allRecoveries()
        val cycles = dao.allCycles()
        val sleeps = dao.allSleeps()
        val body = app.settings.currentBody()
        val maxHr = body?.maxHeartRate ?: cfg.fallbackMaxHr

        val today = LocalDate.now(zone).toString()
        val checkin = dao.checkin(today)

        // Oldest-first series for the baseline. WHOOP returns one recovery per cycle.
        val hrvSeries = recoveries.mapNotNull { it.hrvRmssd }
        val rhrSeries = recoveries.mapNotNull { it.restingHeartRate?.toDouble() }

        val stability = if (hrvSeries.isEmpty() && rhrSeries.isEmpty()) null else StabilityScore.compute(
            StabilityInputs(
                hrvSeries = hrvSeries,
                rhrSeries = rhrSeries,
                sleepQuality = checkin?.sleepQuality,
                symptomSeverity = checkin?.symptomSeverity,
            ),
            cfg,
        )

        val todaysRecovery = recoveries.lastOrNull()
        val rhr = todaysRecovery?.restingHeartRate ?: rhrSeries.lastOrNull()?.toInt() ?: 60
        val budget = stability?.let { cfg.dailyBudget(it.score) } ?: cfg.dailyBudget(3)

        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = Instant.now().toEpochMilli()
        val bleHr = dao.hrBetween(dayStart, dayEnd).map { HrSample(it.timestampMs, it.bpm) }

        // Health Connect fallback: if no BLE samples today, pull Fitbit's minute-level HR.
        val samples = if (bleHr.isNotEmpty()) bleHr else run {
            val hc = runCatching {
                app.healthConnect.heartRate(
                    Instant.ofEpochMilli(dayStart), Instant.ofEpochMilli(dayEnd)
                ).map { HrSample(it.timestampMs, it.bpm) }
            }.getOrDefault(emptyList())
            if (hc.size > bleHr.size) hc else bleHr
        }

        // The current cycle is the one WHOOP has not closed yet. Comparing dates is unreliable:
        // cycle.start is UTC and can land on the previous local day.
        val todayCycle = cycles.lastOrNull { it.end == null } ?: cycles.lastOrNull()
        val dayIsoStart = java.time.Instant.ofEpochMilli(dayStart).toString()
        val dayIsoEnd = java.time.Instant.ofEpochMilli(dayStart + 86_400_000L).toString()
        val todaysWorkouts = app.db.dao().workoutsBetween(dayIsoStart, dayIsoEnd)
        val todaysWorkoutCount = todaysWorkouts.size
        val zonedActivities = todaysWorkouts.filter { it.hasZoneData }.mapNotNull { w ->
            val s = runCatching { java.time.Instant.parse(w.start).toEpochMilli() }.getOrNull()
            val e = runCatching { java.time.Instant.parse(w.end).toEpochMilli() }.getOrNull()
            if (s == null || e == null) null
            else PacePoints.ZonedActivity(s, e, w.zoneDurationsMs(), w.avgHeartRate)
        }
        val pacing: PaceBreakdown
        var estimated = false
        var zoneDerived = false
        if (samples.isNotEmpty()) {
            // Live HR and logged activities cover different windows — merge, never substitute.
            pacing = PacePoints.combine(samples, zonedActivities, rhr, maxHr, cfg)
            zoneDerived = zonedActivities.isNotEmpty()
        } else {
            // No live HR (BLE / Health Connect). Use WHOOP's own measured zone durations from the
            // activities logged today — real intraday zone time beats any average-HR guess.
            if (zonedActivities.isNotEmpty()) {
                pacing = PacePoints.combine(
                    emptyList(), zonedActivities, rhr, maxHr, cfg,
                )
                estimated = false
                zoneDerived = true
            } else {
                val elapsedHours = ((System.currentTimeMillis() - dayStart) / 3_600_000.0)
                    .coerceIn(0.0, 16.0)
                val avg = todayCycle?.avgHeartRate ?: rhr
                val points = PacePoints.rateAt(avg, rhr, maxHr, cfg) * elapsedHours * 60
                pacing = PaceBreakdown(points, 0, 0, 0, todayCycle?.maxHeartRate ?: 0)
                estimated = true
            }
        }

        val tonight = sleeps.lastOrNull { it.start.take(10) <= today }
        val sleepHours = tonight?.inBedMs?.let { (it - (tonight.awakeMs ?: 0L)) / 3_600_000.0 }

        val workouts = app.db.dao().workoutsBetween(
            java.time.Instant.ofEpochMilli(dayStart - 90L * 86_400_000L).toString(),
            java.time.Instant.ofEpochMilli(dayStart + 86_400_000L).toString(),
        )
        val zonesByDay: Map<String, List<Long>> = workouts
            .filter { it.hasZoneData }
            .groupBy { it.start.take(10) }
            .mapValues { (_, ws) ->
                (0..5).map { z -> ws.sumOf { it.zoneDurationsMs()[z] ?: 0L } }
            }

        val history = buildHistory(cycles, recoveries, sleeps, samples, rhr, maxHr, cfg, zonesByDay)

        local.update {
            it.copy(
                today = today,
                stability = stability,
                checkinDone = checkin != null,
                pacing = pacing,
                pacingEstimated = estimated,
                pacingFromZones = zoneDerived,
                todayHr = samples.takeLast(180).map { it.bpm },
                pacingDay = buildPacingDay(todaysWorkoutCount, dayStart, rhr, maxHr, cfg, samples),
                activities = insightsFor(todaysWorkouts, rhr, maxHr, cfg),
                activityThresholdBpm = cfg.activityThreshold(rhr),
                exertionThresholdBpm = cfg.exertionThreshold(rhr, maxHr),
                thresholdDays = thresholdHistory(recoveries, maxHr, cfg),
                workoutsToday = todaysWorkoutCount,
                budget = budget,
                sleepHours = sleepHours,
                hrv = todaysRecovery?.hrvRmssd,
                rhr = todaysRecovery?.restingHeartRate,
                history = history,
                maxHeartRate = maxHr,
            )
        }
    }

    /**
     * Builds per-day history purely from what the API gives us: each WHOOP cycle is a day.
     * PacePoints for past days with no stored HR is estimated from the cycle's average HR for the
     * awake fraction of the day — clearly labelled as an estimate in the UI.
     */
    private fun buildHistory(
        cycles: List<CycleEntity>,
        recoveries: List<RecoveryEntity>,
        sleeps: List<SleepEntity>,
        todaySamples: List<HrSample>,
        rhr: Int,
        maxHr: Int,
        cfg: PacingConfig,
        zonesByDay: Map<String, List<Long>> = emptyMap(),
    ): List<DayPoint> {
        val recByCycle = recoveries.associateBy { it.cycleId }
        val sleepByCycle = sleeps.filter { !it.nap }.associateBy { it.cycleId }
        return cycles.takeLast(30).map { cycle ->
            val rec = recByCycle[cycle.id]
            val sleep = sleepByCycle[cycle.id]
            val hrvSeries = recoveries.takeWhile { it.cycleId != cycle.id }.mapNotNull { it.hrvRmssd }
            val rhrSeries = recoveries.takeWhile { it.cycleId != cycle.id }.mapNotNull { it.restingHeartRate?.toDouble() }
            val stability = if (hrvSeries.size >= 2) {
                StabilityScore.compute(
                    StabilityInputs(hrvSeries + listOfNotNull(rec?.hrvRmssd), rhrSeries + listOfNotNull(rec?.restingHeartRate?.toDouble()), null, null),
                    cfg,
                ).score
            } else 3

            val isToday = cycle.start.take(10) == LocalDate.now(zone).toString()
            val zones = zonesByDay[cycle.start.take(10)]
            val points = if (isToday && todaySamples.isNotEmpty()) {
                PacePoints.compute(todaySamples, rhr, maxHr, cfg).points
            } else if (zones != null && zones.any { it > 0 }) {
                // Real measured time-in-zone from the activities logged that day.
                PacePoints.fromZoneDurations(zones, rhr, maxHr, cfg).points
            } else {
                // Estimate: assume the day's mean HR held for the waking hours.
                val awakeHours = ((sleep?.inBedMs ?: 0L) / 3_600_000.0).let { if (it <= 0) 16.0 else 24 - it }
                val avg = cycle.avgHeartRate ?: rhr
                PacePoints.rateAt(avg, rhr, maxHr, cfg) * awakeHours * 60
            }

            DayPoint(
                date = cycle.start.take(10),
                stability = stability,
                hrv = rec?.hrvRmssd,
                rhr = rec?.restingHeartRate,
                pacePoints = points,
                budget = cfg.dailyBudget(stability),
                sleepHours = sleep?.inBedMs?.let { (it - (sleep.awakeMs ?: 0L)) / 3_600_000.0 },
            )
        }
    }

    fun saveCheckin(sleep: Int, symptom: Int, energy: Int?, note: String?) {
        viewModelScope.launch {
            app.journal.saveCheckin(LocalDate.now(zone).toString(), sleep, symptom, energy, note)
            recompute()
        }
    }

    fun saveMonthly(month: String, worst: Int, best: Int, medChanged: Boolean, note: String?) {
        viewModelScope.launch {
            app.journal.saveMonthly(month, worst, best, medChanged, note)
        }
    }

    /** Writes a CSV to the app's cache dir and returns a share intent for it. */
    fun exportCsv(): android.content.Intent {
        val dao = app.db.dao()
        val text = kotlinx.coroutines.runBlocking {
            io.github.kevin.visibleclone.ui.logs.CsvExport.build(
                dao.allCycles(), dao.allRecoveries(), dao.allCheckins(), dao.allLogs()
            )
        }
        val file = java.io.File(app.cacheDir, "visible-export-${LocalDate.now()}.csv")
        file.writeText(text)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            app, "${app.packageName}.fileprovider", file
        )
        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun startLive() = HrBroadcastService.start(app)
    fun stopLive() = HrBroadcastService.stop(app)

    fun updateConfig(cfg: PacingConfig) {
        viewModelScope.launch {
            app.settings.saveConfig(cfg)
            recompute()
        }
    }
}
