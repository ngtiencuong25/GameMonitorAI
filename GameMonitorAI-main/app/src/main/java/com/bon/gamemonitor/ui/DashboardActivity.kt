package com.bon.gamemonitor.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bon.gamemonitor.GameMonitorApplication
import com.bon.gamemonitor.R
import com.bon.gamemonitor.databinding.ActivityDashboardBinding
import com.bon.gamemonitor.detection.GameDetector
import com.bon.gamemonitor.detection.GameDetectionState
import com.bon.gamemonitor.detection.RunningProcessForegroundProvider
import com.bon.gamemonitor.engine.*
import com.bon.gamemonitor.overlay.OverlayService
import com.bon.gamemonitor.util.Logger
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var engine: MonitorEngine
    private lateinit var helper: FreshnessDisplayHelper
    private lateinit var gameDetector: GameDetector
    private val scope = lifecycleScope

    companion object {
        private const val TAG = "DashboardActivity"
        private const val OVERLAY_PERMISSION_REQUEST = 1234
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        engine = GameMonitorApplication.instance.monitorEngine
        helper = FreshnessDisplayHelper(engine)

        val provider = RunningProcessForegroundProvider(applicationContext)
        gameDetector = GameDetector(provider)
        gameDetector.startDetection()
        (engine as? MonitorEngineImpl)?.attachGameDetector(gameDetector)

        engine.startMonitoring()
        observeMetrics()
        observeGameState()

        binding.overlayToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    requestOverlayPermission()
                    binding.overlayToggle.isChecked = false
                } else {
                    startOverlayService()
                }
            } else {
                stopOverlayService()
            }
        }

        binding.overlayToggle.isChecked = OverlayService.isRunning
    }

    private fun observeMetrics() {
        (engine as? MonitorEngineImpl)?.getBatteryState()
            ?.onEach { updateBattery() }
            ?.launchIn(scope)

        (engine as? MonitorEngineImpl)?.getPingState()
            ?.onEach { updatePing() }
            ?.launchIn(scope)

        (engine as? MonitorEngineImpl)?.getTemperatureState()
            ?.onEach { updateTemperature() }
            ?.launchIn(scope)

        (engine as? MonitorEngineImpl)?.getFpsState()
            ?.onEach { updateFps() }
            ?.launchIn(scope)
    }

    private fun observeGameState() {
        gameDetector.currentGame
            .onEach { state ->
                binding.gameStatus.text = when (state) {
                    is GameDetectionState.Game -> "🎮 Playing: ${getGameDisplayName(state.packageName)}"
                    GameDetectionState.NoGame -> "⏸️ No game detected"
                    GameDetectionState.Unavailable -> "⚠️ Detection unavailable"
                    else -> "Unknown game"
                }
            }
            .launchIn(scope)
    }

    private fun updateBattery() {
        val state = helper.getDisplayState<Int>(MetricType.BATTERY)
        binding.valueBattery.text = when (state) {
            is FreshnessDisplayHelper.DisplayState.Fresh -> "${state.value}%"
            else -> "N/A"
        }
        binding.freshnessBattery.text = if (state is FreshnessDisplayHelper.DisplayState.Fresh) "● Fresh" else "○ Stale / N/A"
        binding.freshnessBattery.setTextColor(if (state is FreshnessDisplayHelper.DisplayState.Fresh) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.darker_gray))
    }

    private fun updatePing() {
        val state = helper.getDisplayState<Int>(MetricType.PING)
        binding.valuePing.text = when (state) {
            is FreshnessDisplayHelper.DisplayState.Fresh -> "${state.value} ms"
            else -> "N/A"
        }
        binding.freshnessPing.text = if (state is FreshnessDisplayHelper.DisplayState.Fresh) "● Fresh" else "○ Stale / N/A"
        binding.freshnessPing.setTextColor(if (state is FreshnessDisplayHelper.DisplayState.Fresh) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.darker_gray))
    }

    private fun updateTemperature() {
        val state = helper.getDisplayState<Float>(MetricType.TEMPERATURE)
        binding.valueTemperature.text = when (state) {
            is FreshnessDisplayHelper.DisplayState.Fresh -> "${state.value} °C"
            else -> "N/A"
        }
        binding.freshnessTemperature.text = if (state is FreshnessDisplayHelper.DisplayState.Fresh) "● Fresh" else "○ Stale / N/A"
        binding.freshnessTemperature.setTextColor(if (state is FreshnessDisplayHelper.DisplayState.Fresh) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.darker_gray))
    }

    private fun updateFps() {
        binding.valueFps.text = "N/A"
        binding.freshnessFps.text = "○ Deferred"
        binding.freshnessFps.setTextColor(getColor(android.R.color.darker_gray))
    }

    private fun getGameDisplayName(packageName: String): String {
        return when (packageName) {
            "com.dts.freefireth" -> "Free Fire"
            "com.garena.game.kgvn" -> "Liên Quân"
            else -> packageName
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                binding.overlayToggle.isChecked = true
                startOverlayService()
            } else {
                binding.overlayToggle.isChecked = false
                Logger.w(TAG, "Overlay permission denied")
            }
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        OverlayService.isRunning = true
        Logger.i(TAG, "Overlay service started")
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
        OverlayService.isRunning = false
        Logger.i(TAG, "Overlay service stopped")
    }

    override fun onPause() {
        super.onPause()
        engine.onPause()
    }

    override fun onResume() {
        super.onResume()
        engine.onResume()
    }

    override fun onDestroy() {
        gameDetector.stopDetection()
        (engine as? MonitorEngineImpl)?.detachGameDetector()
        engine.onDestroy()
        super.onDestroy()
    }
}