package com.bon.gamemonitor.collector

import com.bon.gamemonitor.data.Metric

interface Collector<T> {
    suspend fun collect(): Metric<T>
    fun getLatest(): Metric<T>?
    fun isAvailable(): Boolean
    val name: String
}
