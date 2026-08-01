package com.bon.gamemonitor.detection

import android.app.ActivityManager
import android.content.Context
import com.bon.gamemonitor.util.Logger

class RunningProcessForegroundProvider(private val context: Context) : ForegroundAppProvider {

    private val tag = "ForegroundProvider"

    override fun getForegroundPackage(): ForegroundAppResult {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager == null) {
                return ForegroundAppResult.Unavailable("ActivityManager not available")
            }
            val processes = activityManager.getRunningAppProcesses()
            if (processes.isNullOrEmpty()) {
                return ForegroundAppResult.NoForegroundApp
            }
            val foregroundProcess = processes.firstOrNull {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
            if (foregroundProcess == null) {
                return ForegroundAppResult.NoForegroundApp
            }
            val packageName = foregroundProcess.processName.split(":").firstOrNull()
            if (packageName.isNullOrEmpty()) {
                return ForegroundAppResult.Unavailable("Invalid process name")
            }
            ForegroundAppResult.Package(packageName)
        } catch (e: Exception) {
            Logger.e(tag, "Error getting foreground package", e)
            ForegroundAppResult.Unavailable(e.message)
        }
    }
}
