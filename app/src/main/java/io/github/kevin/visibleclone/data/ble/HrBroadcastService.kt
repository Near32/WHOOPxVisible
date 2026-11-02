package io.github.kevin.visibleclone.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.kevin.visibleclone.MainActivity
import io.github.kevin.visibleclone.R
import io.github.kevin.visibleclone.data.local.AppDatabase
import io.github.kevin.visibleclone.data.local.HrSampleEntity
import io.github.kevin.visibleclone.data.local.SettingsStore
import io.github.kevin.visibleclone.domain.HrSample
import io.github.kevin.visibleclone.domain.PacingAlerter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Connects to the WHOOP strap's standard Bluetooth SIG Heart Rate service while the user has
 * "Broadcast Heart Rate" enabled in the WHOOP app, then records every reading to Room.
 *
 * The strap advertises 0x180D like any chest strap, so this is plain GATT — no WHOOP SDK exists.
 */
class HrBroadcastService : Service() {

    companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        // Standard Bluetooth SIG Battery Service — exposed by the strap on the same connection.
        val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        private const val CHANNEL_ID = "hr_broadcast"
        private const val ALERT_CHANNEL_ID = "pacing_alerts"
        private const val NOTIFICATION_ID = 42
        private const val ALERT_ID = 43
        private const val TAG = "HrBle"

        const val ACTION_START = "io.github.kevin.visibleclone.START_HR"
        const val ACTION_STOP = "io.github.kevin.visibleclone.STOP_HR"

        /** Set by the service, read by the UI. */
        val state = MutableStateFlow(HrState())

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HrBroadcastService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            // stopService destroys the running instance (running onDestroy). The ACTION_STOP path
            // below is kept for callers that only have an intent.
            context.stopService(Intent(context, HrBroadcastService::class.java))
            state.value = HrState(status = "Live HR off")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeScan: ScanCallback? = null
    /** Backoff for reconnect attempts, so a stubborn strap cannot thrash the radio. */
    private var reconnectAttempts = 0
    private var alerter: PacingAlerter? = null
    private var restingHr = 60
    private var maxHr = 185
    private var gatt: BluetoothGatt? = null
    private var pendingWriteJob: Job? = null
    private val pending = mutableListOf<Pair<Long, Int>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.i(TAG, "onStartCommand action=${intent?.action}")
        loadAlertThresholds()
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                if (gatt == null) scanAndConnect()
                return START_STICKY
            }
        }
    }

    /** Reads the current config + today's RHR once; thresholds don't change mid-session. */
    private fun loadAlertThresholds() {
        scope.launch {
            val settings = SettingsStore(applicationContext)
            val cfg = settings.currentConfig()
            alerter = PacingAlerter(cfg)
            restingHr = AppDatabase.create(applicationContext).dao()
                .allRecoveries().lastOrNull()?.restingHeartRate ?: 60
            maxHr = settings.currentBody()?.maxHeartRate ?: cfg.fallbackMaxHr
        }
    }

    private fun maybeAlert(bpm: Int, nowMs: Long) {
        val a = alerter ?: return
        if (a.onSample(bpm, restingHr, maxHr, nowMs)) {
            notifyPacingAlert(bpm)
            vibrate()
        }
    }

    private fun vibrate() {
        val v = getSystemService(android.os.Vibrator::class.java) ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            v.vibrate(android.os.VibrationEffect.createOneShot(250, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(250)
        }
    }

    private fun notifyPacingAlert(bpm: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        val content = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            ALERT_ID,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("You're in your exertion zone")
                .setContentText("$bpm bpm — ease off for a moment if you can.")
                .setContentIntent(content)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    override fun onDestroy() {
        android.util.Log.i(TAG, "onDestroy")
        stopScanning()
        gatt?.close()
        gatt = null
        flush()

        // Explicitly drop the foreground notification; stopSelf alone can leave it behind on
        // some OEM builds, and a stale "Pacing active" is misleading.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)

        state.value = HrState(status = "Live HR off")
        scope.cancel()
        super.onDestroy()
    }

    // ---------- BLE ----------

    @SuppressLint("MissingPermission")
    private fun scanAndConnect() {
        val granted = { p: String ->
            ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        }
        // Both are required: SCAN finds the strap, CONNECT reads device.name and connectGatt().
        if (!granted(Manifest.permission.BLUETOOTH_SCAN)) {
            state.value = state.value.copy(status = "Bluetooth scan permission missing")
            return
        }
        if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            state.value = state.value.copy(
                status = "Bluetooth connect permission missing — grant it in app settings",
            )
            return
        }
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            state.value = state.value.copy(status = "Bluetooth is off")
            return
        }

        state.value = state.value.copy(status = "Searching for broadcast…")
        val scanner = adapter.bluetoothLeScanner ?: return
        stopScanning()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Never let a permission or race exception escape: it would kill the process.
                runCatching {
                    val name = runCatching { result.device.name }.getOrNull()
                    val uuids = result.scanRecord?.serviceUuids
                    android.util.Log.d(TAG, "scan hit name=$name uuids=$uuids")
                    val hasHrService = uuids?.any { it.uuid == HEART_RATE_SERVICE } == true
                    if (hasHrService && gatt == null) {
                        stopScanning()
                        state.value = state.value.copy(status = "Connecting…", deviceName = name)
                        gatt = result.device.connectGatt(this@HrBroadcastService, false, gattCallback)
                    }
                }.onFailure { e ->
                    android.util.Log.e(TAG, "scan result handling failed", e)
                    state.value = state.value.copy(status = "Scan error: ${e.message}")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                android.util.Log.w(TAG, "onScanFailed $errorCode")
                state.value = state.value.copy(status = "Scan failed ($errorCode)")
            }
        }
        activeScan = callback
        runCatching {
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback,
            )
        }.onFailure { e ->
            state.value = state.value.copy(status = "Could not start scan: ${e.message}")
        }

        // Give the strap a window to be found, then retry later rather than spinning forever.
        scope.launch {
            delay(30_000)
            if (gatt == null) {
                stopScanning()
                state.value = state.value.copy(status = "No broadcast found — toggle Broadcast Heart Rate")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        val cb = activeScan ?: return
        activeScan = null
        runCatching {
            (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
                .adapter?.bluetoothLeScanner?.stopScan(cb)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            android.util.Log.i(TAG, "onConnectionStateChange status=$status newState=$newState")
            when {
                newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    reconnectAttempts = 0
                    state.value = state.value.copy(
                        connected = true,
                        status = "Connected to ${state.value.deviceName ?: "strap"} — discovering services…",
                    )
                    g.discoverServices()
                }
                newState == BluetoothGatt.STATE_DISCONNECTED -> {
                    g.close()
                    gatt = null
                    state.value = state.value.copy(
                        connected = false,
                        bpm = null,
                        status = if (status == BluetoothGatt.GATT_SUCCESS) {
                            "Strap disconnected — searching again"
                        } else {
                            "Connection refused (GATT $status). Is another app holding the strap?"
                        },
                    )
                    reconnectAttempts += 1
                    if (reconnectAttempts > 8) {
                        state.value = state.value.copy(
                            status = "Stopped retrying after $reconnectAttempts attempts",
                        )
                        stopSelf()
                    } else {
                        val waitMs = (2_000L * reconnectAttempts).coerceAtMost(30_000L)
                        state.value = state.value.copy(
                            status = state.value.status + " (retry $reconnectAttempts in ${waitMs / 1000}s)",
                        )
                        scope.launch { delay(waitMs); scanAndConnect() }
                    }
                }
                else -> {
                    // e.g. 133 on some phones when the peripheral rejects the connection.
                    android.util.Log.w(TAG, "unexpected state newState=$newState status=$status")
                    state.value = state.value.copy(
                        connected = false,
                        status = "Connection failed (GATT $status)",
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)
            if (ch == null) {
                val found = g.services?.joinToString { it.uuid.toString().take(8) } ?: "none"
                android.util.Log.w(TAG, "HR service missing; services=$found")
                state.value = state.value.copy(
                    status = "Heart Rate service not found — strap may not be broadcasting",
                )
                g.disconnect()
                return
            }
            g.setCharacteristicNotification(ch, true)
            ch.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)?.let { desc ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(desc)
                }
            }
            android.util.Log.i(TAG, "HR characteristic subscribed; services=${g.services?.size}")
            state.value = state.value.copy(status = "Receiving heart rate")
            readBattery(g)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == BATTERY_LEVEL) {
                onBatteryPayload(value)
            } else {
                onHrPayload(value)
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (characteristic.uuid == BATTERY_LEVEL && status == BluetoothGatt.GATT_SUCCESS) {
                onBatteryPayload(value)
            }
        }

        @Deprecated("Android <13 callback")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            if (characteristic.uuid == BATTERY_LEVEL) onBatteryPayload(value) else onHrPayload(value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readBattery(g: BluetoothGatt) {
        val ch = g.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL) ?: return
        g.readCharacteristic(ch)
    }

    private fun onBatteryPayload(value: ByteArray) {
        if (value.isEmpty()) return
        val pct = value[0].toInt() and 0xFF
        if (pct in 0..100) {
            state.value = state.value.copy(batteryPercent = pct)
        }
    }

    /** Bluetooth SIG HR Measurement: flag bit 0 selects uint8 vs uint16 bpm. */
    private fun onHrPayload(value: ByteArray) {
        if (value.isEmpty()) return
        val flags = value[0].toInt()
        val bpm = if (flags and 0x01 != 0) {
            if (value.size < 3) return
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else {
            value[1].toInt() and 0xFF
        }
        if (bpm in 25..250) {
            val now = System.currentTimeMillis()
            state.value = state.value.copy(bpm = bpm, lastUpdateMs = now)
            maybeAlert(bpm, now)
            pending += now to bpm
            scheduleFlush()
        }
    }

    /** Batch inserts so we aren't writing a row per beat. */
    private fun scheduleFlush() {
        if (pendingWriteJob?.isActive == true) return
        pendingWriteJob = scope.launch {
            delay(5_000)
            flush()
        }
    }

    private fun flush() {
        if (pending.isEmpty()) return
        val rows = pending.map { HrSampleEntity(it.first, "ble", it.second) }
        pending.clear()
        scope.launch {
            val dao = AppDatabase.create(applicationContext).dao()
            dao.insertHr(rows)
            dao.pruneHrBefore(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        }
    }

    // ---------- notification ----------

    @SuppressLint("MissingPermission")
    private fun startForegroundWithNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live heart rate", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Pacing alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        val content = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Pacing active")
            .setContentText("Listening for your WHOOP broadcast")
            .setContentIntent(content)
            .setOngoing(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { e ->
            android.util.Log.e(TAG, "startForeground failed", e)
            state.value = state.value.copy(status = "Foreground service failed: ${e.message}")
        }
    }
}

data class HrState(
    val connected: Boolean = false,
    val bpm: Int? = null,
    val batteryPercent: Int? = null,
    val lastUpdateMs: Long = 0,
    val status: String = "Idle",
    val deviceName: String? = null,
) {
    /** BLE HR notifications arrive ~1/second; older than this and we treat the stream as stale. */
    fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - lastUpdateMs > 15_000
}

/** Convenience for the UI and history. */
fun HrSampleEntity.toDomain() = HrSample(timestampMs, bpm)
