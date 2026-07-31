package com.bon.gamemonitor.collector

import android.content.Context
import android.os.BatteryManager
import com.bon.gamemonitor.data.BatteryMetric
import com.bon.gamemonitor.util.Logger

class BatteryCollector(private val context: Context) : Collector<Int> {

    override val name: String = "BatteryCollector"
    private var latestMetric: BatteryMetric? = null

    override suspend fun collect(): BatteryMetric {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (batteryManager == null) {
                return createUnavailableMetric("BatteryManager service unavailable")
            }
            val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (capacity < 0 || capacity > 100) {
                return createUnavailableMetric("Invalid battery capacity: $capacity")
            }
            BatteryMetric(value = capacity, timestamp = System.currentTimeMillis()).also {
                latestMetric = it
                Logger.d(name, "Battery collected: $capacity%")
            }
        } catch (e: Exception) {
            Logger.e(name, "Error collecting battery", e)
            createUnavailableMetric("Error: ${e.message}")
        }
    }

    override fun getLatest(): BatteryMetric? = latestMetric

    override fun isAvailable(): Boolean {
        return try {
            context.getSystemService(Context.BATTERY_SERVICE) is BatteryManager
        } catch (e: Exception) {
            false
        }
    }

    private fun createUnavailableMetric(reason: String): BatteryMetric {
        Logger.w(name, reason)
        return BatteryMetric(value = null, timestamp = System.currentTimeMillis()).also {
            latestMetric = it
        }
    }
}
