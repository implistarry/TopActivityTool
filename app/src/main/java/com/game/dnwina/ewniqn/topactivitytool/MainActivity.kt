package com.game.dnwina.ewniqn.topactivitytool

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUI()
    }

    private val usageStatsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUI()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.btnUsageStatsPermission).setOnClickListener {
            requestUsageStatsPermission()
        }

        findViewById<Button>(R.id.btnNotificationPermission).setOnClickListener {
            requestNotificationPermission()
        }

        toggleButton.setOnClickListener {
            toggleService()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasUsageStats = hasUsageStatsPermission()
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        } else {
            true
        }
        val isRunning = FloatingWindowService.isRunning

        val sb = StringBuilder()
        sb.appendLine("权限状态:")
        sb.appendLine("  悬浮窗权限: ${if (hasOverlay) "✅ 已授权" else "❌ 未授权"}")
        sb.appendLine("  使用情况访问权限: ${if (hasUsageStats) "✅ 已授权" else "❌ 未授权"}")
        sb.appendLine("  通知权限: ${if (hasNotification) "✅ 已授权" else "❌ 未授权"}")
        sb.appendLine()
        sb.appendLine("服务状态: ${if (isRunning) "🟢 运行中" else "🔴 已停止"}")

        statusText.text = sb.toString()

        val allPermissionsGranted = hasOverlay && hasUsageStats && hasNotification
        toggleButton.isEnabled = allPermissionsGranted
        toggleButton.text = if (isRunning) getString(R.string.stop_service) else getString(R.string.start_service)

        // Update permission button enable states
        findViewById<Button>(R.id.btnOverlayPermission).isEnabled = !hasOverlay
        findViewById<Button>(R.id.btnUsageStatsPermission).isEnabled = !hasUsageStats
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            findViewById<Button>(R.id.btnNotificationPermission).isEnabled = !hasNotification
        } else {
            findViewById<Button>(R.id.btnNotificationPermission).isEnabled = false
        }
    }

    @Suppress("DEPRECATION")
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        usageStatsPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toggleService() {
        if (FloatingWindowService.isRunning) {
            stopService(Intent(this, FloatingWindowService::class.java))
        } else {
            val intent = Intent(this, FloatingWindowService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
        }
        // Delay UI update to allow service state to change
        statusText.postDelayed({ updateUI() }, 500)
    }
}
