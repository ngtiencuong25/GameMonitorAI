package com.bon.gamemonitor.collector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.bon.gamemonitor.data.TemperatureMetric
import com.bon.gamemonitor.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.isFinite

class TemperatureCollector(private val context: Context) : Collector<Float>, SensorEventListener {

    override val name: String = "TemperatureCollector"
    private var latestMetric: TemperatureMetric? = null
    private var selectedSensor: Sensor? = null
    private val sensorManager: SensorManager? by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private var isListening = false
    private val _isAvailable = MutableStateFlow(false)
    val isAvailableFlow: StateFlow<Boolean> = _isAvailable.asStateFlow()

    init {
        discoverSensor()
    }

    private fun discoverSensor() {
        sensorManager?.let { sm ->
            val sensor = sm.getDefaultSensor(Sensor.TYPE_TEMPERATURE)
            if (sensor != null) {
                selectedSensor = sensor
                _isAvailable.value = true
                Logger.i(name, "Selected sensor: ${sensor.name} (type=${sensor.type})")
            } else {
                Logger.w(name, "No device temperature sensor found")
                _isAvailable.value = false
            }
        } ?: run {
            Logger.w(name, "SensorManager not available")
            _isAvailable.value = false
        }
    }

    override suspend fun collect(): TemperatureMetric {
        startListening()
        return latestMetric ?: TemperatureMetric(value = null, timestamp = System.currentTimeMillis(), sensorName = null, sensorType = null)
    }

    private fun startListening() {
        if (isListening) return
        val sensor = selectedSensor ?: return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)?.let {
            if (it) {
                isListening = true
                Logger.d(name, "Started listening to sensor")
            } else {
                Logger.w(name, "Failed to register listener")
            }
        }
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        Logger.d(name, "Stopped listening")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val selected = selectedSensor ?: return
        if (event.sensor != selected) return
        val temperature = event.values[0]
        if (!temperature.isFinite()) {
            Logger.w(name, "Non-finite temperature reading: $temperature")
            return
        }
        latestMetric = TemperatureMetric(
            value = temperature,
            timestamp = System.currentTimeMillis(),
            sensorName = selected.name,
            sensorType = selected.type
        )
        Logger.d(name, "Temperature updated: $temperature°C")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun getLatest(): TemperatureMetric? = latestMetric
    override fun isAvailable(): Boolean = _isAvailable.value

    private fun createUnavailableMetric(reason: String): TemperatureMetric {
        Logger.w(name, reason)
        return TemperatureMetric(value = null, timestamp = System.currentTimeMillis(), sensorName = null, sensorType = null).also {
            latestMetric = it
        }
    }
}