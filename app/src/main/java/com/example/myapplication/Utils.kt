package com.example.myapplication

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
