package io.github.kevin.visibleclone

import android.app.Application
import io.github.kevin.visibleclone.data.ble.HrBroadcastService
import io.github.kevin.visibleclone.data.health.HealthConnectSource
import io.github.kevin.visibleclone.data.local.AppDatabase
import io.github.kevin.visibleclone.data.local.JournalRepository
import io.github.kevin.visibleclone.data.local.SettingsStore
import io.github.kevin.visibleclone.data.whoop.SyncRepository
import io.github.kevin.visibleclone.data.whoop.WhoopApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled service locator. One user, one process, no DI framework needed.
 */
class App : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var api: WhoopApiClient
        private set
    lateinit var sync: SyncRepository
        private set
    lateinit var healthConnect: HealthConnectSource
        private set
    lateinit var journal: JournalRepository
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.create(this)
        settings = SettingsStore(this)
        api = WhoopApiClient(settings)
        sync = SyncRepository(db.dao(), api, settings)
        healthConnect = HealthConnectSource(this)
        journal = JournalRepository(db.dao())
        SyncWorker.schedule(this)
    }
}
