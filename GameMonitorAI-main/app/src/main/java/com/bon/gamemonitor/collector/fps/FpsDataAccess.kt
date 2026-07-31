package com.bon.gamemonitor.collector.fps

import android.app.Activity
import kotlinx.coroutines.flow.Flow

data class FrameData(val callbackTimestampNs: Long, val durationNs: Long)

interface FpsDataAccess {
    fun startCollecting(activity: Activity): Flow<FrameData>
    fun stopCollecting()
    fun isAvailable(): Boolean
}
