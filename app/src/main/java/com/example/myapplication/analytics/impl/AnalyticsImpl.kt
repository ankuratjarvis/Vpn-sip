package com.example.myapplication.analytics.impl

import android.content.Context
import android.os.Bundle
import com.example.myapplication.analytics.Analytics
import com.example.myapplication.analytics.AnalyticsEvent
import com.example.myapplication.analytics.AnalyticsParam
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

class AnalyticsImpl(val context: Context):Analytics {

    private var mAnalytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    override fun trackEvent(event: AnalyticsEvent, param: AnalyticsParam, data: String) {
        val bundle = Bundle()
        bundle.putString(param.value,data.ifEmpty { param.value })
        submitEvent(event,bundle)
    }

    override fun trackEvent(event: AnalyticsEvent, data: HashMap<AnalyticsParam, String>) {
        val bundle = Bundle()

        for (entry in data.entries) {
            bundle.putString(entry.key.value, entry.value)
        }

        submitEvent(event, bundle)
    }

    private fun submitEvent(event:AnalyticsEvent,data: Bundle){
        mAnalytics.setUserProperty(AnalyticsParam.DEVICE.value, "Android")
//        mAnalytics.setUserProperty(AnalyticParam.APP_VERSION.value, BuildConfig.VERSION_NAME)

        mAnalytics.logEvent(event.value,data)
    }
}