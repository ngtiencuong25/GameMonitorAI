package com.bon.gamemonitor.detection

import com.bon.gamemonitor.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

sealed class GameDetectionState {
    object NoGame : GameDetectionState()
    data class Game(val packageName: String) : GameDetectionState()
    object Unavailable : GameDetectionState()
}

data class GameStartEvent(val packageName: String, val timestamp: Long = System.currentTimeMillis())
data class GameStopEvent(val packageName: String, val timestamp: Long = System.currentTimeMillis())

class GameDetector(
    private val foregroundProvider: ForegroundAppProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val intervalMs: Long = 1000L
) {
    companion object {
        val SUPPORTED_GAMES = setOf("com.dts.freefireth", "com.garena.game.kgvn")
        private const val TAG = "GameDetector"
    }

    private val _currentGame = MutableStateFlow<GameDetectionState>(GameDetectionState.NoGame)
    val currentGame: StateFlow<GameDetectionState> = _currentGame.asStateFlow()

    private val _gameStartEvents = MutableSharedFlow<GameStartEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val gameStartEvents: SharedFlow<GameStartEvent> = _gameStartEvents.asSharedFlow()

    private val _gameStopEvents = MutableSharedFlow<GameStopEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val gameStopEvents: SharedFlow<GameStopEvent> = _gameStopEvents.asSharedFlow()

    private val _detectionVersion = MutableStateFlow(0)
    val detectionVersion: StateFlow<Int> = _detectionVersion.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private var detectionJob: Job? = null

    fun startDetection() {
        if (isRunning.getAndSet(true)) return
        detectionJob = scope.launch(dispatcher) {
            while (isRunning.get()) {
                val previousState = _currentGame.value
                val newState = detectState()

                val shouldEmitStart = when {
                    newState is GameDetectionState.Game && previousState !is GameDetectionState.Game -> true
                    newState is GameDetectionState.Game && previousState is GameDetectionState.Game && (previousState as GameDetectionState.Game).packageName != newState.packageName -> true
                    else -> false
                }

                if (shouldEmitStart) {
                    _gameStartEvents.tryEmit(GameStartEvent((newState as GameDetectionState.Game).packageName))
                }

                val shouldEmitStop = previousState is GameDetectionState.Game &&
                        (newState !is GameDetectionState.Game || (newState is GameDetectionState.Game && newState.packageName != (previousState as GameDetectionState.Game).packageName))

                if (shouldEmitStop) {
                    _gameStopEvents.tryEmit(GameStopEvent((previousState as GameDetectionState.Game).packageName))
                }

                _currentGame.value = newState
                _detectionVersion.value++
                delay(intervalMs)
            }
        }
    }

    fun stopDetection() {
        isRunning.set(false)
        detectionJob?.cancel()
        detectionJob = null
        _currentGame.value = GameDetectionState.NoGame
        Logger.i(TAG, "Game detection stopped")
    }

    private fun detectState(): GameDetectionState {
        return try {
            val result = foregroundProvider.getForegroundPackage()
            when (result) {
                is ForegroundAppResult.Package -> {
                    if (SUPPORTED_GAMES.contains(result.packageName)) {
                        GameDetectionState.Game(result.packageName)
                    } else {
                        GameDetectionState.NoGame
                    }
                }
                ForegroundAppResult.NoForegroundApp -> GameDetectionState.NoGame
                is ForegroundAppResult.Unavailable -> GameDetectionState.Unavailable
                else -> GameDetectionState.Unavailable
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error detecting foreground app", e)
            GameDetectionState.Unavailable
        }
    }

    fun isGameRunning(): Boolean = _currentGame.value is GameDetectionState.Game
    fun getCurrentGamePackage(): String? {
        return when (val state = _currentGame.value) {
            is GameDetectionState.Game -> state.packageName
            else -> null
        }
    }
}