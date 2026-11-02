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
    val budget: Double = 14.0,
    val config: PacingConfig = PacingConfig(),
    val sleepHours: Double? = null,
    val hrv: Double? = null,
    val rhr: Int? = null,
    val history: List<DayPoint> = emptyList(),
    val hr: HrState = HrState(),
    val connectedToWhoop: Boolean = false,
    val syncError: String? = null,
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
                    .onFailure { e -> local.update { it.copy(syncError = e.message) } }
            }
            recompute()
            local.update { it.copy(syncing = false) }
        }
    }

    private suspend fun recompute() {
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

        val pacing = PacePoints.compute(samples, rhr, maxHr, cfg)

        val tonight = sleeps.lastOrNull { it.start.take(10) <= today }
        val sleepHours = tonight?.inBedMs?.let { (it - (tonight.awakeMs ?: 0L)) / 3_600_000.0 }

        val history = buildHistory(cycles, recoveries, sleeps, samples, rhr, maxHr, cfg)

        local.update {
            it.copy(
                today = today,
                stability = stability,
                checkinDone = checkin != null,
                pacing = pacing,
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
            val points = if (isToday && todaySamples.isNotEmpty()) {
                PacePoints.compute(todaySamples, rhr, maxHr, cfg).points
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
