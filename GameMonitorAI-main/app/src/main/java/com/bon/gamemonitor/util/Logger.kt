package com.bon.gamemonitor.util

import android.util.Log

object Logger {
    fun d(tag: String, msg: String) = Log.d("GameMonitor", "[$tag] $msg")
    fun i(tag: String, msg: String) = Log.i("GameMonitor", "[$tag] $msg")
    fun w(tag: String, msg: String) = Log.w("GameMonitor", "[$tag] $msg")
    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("GameMonitor", "[$tag] $msg", throwable)
        } else {
            Log.e("GameMonitor", "[$tag] $msg")
        }
    }
}
