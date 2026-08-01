package com.bon.gamemonitor.util

import kotlinx.coroutines.delay

interface TimeProvider {
    suspend fun delay(timeMs: Long)
    fun currentTimeMillis(): Long
}

class RealTimeProvider : TimeProvider {
    override suspend fun delay(timeMs: Long) = kotlinx.coroutines.delay(timeMs)
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
