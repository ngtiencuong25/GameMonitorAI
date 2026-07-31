package com.bon.gamemonitor.engine

sealed class GameMonitorError(
    val message: String,
    val cause: Throwable? = null
) {
    class PermissionError(message: String, cause: Throwable? = null) : GameMonitorError(message, cause)
    class CollectorError(message: String, cause: Throwable? = null) : GameMonitorError(message, cause)
    class ValidationError(message: String, cause: Throwable? = null) : GameMonitorError(message, cause)
    class EngineError(message: String, cause: Throwable? = null) : GameMonitorError(message, cause)
    class UnknownError(message: String, cause: Throwable? = null) : GameMonitorError(message, cause)
}
