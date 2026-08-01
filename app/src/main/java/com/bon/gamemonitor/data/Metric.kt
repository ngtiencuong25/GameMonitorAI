package com.bon.gamemonitor.data

sealed class Metric<T> {
    abstract val value: T?
    abstract val timestamp: Long
}

data class BatteryMetric(override val value: Int?, override val timestamp: Long = System.currentTimeMillis()) : Metric<Int>()
data class PingMetric(override val value: Int?, override val timestamp: Long = System.currentTimeMillis(), val target: String? = null) : Metric<Int>()
data class TemperatureMetric(override val value: Float?, override val timestamp: Long = System.currentTimeMillis(), val sensorName: String? = null, val sensorType: Int? = null) : Metric<Float>()
data class FpsMetric(override val value: Float?, override val timestamp: Long = System.currentTimeMillis()) : Metric<Float>()
