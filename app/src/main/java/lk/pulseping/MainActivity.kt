package lk.pulseping

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var preferences: SharedPreferences
    private lateinit var powerButton: Button
    private lateinit var serviceStatus: TextView
    private lateinit var pingValue: TextView
    private lateinit var jitterValue: TextView
    private lateinit var lossValue: TextView
    private lateinit var logText: TextView
    private lateinit var targetSpinner: Spinner
    private lateinit var intervalSpinner: Spinner
    private lateinit var customHostInput: EditText
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
            targetSpinner = findViewById(R.id.targetSpinner)
            intervalSpinner = findViewById(R.id.intervalSpinner)
            customHostInput = findViewById(R.id.customHostInput)

            configureSpinners()
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
            "Singapore Gaming Node",
            "Google DNS (8.8.8.8)",
            "Cloudflare (1.1.1.1)",
            "Custom Host"
        )
        val intervals = listOf("Turbo  ·  2 seconds", "Fast  ·  5 seconds", "Balanced  ·  10 seconds")
        val targetAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, targets).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val intervalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        targetSpinner.adapter = targetAdapter
        intervalSpinner.adapter = intervalAdapter

        targetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                customHostInput.visibility = if (position == TARGET_CUSTOM) View.VISIBLE else View.GONE
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
        return when (targetSpinner.selectedItemPosition) {
            0 -> "Singapore Gaming Node" to "sgp-ping.vultr.com"
            1 -> "Google DNS" to "8.8.8.8"
            2 -> "Cloudflare" to "1.1.1.1"
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

        serviceStatus.text = when {
            !running -> "●  MONITOR OFF"
            paused -> "●  PAUSED"
            else -> "●  MONITORING  ·  ${preferences.getString(PingService.KEY_TARGET_NAME, "NODE")}"
        }
        serviceStatus.setTextColor(
            if (running && !paused) getColor(R.color.neon_green) else getColor(R.color.text_secondary)
        )
        pingValue.text = ping ?: "--"
        jitterValue.text = jitter?.let { "$it ms" } ?: "-- ms"
        lossValue.text = "${"%.1f".format(java.util.Locale.US, loss)}%"
        logText.text = log?.takeIf { it.isNotBlank() } ?: "Waiting for monitor to start..."
        powerButton.text = if (running) "STOP" else "START"
        powerButton.contentDescription = if (running) "Stop network monitoring" else "Start network monitoring"
        powerButton.setBackgroundResource(
            if (running) R.drawable.power_button_active else R.drawable.power_button
        )
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
    }
}
