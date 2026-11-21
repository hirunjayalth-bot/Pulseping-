package lk.pulseping

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class PingService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var workerThread: HandlerThread
    private lateinit var workerHandler: Handler
    private lateinit var notificationManager: NotificationManager
    private val recentLines = ArrayDeque<String>()

    @Volatile
    private var paused = false

    private var targetName = "Network node"
    private var host = "8.8.8.8"
    private var intervalMillis = 5000L
    private var sampleCount = 0
    private var failedCount = 0
    private var lastSuccessfulPing: Long? = null
    private var jitterTotal = 0L
    private var jitterSamples = 0
    private var jitterAverage: Long? = null
    private var lastPingText: String? = null
    private var initialized = false
    private val preferences by lazy {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        try {
            notificationManager = checkNotNull(getSystemService(NotificationManager::class.java)) {
                "Notification service is unavailable"
            }
            workerThread = HandlerThread("PulsePingProbe").apply { start() }
            workerHandler = Handler(workerThread.looper)
            createNotificationChannel()
            initialized = true
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to initialize the monitor service", exception)
            if (::workerThread.isInitialized) workerThread.quitSafely()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!initialized) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                paused = true
                workerHandler.removeCallbacksAndMessages(null)
                preferences.edit().putBoolean(KEY_PAUSED, true).apply()
                updateNotification()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                paused = false
                preferences.edit().putBoolean(KEY_PAUSED, false).apply()
                updateNotification()
                scheduleProbe(0L)
                return START_NOT_STICKY
            }
        }

        targetName = intent?.getStringExtra(EXTRA_TARGET_NAME) ?: targetName
        host = intent?.getStringExtra(EXTRA_HOST) ?: host
        intervalMillis = (intent?.getIntExtra(EXTRA_INTERVAL_SECONDS, 5) ?: 5).coerceIn(2, 10) * 1000L
        paused = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Notification permission is not granted; refusing to start the foreground monitor")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        preferences.edit()
            .putBoolean(KEY_RUNNING, true)
            .putBoolean(KEY_PAUSED, false)
            .putString(KEY_TARGET_NAME, targetName)
            .putString(KEY_PING, null)
            .putString(KEY_JITTER, null)
            .putFloat(KEY_LOSS, 0f)
            .putString(KEY_LOG, "")
            .apply()

        try {
            startAsForeground()
            updateNotification()
            scheduleProbe(0L)
        } catch (exception: SecurityException) {
            handleStartupFailure(startId, exception)
        } catch (exception: IllegalStateException) {
            handleStartupFailure(startId, exception)
        }
        return START_NOT_STICKY
    }

    private fun handleStartupFailure(startId: Int, exception: RuntimeException) {
        Log.e(TAG, "Unable to start the foreground monitor", exception)
        preferences.edit().putBoolean(KEY_RUNNING, false).putBoolean(KEY_PAUSED, false).apply()
        stopSelf(startId)
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun scheduleProbe(delayMillis: Long) {
        workerHandler.removeCallbacksAndMessages(null)
        val task = object : Runnable {
            override fun run() {
                if (paused) return
                val startedAt = SystemClock.elapsedRealtime()
                val reachable = try {
                    InetAddress.getByName(host).isReachable(PROBE_TIMEOUT_MILLIS.toInt())
                } catch (_: IOException) {
                    false
                }
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (!paused) {
                    recordProbe(reachable, elapsed)
                    mainHandler.post { updateNotification() }
                    workerHandler.postDelayed(this, intervalMillis)
                }
            }
        }
        workerHandler.postDelayed(task, delayMillis)
    }

    private fun recordProbe(reachable: Boolean, elapsedMillis: Long) {
        sampleCount++
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = if (reachable) {
            val ping = elapsedMillis.coerceAtLeast(1L)
            lastSuccessfulPing?.let {
                jitterTotal += kotlin.math.abs(ping - it)
                jitterSamples++
            }
            lastSuccessfulPing = ping
            jitterAverage = if (jitterSamples == 0) 0L else jitterTotal / jitterSamples
            lastPingText = ping.toString()
            "[$timestamp]  $host  ${ping} ms  OK"
        } else {
            failedCount++
            lastPingText = null
            "[$timestamp]  $host  TIMEOUT"
        }

        recentLines.addLast(line)
        while (recentLines.size > MAX_LOG_LINES) recentLines.removeFirst()

        val packetLoss = failedCount * 100f / sampleCount
        preferences.edit()
            .putString(KEY_PING, lastPingText)
            .putString(KEY_JITTER, jitterAverage?.toString())
            .putFloat(KEY_LOSS, packetLoss)
            .putString(KEY_LOG, recentLines.joinToString("\n"))
            .apply()
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val isPaused = paused
        val ping = preferences.getString(KEY_PING, null)
        val loss = preferences.getFloat(KEY_LOSS, 0f)
        val hasProbe = !preferences.getString(KEY_LOG, "").isNullOrBlank()
        val state = when {
            isPaused -> "Paused"
            loss >= UNSTABLE_LOSS_PERCENT -> "Unstable · ${loss.toInt()}% loss"
            !hasProbe -> "Waiting for first probe"
            ping == null -> "No response · ${loss.toInt()}% loss"
            else -> "Stable · $ping ms"
        }
        val toggleAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val toggleLabel = if (isPaused) "RESUME" else "PAUSE"

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("PulsePing LK · $targetName")
            .setContentText(state)
            .setStyle(Notification.BigTextStyle().bigText("$state · $host"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(activityPendingIntent())
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                toggleLabel,
                servicePendingIntent(toggleAction, if (isPaused) 2 else 1)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP",
                servicePendingIntent(ACTION_STOP, 3)
            )
            .build()
    }

    private fun activityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PingService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent ping and stability status"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        paused = true
        if (::workerHandler.isInitialized) workerHandler.removeCallbacksAndMessages(null)
        if (::workerThread.isInitialized) workerThread.quitSafely()
        preferences.edit()
            .putBoolean(KEY_RUNNING, false)
            .putBoolean(KEY_PAUSED, false)
            .apply()
        if (::notificationManager.isInitialized) {
            try {
                notificationManager.cancel(NOTIFICATION_ID)
            } catch (exception: SecurityException) {
                Log.w(TAG, "Unable to dismiss the monitor notification", exception)
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PulsePingService"
        const val PREFERENCES_NAME = "pulseping_monitor"
        const val KEY_RUNNING = "running"
        const val KEY_PAUSED = "paused"
        const val KEY_TARGET_NAME = "target_name"
        const val KEY_PING = "ping"
        const val KEY_JITTER = "jitter"
        const val KEY_LOSS = "loss"
        const val KEY_LOG = "log"

        const val EXTRA_TARGET_NAME = "target_name"
        const val EXTRA_HOST = "host"
        const val EXTRA_INTERVAL_SECONDS = "interval_seconds"

        const val ACTION_PAUSE = "lk.pulseping.action.PAUSE"
        const val ACTION_RESUME = "lk.pulseping.action.RESUME"
        const val ACTION_STOP = "lk.pulseping.action.STOP"

        private const val CHANNEL_ID = "pulseping_monitor"
        private const val NOTIFICATION_ID = 2417
        private const val PROBE_TIMEOUT_MILLIS = 1500L
        private const val MAX_LOG_LINES = 12
        private const val UNSTABLE_LOSS_PERCENT = 20f
    }
}
