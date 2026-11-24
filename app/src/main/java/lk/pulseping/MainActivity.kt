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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK

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
    private lateinit var intervalSpinner: Spinner
    private lateinit var customHostInput: EditText
    private lateinit var pulseRadar: PulseRadarView
    private lateinit var bannerAd: View
    private lateinit var tabContent: android.widget.FrameLayout
    private lateinit var suitePages: SuitePages
    private var selectedTab = TAB_PINGER
    private var lastAnimatedPing: String? = null
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
            StartAppSDK.initParams(this, START_APP_ID).setReturnAdsEnabled(false).init()
            setContentView(R.layout.activity_main)

            val rootContainer = findViewById<View>(R.id.rootContainer)
            ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, windowInsets ->
                val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(
                    view.paddingLeft,
                    systemBars.top,
                    view.paddingRight,
                    systemBars.bottom
                )
                windowInsets
            }
            ViewCompat.requestApplyInsets(rootContainer)

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
            intervalSpinner = findViewById(R.id.intervalSpinner)
            customHostInput = findViewById(R.id.customHostInput)
            pulseRadar = findViewById(R.id.pulseRadar)
            bannerAd = findViewById(R.id.startAppBanner)
            tabContent = findViewById(R.id.tabContent)

            configureSpinners()
            configureBottomNavigation()
            powerButton.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                it.animate().scaleX(0.94f).scaleY(0.94f).setDuration(65L).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
                }.start()
                toggleMonitor()
            }
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
        if (screenInitialized) {
            pulseRadar.setActive(false)
        }
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun configureSpinners() {
        val targets = listOf(
            "Free Fire Server  ·  ff.garena.com",
            "PUBG Mobile Node  ·  sgp-1.valve.net",
            "Hutch 4G/5G Stabilizer  ·  1.1.1.1",
            "Dialog 4G/5G Stabilizer  ·  8.8.8.8",
            "Mobitel 4G/5G Stabilizer  ·  9.9.9.9",
            "Custom host"
        )
        val intervals = listOf("Fast  ·  2 seconds", "Balanced  ·  5 seconds", "Relaxed  ·  10 seconds")
        val targetAdapter = themedAdapter(targets)
        val intervalAdapter = themedAdapter(intervals)
        targetSpinner.adapter = targetAdapter
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
        bannerAd.visibility = View.GONE
        suitePages = SuitePages(this)
        tabContent.addView(suitePages.sensiPage)
        tabContent.addView(suitePages.toolsPage)
        listOf(
            R.id.navPinger to TAB_PINGER,
            R.id.navSensi to TAB_SENSI,
            R.id.navTools to TAB_TOOLS
        ).forEach { (viewId, tab) ->
            findViewById<TextView>(viewId).setOnClickListener { selectTab(tab) }
        }
    }

    private fun selectTab(tab: Int) {
        if (selectedTab == tab) return
        selectedTab = tab
        val pages = listOf(
            findViewById<View>(R.id.monitorScroll),
            suitePages.sensiPage,
            suitePages.toolsPage
        )
        pages.forEachIndexed { index, page ->
            val selected = index == tab
            page.visibility = if (selected) View.VISIBLE else View.GONE
            if (selected) {
                page.alpha = 0f
                page.translationY = 9f * resources.displayMetrics.density
                page.animate().alpha(1f).translationY(0f).setDuration(180L).start()
            }
        }
        listOf(R.id.navPinger, R.id.navSensi, R.id.navTools).forEachIndexed { index, id ->
            findViewById<TextView>(id).setTextColor(
                getColor(if (index == tab) R.color.crimson else R.color.text_secondary)
            )
        }
        bannerAd.visibility = if (tab == TAB_PINGER) View.GONE else View.VISIBLE

        if (tab == TAB_SENSI || tab == TAB_TOOLS) maybeShowInterstitial()
    }

    private fun maybeShowInterstitial() {
        val now = System.currentTimeMillis()
        val lastShown = getPreferences(MODE_PRIVATE).getLong(KEY_LAST_START_APP_AD, 0L)
        if (now - lastShown < INTERSTITIAL_COOLDOWN_MILLIS) return

        StartAppAd.showAd(this)
        getPreferences(MODE_PRIVATE).edit().putLong(KEY_LAST_START_APP_AD, now).apply()
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
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
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
        return when (targetSpinner.selectedItemPosition) {
            0 -> "Free Fire Server" to "ff.garena.com"
            1 -> "PUBG Mobile Node" to "sgp-1.valve.net"
            2 -> "Hutch 4G/5G Stabilizer" to "1.1.1.1"
            3 -> "Dialog 4G/5G Stabilizer" to "8.8.8.8"
            4 -> "Mobitel 4G/5G Stabilizer" to "9.9.9.9"
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
            signalDrop -> "●  CONNECTION ISSUE"
            healthy -> "●  ONLINE"
            !running -> "●  STANDBY"
            paused -> "●  PAUSED"
            else -> "●  PROBING"
        }
        serviceStatus.setTextColor(targetColor)
        animatePingValue(ping)
        pingValue.setTextColor(targetColor)
        jitterValue.text = jitter?.let { "$it ms" } ?: "-- ms"
        lossValue.text = "${"%.1f".format(java.util.Locale.US, loss)}%"
        logText.text = log?.takeIf { it.isNotBlank() } ?: "Waiting for monitor to start..."
        powerButton.text = if (running) "STOP" else "START"
        powerButton.contentDescription = if (running) "Stop network monitoring" else "Start network monitoring"
        powerButton.setCompoundDrawablesWithIntrinsicBounds(null, tintedHeartbeat(Color.WHITE), null, null)
        pingGraph.setSamples(samples)
        alertBanner.text = if (ping == null) "HOST NOT RESPONDING" else "HIGH RESPONSE TIME"
        updateAlertBanner(signalDrop)
        animateSignalColor(targetColor)
        pulseRadar.setActive(running && !paused)
    }

    private fun animatePingValue(ping: String?) {
        if (ping == null) {
            pingValue.text = "--"
            lastAnimatedPing = null
            return
        }
        if (lastAnimatedPing == ping) return
        val destination = ping.toIntOrNull()
        val start = lastAnimatedPing?.toIntOrNull()
        lastAnimatedPing = ping
        if (destination == null || start == null) {
            pingValue.text = ping
            return
        }
        ValueAnimator.ofInt(start, destination).apply {
            duration = 320L
            addUpdateListener { pingValue.text = (it.animatedValue as Int).toString() }
            start()
        }
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
        powerButton.setTextColor(Color.WHITE)
        powerButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (color == getColor(R.color.neon_green)) 0xFF071A12.toInt() else 0xFF1A080D.toInt())
            setStroke((3 * resources.displayMetrics.density).toInt(), color)
        }
        pingGraph.setSignalColor(color)
        pulseRadar.setSignalColor(color)
        serviceStatus.setTextColor(color)
        serviceStatus.background = GradientDrawable().apply {
            setColor(getColor(R.color.surface))
            cornerRadius = 12f * resources.displayMetrics.density
            setStroke((1 * resources.displayMetrics.density).toInt(), color)
        }
        pingValue.setTextColor(color)
        powerButton.setCompoundDrawablesWithIntrinsicBounds(null, tintedHeartbeat(Color.WHITE), null, null)
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
        private const val TARGET_CUSTOM = 5
        private const val TAB_PINGER = 0
        private const val TAB_SENSI = 1
        private const val TAB_TOOLS = 2
        private const val START_APP_ID = "210661457"
        private const val KEY_LAST_START_APP_AD = "last_start_app_ad_shown_at"
        private const val INTERSTITIAL_COOLDOWN_MILLIS = 45_000L
        private const val HIGH_PING_MILLIS = 220L
        private const val COLOR_TRANSITION_MILLIS = 260L
        private const val ALERT_FLASH_MILLIS = 450L
    }
}
