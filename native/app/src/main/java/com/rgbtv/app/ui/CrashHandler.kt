package com.rgbtv.app.ui

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

/** Catches fatal crashes and shows them in-app (with copyable stack trace). */
object CrashHandler {
    fun install(app: Application) {
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            var shown = false
            try {
                val trace = Log.getStackTraceString(e)
                val i = Intent(app, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("title", (e.javaClass.simpleName ?: "Error") + ": " + (e.message ?: ""))
                    putExtra("trace", trace.take(8000))
                }
                app.startActivity(i)
                shown = true
            } catch (_: Exception) {
            }
            if (!shown) {
                try { def?.uncaughtException(t, e) } catch (_: Exception) { }
            }
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }
}
