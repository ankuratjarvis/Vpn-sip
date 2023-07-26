package com.example.myapplication.common

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import android.widget.TextView
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
