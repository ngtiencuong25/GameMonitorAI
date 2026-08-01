package com.bon.gamemonitor

import android.app.Application
import android.util.Log
import com.bon.gamemonitor.collector.*
import com.bon.gamemonitor.engine.MonitorEngine
import com.bon.gamemonitor.engine.MonitorEngineImpl

class GameMonitorApplication : Application() {

    companion object {
        private const val TAG = "GameMonitorApp"
        lateinit var instance: GameMonitorApplication
            private set
    }

    lateinit var monitorEngine: MonitorEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val batteryCollector = BatteryCollector(this)
        val pingCollector = PingCollector(this)
        val temperatureCollector = TemperatureCollector(this)
        val fpsCollector: Collector<Float>? = null

        monitorEngine = MonitorEngineImpl(
            batteryCollector = batteryCollector,
            pingCollector = pingCollector,
            temperatureCollector = temperatureCollector,
            fpsCollector = fpsCollector
        )

        Log.i(TAG, "🚀 GameMonitor AI V1.0 initialized")
    }
}
