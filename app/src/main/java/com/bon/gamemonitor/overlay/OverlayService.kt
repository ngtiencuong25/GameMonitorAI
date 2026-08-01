package com.bon.gamemonitor.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.bon.gamemonitor.GameMonitorApplication
import com.bon.gamemonitor.R
import com.bon.gamemonitor.engine.MetricType
import com.bon.gamemonitor.engine.MonitorEngine
import com.bon.gamemonitor.ui.FreshnessDisplayHelper
import kotlinx.coroutines.*

class OverlayService : Service() {

    companion object {
        var isRunning = false
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_channel"
    }

    private lateinit var engine: MonitorEngine
    private lateinit var helper: FreshnessDisplayHelper
    private lateinit var overlayView: View
    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var dragInitialX = 0f
    private var dragInitialY = 0f
    private var dragInitialTouchX = 0f
    private var dragInitialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        engine = GameMonitorApplication.instance.monitorEngine
        helper = FreshnessDisplayHelper(engine)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
        observeMetrics()
    }

    private fun createOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        windowManager.addView(overlayView, params)

        // Make draggable
        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragInitialX = params.x.toFloat()
                    dragInitialY = params.y.toFloat()
                    dragInitialTouchX = event.rawX
                    dragInitialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (dragInitialX + (event.rawX - dragInitialTouchX)).toInt()
                    params.y = (dragInitialY + (event.rawY - dragInitialTouchY)).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun observeMetrics() {
        // Simple polling-based update (acceptable for V1.0)
        // In V1.1, we can switch to Flow-based push updates
        scope.launch {
            while (isRunning) {
                updateOverlay()
                delay(2000)
            }
        }
    }

    private fun updateOverlay() {
        val batteryState = helper.getDisplayState<Int>(MetricType.BATTERY)
        val pingState = helper.getDisplayState<Int>(MetricType.PING)
        val tempState = helper.getDisplayState<Float>(MetricType.TEMPERATURE)

        val tvBattery = overlayView.findViewById<TextView>(R.id.overlayBatteryValue)
        val tvPing = overlayView.findViewById<TextView>(R.id.overlayPingValue)
        val tvTemp = overlayView.findViewById<TextView>(R.id.overlayTemperatureValue)
        val tvFps = overlayView.findViewById<TextView>(R.id.overlayFpsValue)

        tvBattery.text = when (batteryState) {
            is FreshnessDisplayHelper.DisplayState.Fresh -> "${batteryState.value}%"
            else -> "N/A"
        }
        tvPing.text = when (pingState) {
            is FreshnessDisplayHelper.DisplayState.Fresh -> "${pingState.value}ms"
            else -> "N/A"
        }
        tvTemp.text = when (tempState) {
            is FreshnessDisplayHelper.DisplayState.Fresh -> "${tempState.value}°C"
            else -> "N/A"
        }
        tvFps.text = "N/A"  // FPS deferred
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GameMonitor Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GameMonitor AI")
            .setContentText("Monitoring overlay is active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        scope.cancel()
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            // View may already be removed
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
