package com.bon.gamemonitor.collector

data class PingTarget(
    val host: String,
    val port: Int = 80,
    val description: String = "Monitoring Target"
) {
    init {
        require(host.isNotBlank()) { "Host must not be blank" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
    }

    companion object {
        val DEFAULT = PingTarget("8.8.8.8", 80, "Google Public DNS")
        val ALTERNATIVE = PingTarget("1.1.1.1", 80, "Cloudflare Public DNS")
    }
}
