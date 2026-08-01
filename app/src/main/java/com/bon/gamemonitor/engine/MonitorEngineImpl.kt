package com.bon.gamemonitor.engine

import android.app.Activity
import com.bon.gamemonitor.collector.*
import com.bon.gamemonitor.collector.fps.AndroidFpsDataAccess
import com.bon.gamemonitor.collector.fps.FpsDataAccess
import com.bon.gamemonitor.data.*
import com.bon.gamemonitor.detection.GameDetector
import com.bon.gamemonitor.detection.GameDetectionState
import com.bon.gamemonitor.util.Logger
import com.bon.gamemonitor.util.RealTimeProvider
import com.bon.gamemonitor.util.TimeProvider
import com.bon.gamemonitor.validation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

class MonitorEngineImpl(
    private val batteryCollector: Collector<Int>,
    private val pingCollector: Collector<Int>,
    private val temperatureCollector: Collector<Float>,
    private val fpsCollector: Collector<Float>? = null,
    private val config: CollectorScheduleConfig = CollectorScheduleConfig(),
    private val freshnessConfig: FreshnessConfig = FreshnessConfig(),
    private val timeProvider: TimeProvider = RealTimeProvider(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : MonitorEngine {

    companion object {
        private const val TAG = "MonitorEngine"
    }

    private val isRunning = AtomicBoolean(false)
    private var engineJob: Job? = null
    private val listeners = mutableListOf<MonitorListener>()

    private val batteryState = MutableStateFlow<BatteryMetric?>(null)
    private val pingState = MutableStateFlow<PingMetric?>(null)
    private val temperatureState = MutableStateFlow<TemperatureMetric?>(null)
    private val fpsState = MutableStateFlow<FpsMetric?>(null)

    private val batteryValidator = BatteryValidator()
    private val pingValidator = PingValidator()
    private val temperatureValidator = TemperatureValidator()

    private var fpsDataAccess: FpsDataAccess? = null
    private var fpsCollectionJob: Job? = null
    private var gameDetector: GameDetector? = null
    private var gameDetectionJob: Job? = null
    private var isUIVisible = false

    override fun startMonitoring() {
        if (isRunning.getAndSet(true)) {
            Logger.w(TAG, "Engine already running")
            return
        }
        Logger.i(TAG, "Starting Monitor Engine")

        engineJob = scope.launch {
            val batteryJob = launch { collectBattery() }
            val pingJob = launch { collectPing() }
            val temperatureJob = launch { collectTemperature() }
            val fpsJob = launch { collectFps() }

            while (isRunning.get()) {
                delay(1000)
            }

            batteryJob.cancel()
            pingJob.cancel()
            temperatureJob.cancel()
            fpsJob.cancel()
            Logger.i(TAG, "Engine stopped")
        }
    }

    private suspend fun collectBattery() {
        while (isRunning.get()) {
            try {
                val metric = batteryCollector.collect()
                val validated = batteryValidator.validate(metric)
                if (validated is ValidationResult.Valid) {
                    batteryState.value = metric as BatteryMetric
                    notifyListeners(metric, MetricType.BATTERY)
                } else {
                    val naMetric = BatteryMetric(value = null, timestamp = timeProvider.currentTimeMillis())
                    batteryState.value = naMetric
                    notifyListeners(naMetric, MetricType.BATTERY)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Battery collection error", e)
                val naMetric = BatteryMetric(value = null, timestamp = timeProvider.currentTimeMillis())
                batteryState.value = naMetric
                notifyListeners(naMetric, MetricType.BATTERY)
            }
            timeProvider.delay(config.batteryIntervalMs)
        }
    }

    private suspend fun collectPing() {
        while (isRunning.get()) {
            try {
                val metric = pingCollector.collect()
                val validated = pingValidator.validate(metric)
                if (validated is ValidationResult.Valid) {
                    pingState.value = metric as PingMetric
                    notifyListeners(metric, MetricType.PING)
                } else {
                    val target = (metric as? PingMetric)?.target ?: "unknown"
                    val naMetric = PingMetric(value = null, timestamp = timeProvider.currentTimeMillis(), target = target)
                    pingState.value = naMetric
                    notifyListeners(naMetric, MetricType.PING)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Ping collection error", e)
                val target = (pingCollector as? PingCollector)?.getTarget()?.host ?: "unknown"
                val naMetric = PingMetric(value = null, timestamp = timeProvider.currentTimeMillis(), target = target)
                pingState.value = naMetric
                notifyListeners(naMetric, MetricType.PING)
            }
            timeProvider.delay(config.pingIntervalMs)
        }
    }

    private suspend fun collectTemperature() {
        while (isRunning.get()) {
            try {
                val metric = temperatureCollector.collect()
                val validated = temperatureValidator.validate(metric)
                if (validated is ValidationResult.Valid) {
                    temperatureState.value = metric as TemperatureMetric
                    notifyListeners(metric, MetricType.TEMPERATURE)
                } else {
                    val naMetric = TemperatureMetric(value = null, timestamp = timeProvider.currentTimeMillis(), sensorName = null, sensorType = null)
                    temperatureState.value = naMetric
                    notifyListeners(naMetric, MetricType.TEMPERATURE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Temperature collection error", e)
                val naMetric = TemperatureMetric(value = null, timestamp = timeProvider.currentTimeMillis())
                temperatureState.value = naMetric
                notifyListeners(naMetric, MetricType.TEMPERATURE)
            }
            timeProvider.delay(config.temperatureIntervalMs)
        }
    }

    // FPS deferred per T033
    private suspend fun collectFps() {
        while (isRunning.get()) {
            delay(1000)
        }
    }

    override fun stopMonitoring() {
        if (!isRunning.getAndSet(false)) {
            Logger.w(TAG, "Engine already stopped")
            return
        }
        Logger.i(TAG, "Stopping Monitor Engine")
        engineJob?.cancel()
        engineJob = null
        stopFpsCollection()
        fpsDataAccess?.stopCollecting()
    }

    override fun getCurrentMetric(type: MetricType): Metric<*>? {
        return when (type) {
            MetricType.BATTERY -> getFreshMetric(batteryState.value, freshnessConfig.batteryFreshnessMs)
            MetricType.PING -> getFreshMetric(pingState.value, freshnessConfig.pingFreshnessMs)
            MetricType.TEMPERATURE -> getFreshMetric(temperatureState.value, freshnessConfig.temperatureFreshnessMs)
            MetricType.FPS -> getFreshMetric(fpsState.value, freshnessConfig.fpsFreshnessMs)
        }
    }

    override fun getLatestMetric(type: MetricType): Metric<*>? {
        return when (type) {
            MetricType.BATTERY -> batteryState.value
            MetricType.PING -> pingState.value
            MetricType.TEMPERATURE -> temperatureState.value
            MetricType.FPS -> fpsState.value
        }
    }

    override fun observeMetric(type: MetricType): Flow<Metric<*>> {
        return when (type) {
            MetricType.BATTERY -> batteryState.filterNotNull()
            MetricType.PING -> pingState.filterNotNull()
            MetricType.TEMPERATURE -> temperatureState.filterNotNull()
            MetricType.FPS -> fpsState.filterNotNull()
        }
    }

    private fun <T> getFreshMetric(metric: Metric<T>?, freshnessMs: Long): Metric<T>? {
        if (metric == null) return null
        val now = timeProvider.currentTimeMillis()
        val age = now - metric.timestamp
        if (age < 0) return null
        return if (age <= freshnessMs) metric else null
    }

    override fun registerListener(listener: MonitorListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    override fun unregisterListener(listener: MonitorListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners(metric: Metric<*>, type: MetricType) {
        synchronized(listeners) {
            listeners.forEach {
                try {
                    it.onMetricUpdate(metric, type)
                } catch (e: Exception) {
                    Logger.e(TAG, "Listener error", e)
                }
            }
        }
    }

    override fun onPause() {
        isUIVisible = false
        stopFpsCollection()
        Logger.i(TAG, "UI paused — FPS collection suspended")
    }

    override fun onResume() {
        isUIVisible = true
        if (gameDetector?.currentGame?.value is GameDetectionState.Game) {
            Logger.i(TAG, "UI resumed — game detected, monitoring active")
        } else {
            Logger.i(TAG, "UI resumed — no game detected")
        }
    }

    override fun onDestroy() {
        stopFpsCollection()
        fpsDataAccess?.stopCollecting()
        detachGameDetector()
        Logger.i(TAG, "onDestroy - Activity-scoped resources released")
    }

    fun attachFpsDataAccess(activity: Activity) {
        if (fpsDataAccess == null) {
            fpsDataAccess = AndroidFpsDataAccess()
        }
        if (fpsCollectionJob?.isActive == true) {
            Logger.d(TAG, "FPS collection already running")
            return
        }
        fpsCollectionJob = scope.launch {
            val flow = fpsDataAccess!!.startCollecting(activity)
            flow.collect { frameData ->
                Logger.d(TAG, "Frame received: duration ${frameData.durationNs}ns")
            }
        }
        Logger.i(TAG, "FPS collection started")
    }

    fun detachFpsDataAccess() {
        stopFpsCollection()
        fpsDataAccess?.stopCollecting()
        Logger.i(TAG, "FPS data access detached")
    }

    private fun stopFpsCollection() {
        fpsCollectionJob?.cancel()
        fpsCollectionJob = null
    }

    fun attachGameDetector(detector: GameDetector) {
        detachGameDetector()
        gameDetector = detector
        gameDetectionJob = scope.launch {
            detector.currentGame.collect { state ->
                when (state) {
                    is GameDetectionState.Game -> {
                        Logger.i(TAG, "Game detected: ${state.packageName}")
                    }
                    GameDetectionState.NoGame -> {
                        Logger.i(TAG, "No game detected")
                        stopFpsCollection()
                    }
                    GameDetectionState.Unavailable -> {
                        Logger.w(TAG, "Game detection unavailable")
                        stopFpsCollection()
                    }
                    else -> {
                        Logger.w(TAG, "Unknown game state")
                    }
                }
            }
        }
    }

    fun detachGameDetector() {
        gameDetectionJob?.cancel()
        gameDetectionJob = null
        gameDetector = null
        Logger.i(TAG, "Game detector detached")
    }

    internal fun getBatteryState(): StateFlow<BatteryMetric?> = batteryState
    internal fun getPingState(): StateFlow<PingMetric?> = pingState
    internal fun getTemperatureState(): StateFlow<TemperatureMetric?> = temperatureState
    internal fun getFpsState(): StateFlow<FpsMetric?> = fpsState
}

data class CollectorScheduleConfig(
    val batteryIntervalMs: Long = 2000L,
    val pingIntervalMs: Long = 3000L,
    val temperatureIntervalMs: Long = 2000L,
    val fpsIntervalMs: Long = 1000L
) {
    init {
        require(batteryIntervalMs > 0) { "Battery interval must be positive" }
        require(pingIntervalMs > 0) { "Ping interval must be positive" }
        require(temperatureIntervalMs > 0) { "Temperature interval must be positive" }
        require(fpsIntervalMs > 0) { "FPS interval must be positive" }
    }
}

data class FreshnessConfig(
    val batteryFreshnessMs: Long = 5000L,
    val pingFreshnessMs: Long = 7000L,
    val temperatureFreshnessMs: Long = 5000L,
    val fpsFreshnessMs: Long = 2000L
) {
    init {
        require(batteryFreshnessMs > 0) { "Battery freshness must be positive" }
        require(pingFreshnessMs > 0) { "Ping freshness must be positive" }
        require(temperatureFreshnessMs > 0) { "Temperature freshness must be positive" }
        require(fpsFreshnessMs > 0) { "FPS freshness must be positive" }
    }
}
