package com.game.dnwina.ewniqn.topactivitytool

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class TopActivityTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            // Need overlay permission — open settings
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        if (FloatingWindowService.isRunning) {
            stopService(Intent(this, FloatingWindowService::class.java))
        } else {
            ContextCompat.startForegroundService(
                this,
                Intent(this, FloatingWindowService::class.java)
            )
        }

        // Delay to allow service state to update, then refresh tile
        Handler(Looper.getMainLooper()).postDelayed({ updateTile() }, 500)
    }

    private fun updateTile() {
        qsTile?.let { tile ->
            if (FloatingWindowService.isRunning) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.tile_label)
                tile.subtitle = "运行中"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_label)
                tile.subtitle = "已停止"
            }
            tile.updateTile()
        }
    }
}
