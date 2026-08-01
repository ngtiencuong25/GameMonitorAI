package com.bon.gamemonitor.engine

import com.bon.gamemonitor.data.Metric
import kotlinx.coroutines.flow.Flow

enum class MetricType { FPS, PING, TEMPERATURE, BATTERY }

interface MonitorEngine {
    fun startMonitoring()
    fun stopMonitoring()
    fun getCurrentMetric(type: MetricType): Metric<*>?
    fun getLatestMetric(type: MetricType): Metric<*>?
    fun observeMetric(type: MetricType): Flow<Metric<*>>
    fun registerListener(listener: MonitorListener)
    fun unregisterListener(listener: MonitorListener)
    fun onPause()
    fun onResume()
    fun onDestroy()
}

interface MonitorListener {
    fun onMetricUpdate(metric: Metric<*>, type: MetricType)
    fun onError(error: GameMonitorError)
}
