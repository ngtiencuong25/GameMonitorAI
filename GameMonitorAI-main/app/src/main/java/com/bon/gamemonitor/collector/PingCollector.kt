package com.bon.gamemonitor.collector

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bon.gamemonitor.data.PingMetric
import com.bon.gamemonitor.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

class PingCollector(
    private val context: Context,
    private var target: PingTarget = PingTarget.DEFAULT
) : Collector<Int> {

    override val name: String = "PingCollector"
    private var latestMetric: PingMetric? = null
    var timeoutMs: Int = 3000
    var maxRetries: Int = 2
    var retryDelayMs: Long = 500
    var backoffMultiplier: Double = 1.5

    init {
        Logger.i(name, "Target configured: ${target.host}:${target.port}")
    }

    fun setTarget(newTarget: PingTarget) {
        if (target != newTarget) {
            target = newTarget
            latestMetric = null
            Logger.i(name, "Target changed to: ${target.host}:${target.port}")
        }
    }

    fun getTarget(): PingTarget = target

    override suspend fun collect(): PingMetric = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext createUnavailableMetric("No network connection")
        }

        var attempt = 0
        var lastError: Exception? = null
        var delayMs = retryDelayMs

        while (attempt <= maxRetries) {
            try {
                val rttMs = measureTcpRtt(target.host, target.port)
                if (rttMs != null) {
                    return@withContext PingMetric(value = rttMs, timestamp = System.currentTimeMillis(), target = target.host).also {
                        latestMetric = it
                        Logger.d(name, "TCP RTT measured: ${rttMs}ms to ${target.host}:${target.port} (attempt ${attempt + 1})")
                    }
                } else {
                    lastError = Exception("Connection timeout or refused")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnknownHostException) {
                Logger.w(name, "Unknown host: ${target.host}")
                return@withContext createUnavailableMetric("Unknown host: ${target.host}")
            } catch (e: Exception) {
                lastError = e
                Logger.w(name, "Measurement attempt ${attempt + 1} failed: ${e.message}")
            }

            if (attempt >= maxRetries) break
            Logger.d(name, "Retrying in ${delayMs}ms (attempt ${attempt + 1}/${maxRetries})")
            kotlinx.coroutines.delay(delayMs)
            delayMs = (delayMs * backoffMultiplier).toLong().coerceAtMost(5000L)
            attempt++
        }

        val errorMsg = lastError?.message ?: "All ${maxRetries + 1} attempts failed"
        return@withContext createUnavailableMetric(errorMsg)
    }

    private fun measureTcpRtt(host: String, port: Int): Int? {
        return try {
            val start = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName(host), port), timeoutMs)
            }
            val elapsedNs = System.nanoTime() - start
            (elapsedNs / 1_000_000).toInt()
        } catch (e: UnknownHostException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override fun getLatest(): PingMetric? = latestMetric
    override fun isAvailable(): Boolean = target.host.isNotBlank()

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun createUnavailableMetric(reason: String): PingMetric {
        Logger.w(name, reason)
        return PingMetric(value = null, timestamp = System.currentTimeMillis(), target = target.host).also {
            latestMetric = it
        }
    }
}