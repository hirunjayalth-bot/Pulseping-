package lk.pulseping

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.view.View
import android.animation.ValueAnimator
import android.animation.ObjectAnimator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.DrawableCompat
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var preferences: SharedPreferences
    private lateinit var powerButton: Button
    private lateinit var serviceStatus: TextView
    private lateinit var pingValue: TextView
    private lateinit var jitterValue: TextView
    private lateinit var lossValue: TextView
    private lateinit var logText: TextView
    private lateinit var alertBanner: TextView
    private lateinit var pingGraph: PingGraphView
    private lateinit var targetSpinner: Spinner
    private lateinit var ispSpinner: Spinner
    private lateinit var intervalSpinner: Spinner
    private lateinit var customHostInput: EditText
    private var currentSignalColor = Color.TRANSPARENT
    private var colorAnimator: ValueAnimator? = null
    private var alertAnimator: ObjectAnimator? = null
    private var pendingServiceIntent: Intent? = null
    private var screenInitialized = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private val refreshTask = object : Runnable {
        override fun run() {
            refreshMonitorState()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            preferences = getSharedPreferences(PingService.PREFERENCES_NAME, MODE_PRIVATE)
            powerButton = findViewById(R.id.powerButton)
            serviceStatus = findViewById(R.id.serviceStatus)
            pingValue = findViewById(R.id.pingValue)
            jitterValue = findViewById(R.id.jitterValue)
            lossValue = findViewById(R.id.lossValue)
            logText = findViewById(R.id.logText)
            alertBanner = findViewById(R.id.alertBanner)
            pingGraph = findViewById(R.id.pingGraph)
            targetSpinner = findViewById(R.id.targetSpinner)
            ispSpinner = findViewById(R.id.ispSpinner)
            intervalSpinner = findViewById(R.id.intervalSpinner)
            customHostInput = findViewById(R.id.customHostInput)

            configureSpinners()
            configureBottomNavigation()
            powerButton.setOnClickListener { toggleMonitor() }
            refreshMonitorState()
            screenInitialized = true
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to initialize the main screen", exception)
            Toast.makeText(this, "PulsePing could not start. Please reinstall the app.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (screenInitialized) uiHandler.post(refreshTask)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(refreshTask)
        super.onPause()
    }

    private fun configureSpinners() {
        val targets = listOf(
            "Free Fire SG",
            "PUBG",
            "CODM",
            "Custom Host"
        )
        val isps = listOf("Hutch 4G/5G", "Dialog 4G/5G", "Mobitel 4G/5G", "Airtel 4G", "SLT Fiber")
        val intervals = listOf("Turbo  ·  2 seconds", "Fast  ·  5 seconds", "Balanced  ·  10 seconds")
        val targetAdapter = themedAdapter(targets)
        val ispAdapter = themedAdapter(isps)
        val intervalAdapter = themedAdapter(intervals)
        targetSpinner.adapter = targetAdapter
        ispSpinner.adapter = ispAdapter
        intervalSpinner.adapter = intervalAdapter

        targetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                customHostInput.visibility = if (position == TARGET_CUSTOM) View.VISIBLE else View.GONE
            }
        }
    }

    private fun themedAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 14f
                }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(getColor(R.color.text_primary))
                    setBackgroundColor(getColor(R.color.surface))
                    setPadding(16, 14, 16, 14)
                    textSize = 14f
                }
            }
        }.apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun configureBottomNavigation() {
        val scrollView = findViewById<android.widget.ScrollView>(R.id.monitorScroll)
        val tabs = listOf(
            R.id.navPinger to findViewById<View>(R.id.powerButton),
            R.id.navStats to findViewById<View>(R.id.pingGraph),
            R.id.navTips to findViewById<View>(R.id.logText)
        )
        tabs.forEach { (tabId, target) ->
            findViewById<TextView>(tabId).setOnClickListener {
                tabs.forEach { (id, _) ->
                    findViewById<TextView>(id).setTextColor(
                        getColor(if (id == tabId) R.color.crimson else R.color.text_secondary)
                    )
                }
                val location = IntArray(2)
                target.getLocationInWindow(location)
                val scrollLocation = IntArray(2)
                scrollView.getLocationInWindow(scrollLocation)
                scrollView.smoothScrollTo(0, scrollView.scrollY + location[1] - scrollLocation[1] - 18)
            }
        }
    }

    private fun toggleMonitor() {
        if (preferences.getBoolean(PingService.KEY_RUNNING, false)) {
            try {
                startService(Intent(this, PingService::class.java).setAction(PingService.ACTION_STOP))
            } catch (exception: SecurityException) {
                showServiceStartError(exception)
            } catch (exception: IllegalStateException) {
                showServiceStartError(exception)
            }
            return
        }

        val (targetName, host) = selectedTarget() ?: return
        val intervalSeconds = when (intervalSpinner.selectedItemPosition) {
            0 -> 2
            1 -> 5
            else -> 10
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingServiceIntent = createServiceIntent(targetName, host, intervalSeconds)
            try {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            } catch (exception: SecurityException) {
                pendingServiceIntent = null
                showServiceStartError(exception)
            } catch (exception: IllegalStateException) {
                pendingServiceIntent = null
                showServiceStartError(exception)
            }
            return
        }

        startMonitorService(createServiceIntent(targetName, host, intervalSeconds))
    }

    private fun startMonitorService(serviceIntent: Intent) {
        try {
            startForegroundService(serviceIntent)
        } catch (exception: SecurityException) {
            showServiceStartError(exception)
        } catch (exception: IllegalStateException) {
            showServiceStartError(exception)
        }
    }

    private fun showServiceStartError(exception: RuntimeException) {
        Log.e(TAG, "Unable to start the monitor service", exception)
        Toast.makeText(this, "Could not start the monitor. Check notification permission and try again.", Toast.LENGTH_LONG)
            .show()
    }

    private fun createServiceIntent(targetName: String, host: String, intervalSeconds: Int): Intent {
        return Intent(this, PingService::class.java).apply {
            putExtra(PingService.EXTRA_TARGET_NAME, targetName)
            putExtra(PingService.EXTRA_HOST, host)
            putExtra(PingService.EXTRA_INTERVAL_SECONDS, intervalSeconds)
        }
    }

    private fun selectedTarget(): Pair<String, String>? {
        val isp = ispSpinner.selectedItem?.toString() ?: "Network"
        return when (targetSpinner.selectedItemPosition) {
            0 -> "Free Fire SG · $isp" to "sgp-ping.vultr.com"
            1 -> "PUBG · $isp" to "sgp-ping.vultr.com"
            2 -> "CODM · $isp" to "sgp-ping.vultr.com"
            TARGET_CUSTOM -> {
                val host = customHostInput.text.toString().trim().trimEnd('.')
                val validHost = Patterns.DOMAIN_NAME.matcher(host).matches() ||
                    Patterns.IP_ADDRESS.matcher(host).matches()
                if (!validHost) {
                    customHostInput.error = "Enter a valid hostname or IPv4 address"
                    null
                } else {
                    "Custom · $host" to host
                }
            }
            else -> null
        }
    }

    private fun refreshMonitorState() {
        val running = preferences.getBoolean(PingService.KEY_RUNNING, false)
        val paused = preferences.getBoolean(PingService.KEY_PAUSED, false)
        val ping = preferences.getString(PingService.KEY_PING, null)
        val jitter = preferences.getString(PingService.KEY_JITTER, null)
        val loss = preferences.getFloat(PingService.KEY_LOSS, 0f)
        val log = preferences.getString(PingService.KEY_LOG, "")
        val samples = preferences.getString(PingService.KEY_HISTORY, "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
        val hasProbe = !log.isNullOrBlank()
        val highPing = ping?.toLongOrNull()?.let { it > HIGH_PING_MILLIS } ?: false
        val signalDrop = running && !paused && hasProbe && (ping == null || highPing)
        val healthy = running && !paused && ping != null && !highPing
        val targetColor = getColor(if (healthy) R.color.neon_green else if (signalDrop) R.color.danger else R.color.crimson)

        serviceStatus.text = when {
            signalDrop -> "●  SIGNAL DROP"
            healthy -> "●  ONLINE"
            !running -> "●  STANDBY"
            paused -> "●  PAUSED"
            else -> "●  PROBING"
        }
        serviceStatus.setTextColor(targetColor)
        pingValue.text = ping ?: "--"
        pingValue.setTextColor(targetColor)
        jitterValue.text = jitter?.let { "$it ms" } ?: "-- ms"
        lossValue.text = "${"%.1f".format(java.util.Locale.US, loss)}%"
        logText.text = log?.takeIf { it.isNotBlank() } ?: "Waiting for monitor to start..."
        powerButton.text = if (running) "STOP" else "START"
        powerButton.contentDescription = if (running) "Stop network monitoring" else "Start network monitoring"
        powerButton.setCompoundDrawablesWithIntrinsicBounds(null, tintedHeartbeat(targetColor), null, null)
        pingGraph.setSamples(samples)
        alertBanner.text = if (ping == null) "SIGNAL DROP / TIMEOUT!" else "SIGNAL DROP / HIGH PING!"
        updateAlertBanner(signalDrop)
        animateSignalColor(targetColor)
    }

    private fun updateAlertBanner(signalDrop: Boolean) {
        if (signalDrop && alertBanner.visibility != View.VISIBLE) {
            alertBanner.visibility = View.VISIBLE
            alertAnimator?.cancel()
            alertAnimator = ObjectAnimator.ofFloat(alertBanner, View.ALPHA, 1f, 0.3f).apply {
                duration = ALERT_FLASH_MILLIS
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        } else if (!signalDrop && alertBanner.visibility == View.VISIBLE) {
            alertAnimator?.cancel()
            alertAnimator = null
            alertBanner.alpha = 1f
            alertBanner.visibility = View.GONE
        }
    }

    private fun tintedHeartbeat(color: Int): android.graphics.drawable.Drawable? {
        val icon = getDrawable(R.drawable.ic_heartbeat)?.mutate() ?: return null
        DrawableCompat.setTint(icon, color)
        return icon
    }

    private fun animateSignalColor(targetColor: Int) {
        val startColor = if (currentSignalColor == Color.TRANSPARENT) targetColor else currentSignalColor
        if (startColor == targetColor) {
            applySignalColor(targetColor)
            return
        }
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofArgb(startColor, targetColor).apply {
            duration = COLOR_TRANSITION_MILLIS
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                currentSignalColor = color
                applySignalColor(color)
            }
            start()
        }
    }

    private fun applySignalColor(color: Int) {
        currentSignalColor = color
        powerButton.setTextColor(color)
        powerButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (color == getColor(R.color.neon_green)) 0xFF071A12.toInt() else 0xFF1A080D.toInt())
            setStroke((3 * resources.displayMetrics.density).toInt(), color)
        }
        pingGraph.setSignalColor(color)
        serviceStatus.setTextColor(color)
        serviceStatus.background = GradientDrawable().apply {
            setColor(getColor(R.color.surface))
            cornerRadius = 12f * resources.displayMetrics.density
            setStroke((1 * resources.displayMetrics.density).toInt(), color)
        }
        pingValue.setTextColor(color)
        powerButton.setCompoundDrawablesWithIntrinsicBounds(null, tintedHeartbeat(color), null, null)
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            val serviceIntent = pendingServiceIntent
            pendingServiceIntent = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                serviceIntent?.let { startMonitorService(it) }
            } else {
                Toast.makeText(this, "Allow notifications to start the persistent monitor.", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    companion object {
        private const val TAG = "PulsePing"
        private const val REQUEST_NOTIFICATIONS = 1001
        private const val TARGET_CUSTOM = 3
        private const val HIGH_PING_MILLIS = 220L
        private const val COLOR_TRANSITION_MILLIS = 260L
        private const val ALERT_FLASH_MILLIS = 450L
    }
}
