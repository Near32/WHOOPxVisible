package io.github.kevin.visibleclone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import io.github.kevin.visibleclone.ui.DashboardViewModel
import io.github.kevin.visibleclone.ui.checkin.CheckinScreen
import io.github.kevin.visibleclone.ui.history.HistoryScreen
import io.github.kevin.visibleclone.ui.live.LiveScreen
import io.github.kevin.visibleclone.ui.logs.LogsScreen
import io.github.kevin.visibleclone.ui.logs.LogsViewModel
import io.github.kevin.visibleclone.ui.monthly.MonthlyScreen
import io.github.kevin.visibleclone.ui.thresholds.ThresholdsScreen
import io.github.kevin.visibleclone.ui.settings.SettingsScreen
import io.github.kevin.visibleclone.ui.settings.SettingsViewModel
import io.github.kevin.visibleclone.ui.theme.VisibleCloneTheme
import io.github.kevin.visibleclone.ui.today.TodayScreen

class MainActivity : ComponentActivity() {

    private val app: App get() = application as App

    /** OAuth callbacks arrive onNewIntent in singleTask mode; Compose has to observe them. */
    private val incomingIntent = kotlinx.coroutines.flow.MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ask for notification + BLE permissions once; both are needed for live pacing.
        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())

        incomingIntent.value = intent
        setContent {
            VisibleCloneTheme {
                Surface(Modifier.fillMaxSize()) {
                    val currentIntent by incomingIntent.collectAsStateWithLifecycle()
                    AppNav(initialIntent = currentIntent, onIntentHandled = { incomingIntent.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntent.value = intent
    }
}

private sealed interface Screen {
    data object Today : Screen
    data object Checkin : Screen
    data object Live : Screen
    data object History : Screen
    data object Logs : Screen
    data object Settings : Screen
    data object Monthly : Screen
    data object Thresholds : Screen
}

@androidx.compose.runtime.Composable
private fun AppNav(initialIntent: Intent?, onIntentHandled: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as App
    val factory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = when {
                modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(app) as T
                modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(app) as T
                modelClass.isAssignableFrom(LogsViewModel::class.java) -> LogsViewModel(app) as T
                else -> error("Unknown VM $modelClass")
            }
        }
    }
    val dash: DashboardViewModel = viewModel(key = "dash", factory = factory)
    val settingsVm: SettingsViewModel = viewModel(key = "settings", factory = factory)
    val logsVm: LogsViewModel = viewModel(key = "logs", factory = factory)

    val state by dash.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.Today) }
    var todayHr by remember { mutableStateOf<List<io.github.kevin.visibleclone.domain.HrSample>>(emptyList()) }

    // OAuth redirect: visibleclone://oauth?code=...&state=...
    val activity = androidx.compose.ui.platform.LocalContext.current as? MainActivity
    LaunchedEffect(initialIntent) {
        val uri = initialIntent?.data ?: return@LaunchedEffect
        if (uri.scheme == "visibleclone" && uri.host == "oauth") {
            val code = uri.getQueryParameter("code")
            val returnedState = uri.getQueryParameter("state")
            if (code != null) settingsVm.completeAuth(code, returnedState)
            screen = Screen.Settings
        }
        onIntentHandled()
    }

    // Collect today's HR for the log cost calculation when the Logs screen is shown.
    LaunchedEffect(screen) {
        if (screen == Screen.Logs) {
            val start = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            todayHr = app.db.dao().hrBetween(start, System.currentTimeMillis())
                .map { io.github.kevin.visibleclone.domain.HrSample(it.timestampMs, it.bpm) }
        }
    }

    Scaffold { padding ->
        val mod = Modifier.padding(padding)
        when (val sc = screen) {
            Screen.Today -> androidx.compose.foundation.layout.Box(mod) {
                TodayScreen(
                    vm = dash, s = state,
                    onOpenCheckin = { screen = Screen.Checkin },
                    onOpenLive = { screen = Screen.Live },
                    onOpenHistory = { screen = Screen.History },
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenLogs = { screen = Screen.Logs },
                    onOpenMonthly = { screen = Screen.Monthly },
                    onOpenThresholds = { screen = Screen.Thresholds },
                )
            }
            Screen.Checkin -> androidx.compose.foundation.layout.Box(mod) {
                CheckinScreen(dash) { screen = Screen.Today }
            }
            Screen.Live -> androidx.compose.foundation.layout.Box(mod) {
                LiveScreen(dash, state) { screen = Screen.Today }
            }
            Screen.History -> androidx.compose.foundation.layout.Box(mod) {
                HistoryScreen(state) { screen = Screen.Today }
            }
            Screen.Logs -> androidx.compose.foundation.layout.Box(mod) {
                LogsScreen(logsVm, dash, state, todayHr) { screen = Screen.Today }
            }
            Screen.Settings -> androidx.compose.foundation.layout.Box(mod) {
                SettingsScreen(settingsVm) { screen = Screen.Today }
            }
            Screen.Monthly -> androidx.compose.foundation.layout.Box(mod) {
                MonthlyScreen(dash, onBack = { screen = Screen.Today }, onSaved = { screen = Screen.Today })
            }
            Screen.Thresholds -> androidx.compose.foundation.layout.Box(mod) {
                ThresholdsScreen(
                    s = state,
                    onBack = { screen = Screen.Today },
                    onOpenSettings = { screen = Screen.Settings },
                )
            }
        }
    }
}
