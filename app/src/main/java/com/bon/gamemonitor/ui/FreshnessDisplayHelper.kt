package com.bon.gamemonitor.ui

import com.bon.gamemonitor.data.Metric
import com.bon.gamemonitor.engine.MetricType
import com.bon.gamemonitor.engine.MonitorEngine

class FreshnessDisplayHelper(private val engine: MonitorEngine) {

    sealed class DisplayState<out T> {
        data class Fresh<T>(val value: T, val metric: Metric<T>) : DisplayState<T>()
        data class Stale<T>(val metric: Metric<T>?) : DisplayState<T>()
        object NoData : DisplayState<Nothing>()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getDisplayState(type: MetricType): DisplayState<T> {
        val freshMetric = engine.getCurrentMetric(type)
        if (freshMetric != null && freshMetric.value != null) {
            return DisplayState.Fresh(freshMetric.value as T, freshMetric as Metric<T>)
        }
        val latest = engine.getLatestMetric(type)
        return if (latest != null) {
            DisplayState.Stale(latest as Metric<T>)
        } else {
            DisplayState.NoData
        }
    }
}
