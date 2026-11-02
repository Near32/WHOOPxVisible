package io.github.kevin.visibleclone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kevin.visibleclone.App
import io.github.kevin.visibleclone.data.health.HealthConnectSource
import io.github.kevin.visibleclone.data.local.BodyMeasurement
import io.github.kevin.visibleclone.data.local.WhoopTokens
import io.github.kevin.visibleclone.domain.PacingConfig
import io.github.kevin.visibleclone.domain.ZoneCalibrator
import io.github.kevin.visibleclone.data.whoop.WhoopAuthClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val connected: Boolean = false,
    val profileName: String? = null,
    val body: BodyMeasurement? = null,
    val config: PacingConfig = PacingConfig(),
    val hcAvailability: HealthConnectSource.Availability = HealthConnectSource.Availability.UNAVAILABLE,
    val hcGranted: Boolean = false,
    val message: String? = null,
    val busy: Boolean = false,
    val calibration: ZoneCalibrator.Result? = null,
    val zoneBounds: List<Pair<Int, Int>> = emptyList(),
)

class SettingsViewModel(private val app: App) : ViewModel() {

    private val local = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = local

    /** PKCE verifier for the in-flight authorization. */
    var pendingVerifier: String? = null
        private set
    var pendingState: String? = null
        private set

    private val auth = WhoopAuthClient()

    init {
        viewModelScope.launch {
            app.settings.config.collect { c -> local.value = local.value.copy(config = c) }
        }
        viewModelScope.launch {
            app.settings.profileName.collect { n -> local.value = local.value.copy(profileName = n) }
        }
        viewModelScope.launch {
            app.settings.body.collect { b -> local.value = local.value.copy(body = b) }
        }
        viewModelScope.launch {
            app.settings.tokens.collect { t -> local.value = local.value.copy(connected = t != null) }
        }
        viewModelScope.launch { refreshHealthConnect() }
    }

    fun beginAuth(): String {
        val pkce = auth.newPkce()
        val state = auth.newState()
        pendingVerifier = pkce.verifier
        pendingState = state
        return auth.authorizationUrl(state, pkce)
    }

    /** Called from the deep-link handler. Returns an error message or null. */
    fun completeAuth(code: String, returnedState: String?) {
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, message = null)
            if (returnedState != null && returnedState != pendingState) {
                local.value = local.value.copy(busy = false, message = "Sign-in state mismatch — try again")
                return@launch
            }
            val verifier = pendingVerifier
            if (verifier == null) {
                local.value = local.value.copy(busy = false, message = "No sign-in in progress")
                return@launch
            }
            runCatching {
                val token = auth.exchangeCode(code, verifier)
                app.settings.saveTokens(
                    WhoopTokens(token.accessToken, token.refreshToken, token.expiresAtMs, token.scope)
                )
                app.sync.syncProfile()
                app.sync.sync()
            }.onSuccess {
                local.value = local.value.copy(busy = false, connected = true, message = "Signed in")
            }.onFailure { e ->
                local.value = local.value.copy(busy = false, message = "Sign-in failed: ${e.message}")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            app.settings.clearTokens()
            local.value = local.value.copy(connected = false, profileName = null, body = null, message = "Signed out")
        }
    }

    /**
     * Infers WHOOP's zone boundaries from the workouts already synced, then stores the result in
     * the pacing config so scoring uses the user's own measured zones.
     */
    fun calibrateZones() {
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, message = null)
            runCatching {
                val workouts = app.db.dao().allWorkouts()
                val entries = workouts
                    .filter { it.hasZoneData && (it.maxHeartRate ?: 0) > 0 }
                    .map { w -> w.maxHeartRate!! to w.zoneDurationsMs().map { (it ?: 0L) > 0L } }
                val rhr = app.db.dao().allRecoveries().lastOrNull()?.restingHeartRate ?: 60
                val maxHr = app.settings.currentBody()?.maxHeartRate ?: PacingConfig().fallbackMaxHr
                val result = ZoneCalibrator.calibrate(entries, rhr, maxHr)
                val cfg = app.settings.currentConfig()
                app.settings.saveConfig(cfg.copy(whoopZoneHrr = result.fractions))
                local.value = local.value.copy(
                    config = cfg.copy(whoopZoneHrr = result.fractions),
                    calibration = result,
                    zoneBounds = result.bpmBounds(rhr, maxHr),
                    busy = false,
                    message = "Calibrated from ${result.workoutsUsed} workouts",
                )
            }.onFailure { e ->
                local.value = local.value.copy(busy = false, message = "Calibration failed: ${e.message}")
            }
        }
    }

    fun saveConfig(cfg: PacingConfig) {
        viewModelScope.launch {
            app.settings.saveConfig(cfg)
            local.value = local.value.copy(config = cfg, message = "Settings saved")
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, message = null)
            runCatching { app.sync.sync(); app.sync.syncProfile() }
                .onSuccess { local.value = local.value.copy(busy = false, message = "Synced") }
                .onFailure { e -> local.value = local.value.copy(busy = false, message = "Sync failed: ${e.message}") }
        }
    }

    suspend fun refreshHealthConnect() {
        val avail = app.healthConnect.availability()
        val granted = runCatching { app.healthConnect.grantedPermissions().isNotEmpty() }.getOrDefault(false)
        local.value = local.value.copy(hcAvailability = avail, hcGranted = granted)
    }

    fun clearMessage() { local.value = local.value.copy(message = null) }
}
