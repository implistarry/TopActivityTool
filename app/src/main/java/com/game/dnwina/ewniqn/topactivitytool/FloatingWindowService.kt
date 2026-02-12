package com.game.dnwina.ewniqn.topactivitytool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingWindowService : Service() {

    companion object {
        const val CHANNEL_ID = "top_activity_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.game.dnwina.ewniqn.topactivitytool.ACTION_STOP"

        @Volatile
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var tvActivityInfo: TextView
    private lateinit var tvHistory: TextView
    private lateinit var historyHeader: View
    private lateinit var historyScrollView: ScrollView
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L // 1 second

    private var currentTextSize = 12f
    private val minTextSize = 8f
    private val maxTextSize = 24f

    // 缓存上次获取到的 Activity 信息，避免查询窗口内无事件时丢失显示
    private var lastKnownPackage = ""
    private var lastKnownClass = ""
    private var lastKnownAppName = ""

    // 历史记录
    private var showHistory = false
    private val historyList = mutableListOf<String>()
    private val maxHistorySize = 50

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateTopActivity()
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        createFloatingWindow()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        try {
            windowManager.removeView(floatingView)
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Activity监控服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示当前顶部Activity信息的前台服务"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, FloatingWindowService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.monitor_running))
            .setContentText(getString(R.string.monitor_desc))
            .setSmallIcon(R.drawable.ic_visibility)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_close, getString(R.string.stop), stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    @Suppress("ClickableViewAccessibility")
    private fun createFloatingWindow() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null)
        tvActivityInfo = floatingView.findViewById(R.id.tvActivityInfo)
        tvHistory = floatingView.findViewById(R.id.tvHistory)
        historyHeader = floatingView.findViewById(R.id.historyHeader)
        historyScrollView = floatingView.findViewById(R.id.historyScrollView)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        setupDrag()
        setupButtons()

        windowManager.addView(floatingView, layoutParams)
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupDrag() {
        val dragArea = floatingView.findViewById<View>(R.id.dragArea)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }

                else -> false
            }
        }
    }

    private fun setupButtons() {
        floatingView.findViewById<ImageButton>(R.id.btnZoomIn).setOnClickListener {
            if (currentTextSize < maxTextSize) {
                currentTextSize += 2f
                tvActivityInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize)
            }
        }

        floatingView.findViewById<ImageButton>(R.id.btnZoomOut).setOnClickListener {
            if (currentTextSize > minTextSize) {
                currentTextSize -= 2f
                tvActivityInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize)
            }
        }

        floatingView.findViewById<ImageButton>(R.id.btnHistory).setOnClickListener {
            showHistory = !showHistory
            val visibility = if (showHistory) View.VISIBLE else View.GONE
            historyHeader.visibility = visibility
            historyScrollView.visibility = visibility
            if (showHistory) {
                refreshHistoryDisplay()
            }
        }

        floatingView.findViewById<TextView>(R.id.btnClearHistory).setOnClickListener {
            historyList.clear()
            refreshHistoryDisplay()
        }

        floatingView.findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            stopSelf()
        }
    }

    private fun updateTopActivity() {
        try {
            val usageStatsManager =
                getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            // 查询较大时间窗口，确保能捕获到最近一次 ACTIVITY_RESUMED 事件
            val beginTime = endTime - 60_000

            val events = usageStatsManager.queryEvents(beginTime, endTime)
            val event = UsageEvents.Event()

            var foundPackage = ""
            var foundClass = ""

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    foundPackage = event.packageName ?: ""
                    foundClass = event.className ?: ""
                }
            }

            // 如果本次查询到了新的事件，更新缓存
            if (foundPackage.isNotEmpty()) {
                // 仅在包名或类名发生变化时才更新
                if (foundPackage != lastKnownPackage || foundClass != lastKnownClass) {
                    if (foundPackage != lastKnownPackage) {
                        lastKnownAppName = getAppName(foundPackage)
                    }
                    // 记录到历史（跳过初始空值）
                    if (lastKnownClass.isNotEmpty()) {
                        addHistory(lastKnownAppName, lastKnownClass)
                    }
                    lastKnownPackage = foundPackage
                    lastKnownClass = foundClass
                }
            }

            // 使用缓存的信息来显示（只要曾经获取过就一直显示）
            if (lastKnownPackage.isNotEmpty()) {
                val info = buildString {
                    appendLine(lastKnownAppName)
                    append(lastKnownClass)
                }

                tvActivityInfo.text = info
            } else {
                tvActivityInfo.text = getString(R.string.waiting_info)
            }
        } catch (e: Exception) {
            // 即使出错，如果有缓存信息也继续显示，不覆盖
            if (lastKnownPackage.isEmpty()) {
                tvActivityInfo.text = "获取信息失败: ${e.message}"
            }
        }
    }

    private fun addHistory(appName: String, className: String) {
        val entry = "$appName\n$className"
        historyList.add(0, entry)
        if (historyList.size > maxHistorySize) {
            historyList.removeAt(historyList.lastIndex)
        }
        if (showHistory) {
            refreshHistoryDisplay()
        }
    }

    private fun refreshHistoryDisplay() {
        if (historyList.isEmpty()) {
            tvHistory.text = "暂无历史记录"
        } else {
            tvHistory.text = historyList.mapIndexed { i, entry ->
                "${i + 1}. $entry"
            }.joinToString("\n———\n")
        }
        // 滚动到顶部显示最新记录
        historyScrollView.post { historyScrollView.scrollTo(0, 0) }
    }

    /**
     * 获取应用名称，做多重容错处理
     */
    private fun getAppName(pkg: String): String {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    pkg,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(pkg, 0)
            }
            val label = packageManager.getApplicationLabel(appInfo)
            if (label.isNullOrBlank()) pkg else label.toString()
        } catch (_: Exception) {
            // NameNotFoundException / SecurityException 等都做兜底
            pkg
        }
    }
}
