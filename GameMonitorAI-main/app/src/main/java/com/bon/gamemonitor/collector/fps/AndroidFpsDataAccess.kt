package com.bon.gamemonitor.collector.fps

import android.app.Activity
import android.os.Build
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import com.bon.gamemonitor.util.Logger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@RequiresApi(Build.VERSION_CODES.N)
class AndroidFpsDataAccess : FpsDataAccess {

    private val lock = Any()
    private var currentListener: Window.OnFrameMetricsAvailableListener? = null
    private var currentWindow: Window? = null
    private var isActive = false

    override fun startCollecting(activity: Activity): Flow<FrameData> = callbackFlow {
        val listener = Window.OnFrameMetricsAvailableListener { _, _, frameMetrics ->
            val durationNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (durationNs > 0) {
                val result = trySend(FrameData(System.nanoTime(), durationNs))
                if (result.isFailure) {
                    Logger.w("FpsDataAccess", "Failed to send frame data (flow closed)")
                }
            }
        }
        val win = activity.window

        synchronized(lock) {
            stopCollectingInternal()
            win.addOnFrameMetricsAvailableListener(listener, null)
            currentListener = listener
            currentWindow = win
            isActive = true
            Logger.d("FpsDataAccess", "Started collecting frame metrics")
        }

        awaitClose {
            synchronized(lock) {
                if (currentListener === listener) {
                    currentWindow?.removeOnFrameMetricsAvailableListener(listener)
                    currentListener = null
                    currentWindow = null
                    isActive = false
                    Logger.d("FpsDataAccess", "Stopped collecting frame metrics")
                } else {
                    Logger.d("FpsDataAccess", "Listener already replaced, skipping removal")
                }
            }
        }
    }

    private fun stopCollectingInternal() {
        currentWindow?.removeOnFrameMetricsAvailableListener(currentListener)
        currentListener = null
        currentWindow = null
        isActive = false
    }

    override fun stopCollecting() {
        synchronized(lock) {
            stopCollectingInternal()
            Logger.d("FpsDataAccess", "stopCollecting() called")
        }
    }

    override fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
}
