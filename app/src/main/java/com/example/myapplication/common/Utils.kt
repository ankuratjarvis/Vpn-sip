package com.example.myapplication.common

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.AppComponentFactory
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone


fun TextView.DisplayLogs(logmsg: String) {
    var logmsg = logmsg
    try {
        if (logmsg == null || logmsg.length < 1) return
        if (logmsg.length > 2500) logmsg = logmsg.substring(0, 300) + "..."
        logmsg = """
            [${
            SimpleDateFormat("HH:mm:ss:SSS")
                .format(Calendar.getInstance(TimeZone.getTimeZone("GMT")).time)
        }] $logmsg
            
            """.trimIndent()
        Log.v(logmsg::javaClass.toString(), logmsg)
        this.append(logmsg)
    } catch (e: Throwable) {
        Log.e(logmsg::class.java.simpleName, "ERROR, DisplayLogs", e)

    }

}
fun netCheck(context: Context): Boolean {
    val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val nInfo = cm.activeNetworkInfo
    return nInfo != null && nInfo.isConnectedOrConnecting
}

fun isAppInForeground(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    // Get the list of running processes
    val processes = activityManager.runningAppProcesses
    if (processes != null) {
        for (processInfo in processes) {
            if (processInfo.processName == context.packageName) {
                // Check if the process importance is foreground
                return processInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }
    }
    return false
}


