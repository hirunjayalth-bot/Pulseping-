package lk.pulseping

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class PingTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val preferences = getSharedPreferences(PingService.PREFERENCES_NAME, MODE_PRIVATE)
        if (preferences.getBoolean(PingService.KEY_RUNNING, false)) {
            startService(Intent(this, PingService::class.java).setAction(PingService.ACTION_STOP))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Open PulsePing and allow notifications to start monitoring.", Toast.LENGTH_LONG).show()
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            val host = preferences.getString(PingService.KEY_HOST, "8.8.8.8") ?: "8.8.8.8"
            val target = preferences.getString(PingService.KEY_TARGET_NAME, "Google DNS") ?: "Google DNS"
            val interval = preferences.getInt(PingService.KEY_INTERVAL_SECONDS, 5)
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(this, PingService::class.java).apply {
                    putExtra(PingService.EXTRA_TARGET_NAME, target)
                    putExtra(PingService.EXTRA_HOST, host)
                    putExtra(PingService.EXTRA_INTERVAL_SECONDS, interval)
                }
            )
        }
        syncTile()
    }

    private fun syncTile() {
        val preferences = getSharedPreferences(PingService.PREFERENCES_NAME, MODE_PRIVATE)
        val running = preferences.getBoolean(PingService.KEY_RUNNING, false)
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "PulsePing"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val paused = preferences.getBoolean(PingService.KEY_PAUSED, false)
                val ping = preferences.getString(PingService.KEY_PING, null)
                subtitle = when {
                    !running -> "Monitor off"
                    paused -> "Paused"
                    ping != null -> "$ping ms"
                    else -> "Starting…"
                }
            }
            updateTile()
        }
    }

    companion object {
        fun requestRefresh(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestListeningState(context, ComponentName(context, PingTileService::class.java))
            }
        }
    }
}
