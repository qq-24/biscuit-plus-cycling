package net.telent.biscuit

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import kotlinx.parcelize.Parcelize
import java.time.Duration
import java.time.Instant
import java.util.*

data class Sensors(
        val speed : SpeedSensor,
        var cadence : CadenceSensor,
        var stride : StrideSensor,
        var heart: HeartSensor,
        var position: PositionSensor
) {
    fun asList(): List<ISensor> {
        return listOf(speed, cadence, heart, stride, position)
    }

    fun close() {
        asList().map { it.close() }
    }

    fun startSearch(context: Context) {
         asList().map { it.startSearch(context, 0) }
    }

    fun timestamp(): Instant {
        return asList().map { s -> s.timestamp }.reduce { a, b -> if(a > b) a else b}
    }

    fun reconnectIfCombined(context: Context) {
        if (speed.isCombinedSensor && speed.state == ISensor.SensorState.PRESENT && cadence.state == ISensor.SensorState.ABSENT) {
            Log.d("sensors", "combined speed ${speed.antDeviceNumber}")
            cadence.startSearch(context, speed.antDeviceNumber!!)
        }
        if (cadence.isCombinedSensor && cadence.state == ISensor.SensorState.PRESENT && speed.state == ISensor.SensorState.ABSENT) {
            Log.d("sensors", "combined cadence ${cadence.antDeviceNumber}")
            speed.startSearch(context, cadence.antDeviceNumber!!)
        }
    }
}

@Parcelize data class SensorSummaries(val entries: List<SensorSummary>) : Parcelable

class BiscuitService : Service() {
    private fun reportSensorStatuses() {
        val payload = SensorSummaries(sensors.asList().map { s -> s.stateReport() })
        Log.d(TAG, "reportSensorStatuses: $payload")

        Intent(INTENT_NAME).also {
            it.setPackage(packageName)
            it.putExtra("sensor_state", payload)
            sendBroadcast(it)
            Log.d(TAG, "broadcast sent: sensor_state")
        }
        sensors.reconnectIfCombined(this)
    }

    private var sensors = Sensors(
            speed = SpeedSensor { s  -> reportSensorStatuses() },
            cadence = CadenceSensor { s  -> reportSensorStatuses() },
            stride = StrideSensor { s  -> reportSensorStatuses() },
            heart = HeartSensor { s  -> reportSensorStatuses() },
            position = PositionSensor { s  -> reportSensorStatuses() })

    private var movingTime: Duration = Duration.ZERO
    @Volatile private var isAutoPaused = false
    @Volatile private var isManualPaused = false
    @Volatile private var lowSpeedStartTime: Instant? = null
    // Cooldown after manual resume to prevent immediate re-pause
    @Volatile private var resumeCooldownUntil: Instant = Instant.EPOCH

    // Consolidated auto-pause state machine (replaces inline logic in updater thread)
    private val autoPauseLogic = AutoPauseLogic()

    // Low-speed alert state machine (mirrors AutoPauseLogic pattern)
    private val lowSpeedAlertLogic = LowSpeedAlertLogic()
    @Volatile private var lowSpeedAlertActive = false

    // 更新线程是否正在运行的标志（线程安全）
    @Volatile private var updaterRunning = false

    // Whether the user has actively started a recording session
    @Volatile private var isRecording = false

    // Pause-aware distance tracking (Requirement 5)
    private var pauseAwareDistance: Double = 0.0
    private var lastDistanceSnapshot: Double = 0.0
    private var wasPausedLastTick: Boolean = false

    // Recording elapsed time (wall-clock, excluding paused periods)
    private var recordingElapsedMs: Long = 0L
    private var lastElapsedTickMs: Long = 0L

    // Wake lock to keep CPU running when screen is off
    private var wakeLock: PowerManager.WakeLock? = null

    // Binder for activities wishing to communicate with this service
    private val binder: IBinder = LocalBinder()

    private val db by lazy {
        BiscuitDatabase.getInstance(this.applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand intent=$intent flags=$flags startId=$startId extras=${intent?.extras?.keySet()}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(CHANNEL_DEFAULT_IMPORTANCE, MAIN_CHANNEL_NAME)
        }

        // START_STICKY restart or null intent: check recording state before continuing
        if (intent == null) {
            Log.d(TAG, "Null intent (likely START_STICKY restart)")
            val prefs = getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
            val recording = prefs.getBoolean("is_recording", false)
            if (!recording) {
                Log.d(TAG, "No active recording, stopping service")
                shutdown()
                return START_NOT_STICKY
            }
            Log.d(TAG, "Active recording found, re-establishing foreground")
            startForeground(ONGOING_NOTIFICATION_ID, buildForegroundNotification())
            return START_STICKY
        }

        // Always call startForeground first to satisfy the 5-second requirement
        // on Android 12+ for ALL intents, including stop_service
        startForeground(ONGOING_NOTIFICATION_ID, buildForegroundNotification())

        when {
            intent.hasExtra("stop_service") -> {
                Log.d(TAG, "stop_service requested")
                Toast.makeText(this, "Stopped recording", Toast.LENGTH_SHORT).show()
                shutdown()
            }
            intent.hasExtra("toggle_pause") -> {
                if (isAutoPaused && !isManualPaused) {
                    // Currently auto-paused: user wants to resume, just clear auto-pause
                    isAutoPaused = false
                    lowSpeedStartTime = null
                    // Give a cooldown so auto-pause doesn't immediately re-trigger
                    resumeCooldownUntil = Instant.now().plusSeconds(10)
                    clearAutoPauseNotification()
                    Log.d(TAG, "Resumed from auto-pause via manual toggle, cooldown until $resumeCooldownUntil")
                } else {
                    // Normal manual pause toggle
                    isManualPaused = !isManualPaused
                    if (isManualPaused) {
                        // Entering manual pause: set cooldown deadline so auto-resume
                        // won't trigger immediately (Requirement 1.3)
                        val pausePrefs = getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
                        val cooldownSec = pausePrefs.getInt("manual_pause_cooldown", 10)
                        autoPauseLogic.manualPauseCooldownUntil = Instant.now().plusSeconds(cooldownSec.toLong())
                        Log.d(TAG, "Manual pause entered, cooldown ${cooldownSec}s until ${autoPauseLogic.manualPauseCooldownUntil}")
                    } else {
                        // Resuming from manual pause (user clicked resume):
                        // directly clear pause state, ignoring any remaining cooldown (Requirement 1.7)
                        isAutoPaused = false
                        lowSpeedStartTime = null
                        resumeCooldownUntil = Instant.now().plusSeconds(10)
                        Log.d(TAG, "Resumed from manual pause, cleared auto-pause, cooldown until $resumeCooldownUntil")
                    }
                }
                getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_manual_paused", isManualPaused)
                    .putBoolean("is_auto_paused", isAutoPaused)
                    .apply()
                Log.d(TAG, "Pause toggle result: manualPaused=$isManualPaused, autoPaused=$isAutoPaused")
            }
            intent.hasExtra("refresh_sensors") -> {
                Log.d(TAG, "refresh_sensors requested")
                sensors.startSearch(this)
            }
            else -> {
                Log.d(TAG, "startForeground requested")
                // 冷启动修复：确保录制资源已初始化
                // 当 onCreate() 因 SharedPreferences.apply() 异步延迟而未能初始化时，
                // 此处作为兜底确保录制正常启动
                ensureRecordingResources()
            }
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String) {
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private var updaterThread: Thread? = null

    /**
     * 创建更新线程的工厂方法。
     * 每次需要启动线程时调用此方法创建新实例（Thread 不能重复 start）。
     */
    private fun createUpdaterThread(): Thread = object: Thread() {
        var lastSensorUpdateTime = Instant.EPOCH
        var lastBroadcastTime = Instant.EPOCH
        var amRunning = true
        fun shutdown() {
            amRunning = false
        }
        override fun run() {
            updaterRunning = true
            try {
                var previousLatitude : Double? = null
                var previousLongitude : Double? = null

                if (isRecording) {
                    db.sessionDao().start(Instant.now())
                }
                while (amRunning) {
                    val latest = sensors.timestamp()
                    val now = Instant.now()
                    val prefs = getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
                    val updateInterval = prefs.getInt("update_interval", 1000)
                    val safeUpdateInterval = if (updateInterval > 0) updateInterval else 1000
                    val sensorUpdated = latest > lastSensorUpdateTime
                    var shouldPersist = false

                    // Read auto-pause settings
                    val autoPauseEnabled = prefs.getBoolean("auto_pause_enabled", false)
                    val autoPauseSpeed = prefs.getFloat("auto_pause_speed", 3f).toDouble()
                    val autoPauseDelay = prefs.getInt("auto_pause_delay", 10) // seconds

                    // Sync recording state from prefs (set by HomeFragment)
                    isRecording = prefs.getBoolean("is_recording", false)

                    // Determine current speed
                    val useGpsSpeed = sensors.speed.state != ISensor.SensorState.PRESENT
                    val currentSpeed = if (useGpsSpeed) sensors.position.speed else sensors.speed.speed

                    // Auto-pause logic — runs EVERY tick, outside sensorUpdated
                    // Sync state to AutoPauseLogic before processing
                    autoPauseLogic.isAutoPaused = isAutoPaused
                    autoPauseLogic.isManualPaused = isManualPaused
                    autoPauseLogic.lowSpeedStartTime = lowSpeedStartTime
                    autoPauseLogic.resumeCooldownUntil = resumeCooldownUntil

                    if (!isRecording) {
                        // Not recording: ensure all pause state is clean
                        isAutoPaused = false
                        lowSpeedStartTime = null
                        autoPauseLogic.isAutoPaused = false
                        autoPauseLogic.lowSpeedStartTime = null
                    } else {
                        val result = autoPauseLogic.processTickFull(
                            autoPauseEnabled = autoPauseEnabled,
                            currentSpeed = currentSpeed,
                            autoPauseSpeed = autoPauseSpeed,
                            autoPauseDelay = autoPauseDelay,
                            now = now
                        )

                        // Sync state back from AutoPauseLogic
                        val prevAutoPaused = isAutoPaused
                        val prevManualPaused = isManualPaused
                        isAutoPaused = autoPauseLogic.isAutoPaused
                        isManualPaused = autoPauseLogic.isManualPaused
                        lowSpeedStartTime = autoPauseLogic.lowSpeedStartTime
                        resumeCooldownUntil = autoPauseLogic.resumeCooldownUntil

                        // Side effects: vibration and notifications on state changes
                        if (result.autoPauseChanged) {
                            triggerStrongVibration()
                            // 持久化自动暂停状态，杀掉 app 重启后可恢复
                            prefs.edit().putBoolean("is_auto_paused", isAutoPaused).apply()
                            if (isAutoPaused) {
                                showAutoPauseNotification(paused = true)
                                Log.d(TAG, "Auto-paused: speed=$currentSpeed < threshold=$autoPauseSpeed")
                            } else {
                                showAutoPauseNotification(paused = false)
                                Log.d(TAG, "Auto-resumed: speed=$currentSpeed >= threshold=$autoPauseSpeed")
                            }
                        }
                        if (result.manualPauseChanged && !isManualPaused) {
                            triggerStrongVibration()
                            showAutoPauseNotification(paused = false)
                            Log.d(TAG, "Auto-resumed from manual pause: speed=$currentSpeed >= threshold=$autoPauseSpeed")
                        }
                    }

                    // Effective pause state: manual OR auto
                    val isPaused = isManualPaused || isAutoPaused

                    // Low-speed alert logic — runs every tick, after auto-pause
                    val lowSpeedAlertEnabled = prefs.getBoolean("low_speed_alert_enabled", false)
                    val lowSpeedAlertThreshold = prefs.getFloat("low_speed_alert_threshold", 10.0f).toDouble()
                    val lowSpeedAlertResult = lowSpeedAlertLogic.processTick(
                        lowSpeedAlertEnabled = lowSpeedAlertEnabled,
                        autoPauseEnabled = autoPauseEnabled,
                        currentSpeed = currentSpeed,
                        lowSpeedThreshold = lowSpeedAlertThreshold,
                        autoPauseSpeed = autoPauseSpeed,
                        isPaused = isPaused,
                        isRecording = isRecording,
                        now = now
                    )
                    lowSpeedAlertActive = lowSpeedAlertResult.shouldAlert

                    // Track recording elapsed time (wall-clock, excluding paused)
                    if (lastElapsedTickMs > 0 && !isPaused) {
                        recordingElapsedMs += System.currentTimeMillis() - lastElapsedTickMs
                    }
                    lastElapsedTickMs = System.currentTimeMillis()
                    // Persist for UI to read on resume
                    prefs.edit()
                        .putLong("accumulated_duration_ms", recordingElapsedMs)
                        .putLong("last_duration_tick_ms", lastElapsedTickMs)
                        .apply()

                    if (sensorUpdated) {
                        // Pause-aware distance tracking (Requirement 5)
                        val rawDistance = if (useGpsSpeed) sensors.position.distance else sensors.speed.distance
                        if (isPaused) {
                            // Paused: record current raw distance snapshot, don't accumulate
                            lastDistanceSnapshot = rawDistance
                        } else {
                            if (wasPausedLastTick) {
                                // Just resumed from pause: reset snapshot, discard pause-period delta
                                lastDistanceSnapshot = rawDistance
                            } else {
                                // Normal running: accumulate delta
                                val delta = rawDistance - lastDistanceSnapshot
                                if (delta > 0) {
                                    pauseAwareDistance += delta
                                    lastDistanceSnapshot = rawDistance
                                }
                            }
                        }
                        wasPausedLastTick = isPaused

                        // Only accumulate moving time if not paused
                        if (!isPaused && currentSpeed > 1.0 && lastSensorUpdateTime > Instant.EPOCH) {
                            val elapsed = Duration.between(lastSensorUpdateTime, latest)
                            movingTime = movingTime.plus(elapsed)
                        }
                        val latChanged = sensors.position.latitude != previousLatitude
                        val lngChanged = sensors.position.longitude != previousLongitude
                        // Only persist if not paused AND there's actual movement
                        // Use low speed filter from settings if enabled, otherwise persist when speed > 0
                        val lowSpeedFilterEnabled = prefs.getBoolean("gps_low_speed_filter", true)
                        val lowSpeedThreshold = if (lowSpeedFilterEnabled) prefs.getFloat("gps_low_speed_threshold", 3f).toDouble() else 0.0
                        shouldPersist = isRecording && !isPaused && currentSpeed > lowSpeedThreshold
                        lastSensorUpdateTime = latest
                        previousLatitude = sensors.position.latitude
                        previousLongitude = sensors.position.longitude
                    }

                    val shouldBroadcast = sensorUpdated ||
                        lastBroadcastTime == Instant.EPOCH ||
                        Duration.between(lastBroadcastTime, now).toMillis() >= safeUpdateInterval

                    if (shouldBroadcast) {
                        Log.d(TAG, "update tick sensorUpdated=$sensorUpdated persist=$shouldPersist intervalMs=$safeUpdateInterval latest=$latest autoPaused=$isAutoPaused")
                        logUpdate(now, shouldPersist)
                        lastBroadcastTime = now
                    }

                    sleep(safeUpdateInterval.toLong())
                }
            } finally {
                updaterRunning = false
            }
        }
    }

    /**
     * 启动更新线程：检查是否已在运行，若未运行则创建并启动新线程。
     */
    private fun startUpdaterThread() {
        if (updaterRunning) return
        updaterThread = createUpdaterThread()
        updaterThread?.start()
    }

    /**
     * 确保录制所需的资源已初始化。
     * 如果更新线程已在运行则直接返回（幂等）。
     * 从 SharedPreferences 读取 isRecording，若未在录制则直接返回。
     * 否则获取 WakeLock、检查权限、启动传感器搜索、启动更新线程。
     */
    private fun ensureRecordingResources() {
        if (updaterRunning) return  // 已在运行，不重复初始化

        val prefs = getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
        isRecording = prefs.getBoolean("is_recording", false)
        if (!isRecording) return

        // 获取 WakeLock
        if (wakeLock == null || wakeLock?.isHeld != true) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "biscuit:recording").apply {
                acquire(12 * 60 * 60 * 1000L)
            }
        }
        Log.d(TAG, "WakeLock acquired")

        // 检查位置权限
        Log.d(TAG, "Location permission check fine=${ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)}")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            sensors.position.state = ISensor.SensorState.FORBIDDEN
        }

        // 启动传感器搜索
        Log.d(TAG, "startSearch sensors")
        sensors.startSearch(this)

        // 启动更新线程
        Log.d(TAG, "startUpdaterThread")
        startUpdaterThread()
    }

    override fun onCreate() {
        Log.d(TAG, "Service started $INTENT_NAME")
        super.onCreate()

        // Restore states from prefs FIRST to gate resource acquisition
        val prefs = getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
        isManualPaused = prefs.getBoolean("is_manual_paused", false)
        isAutoPaused = prefs.getBoolean("is_auto_paused", false)
        isRecording = prefs.getBoolean("is_recording", false)
        recordingElapsedMs = prefs.getLong("accumulated_duration_ms", 0L)
        lastElapsedTickMs = 0L  // Will be set on first tick
        Log.d(TAG, "Restored isRecording=$isRecording recordingElapsedMs=$recordingElapsedMs")

        // 使用 ensureRecordingResources() 统一处理录制资源初始化
        // 该方法内部会重新从 prefs 读取 isRecording 并判断是否需要初始化
        ensureRecordingResources()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        Log.d(TAG, "onTaskRemoved called — service continues running")
        super.onTaskRemoved(rootIntent)
        // Don't shutdown: keep recording even if user swipes app from recents
    }

    private fun shutdown() {
        Log.d(TAG, "shutdown begin")
        isRecording = false
        // Clear manual pause and auto-pause state on stop
        getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_manual_paused", false)
            .putBoolean("is_auto_paused", false)
            .apply()
        // Clear auto-pause notification
        clearAutoPauseNotification()
        // Release wake lock
        try { wakeLock?.release(); wakeLock = null } catch (_: Exception) {}
        stopForeground(true)
        stopSelf()
        sensors.close()
        updaterThread?.interrupt()
        updaterThread = null
        updaterRunning = false
        Log.d(TAG, "shutdown complete")
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
        try { wakeLock?.release(); wakeLock = null } catch (_: Exception) {}
        sensors.close()
        updaterThread?.interrupt()
        updaterThread = null
        updaterRunning = false
    }

    private fun logUpdate(timestamp: Instant, persist: Boolean) {
        val useGpsSpeed = sensors.speed.state != ISensor.SensorState.PRESENT
        val displaySpeed = if (useGpsSpeed) sensors.position.speed else sensors.speed.speed
        val displayDistance = pauseAwareDistance
        Log.d(TAG, "compose trackpoint useGpsSpeed=$useGpsSpeed speed=$displaySpeed cadence=${sensors.cadence.cadence} distance=$displayDistance lat=${sensors.position.latitude} lng=${sensors.position.longitude}")
        
        val tp = Trackpoint(
                timestamp = timestamp,
                lng = sensors.position.longitude,
                lat = sensors.position.latitude,
                speed = displaySpeed.toFloat(),
                cadence = sensors.cadence.cadence.toFloat(),
                movingTime = movingTime,
                distance = displayDistance.toFloat()
        )
        if (persist) {
            db.trackpointDao().addPoint(tp)
            Log.d(TAG, "recording: $tp")
        } else {
            Log.d(TAG, "heartbeat: $tp")
        }

        val i = Intent(INTENT_NAME)
        i.setPackage(packageName)
        i.putExtra("trackpoint", tp)
        i.putExtra("auto_paused", isAutoPaused)
        i.putExtra("manual_paused", isManualPaused)
        i.putExtra("is_paused", isManualPaused || isAutoPaused)
        i.putExtra("low_speed_alert", lowSpeedAlertActive)
        sendBroadcast(i)
        Log.d(TAG, "broadcast sent: trackpoint=$tp")
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /**
     * Get the services for communicating with it
     */
    inner class LocalBinder : Binder() {
        val service: BiscuitService
            get() = this@BiscuitService
    }

    private val vibrationHelper: VibrationHelper by lazy {
        VibrationHelper(getSystemService(Context.VIBRATOR_SERVICE) as Vibrator, this)
    }

    private fun triggerStrongVibration() {
        vibrationHelper.triggerAutoAlert()
    }

    /** Build the ongoing foreground notification (no action buttons, not clearable). */
    private fun buildForegroundNotification(): Notification {
        val notifyPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this.applicationContext, BikeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_DEFAULT_IMPORTANCE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_recording))
            .setContentIntent(notifyPendingIntent)
            .setSmallIcon(R.drawable.ic_chaindodger)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()
    }

    /** Show an ongoing notification for auto-pause/resume events. */
    private fun showAutoPauseNotification(paused: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(CHANNEL_DEFAULT_IMPORTANCE, MAIN_CHANNEL_NAME)
        }
        val notifyPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this.applicationContext, BikeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // 使用字符串资源替代硬编码文本，支持国际化
        val text = if (paused) getString(R.string.notification_auto_paused) else getString(R.string.notification_auto_resumed)
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_DEFAULT_IMPORTANCE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(notifyPendingIntent)
            .setSmallIcon(R.drawable.ic_chaindodger)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(AUTO_PAUSE_NOTIFICATION_ID, notification)
    }

    /** Clear the auto-pause notification. */
    private fun clearAutoPauseNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(AUTO_PAUSE_NOTIFICATION_ID)
    }

    companion object {
        private val TAG = BiscuitService::class.java.simpleName
        const val INTENT_NAME = BuildConfig.APPLICATION_ID + ".TRACKPOINTS"
        private const val ONGOING_NOTIFICATION_ID = 9999
        private const val AUTO_PAUSE_NOTIFICATION_ID = 9998
        private const val CHANNEL_DEFAULT_IMPORTANCE = "csc_ble_channel"
        private const val MAIN_CHANNEL_NAME = "CscService"
    }
}
