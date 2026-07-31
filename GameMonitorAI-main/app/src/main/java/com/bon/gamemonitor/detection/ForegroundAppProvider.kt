package com.bon.gamemonitor.detection

sealed class ForegroundAppResult {
    data class Package(val packageName: String) : ForegroundAppResult()
    object NoForegroundApp : ForegroundAppResult()
    data class Unavailable(val reason: String? = null) : ForegroundAppResult()
}

interface ForegroundAppProvider {
    fun getForegroundPackage(): ForegroundAppResult
}
