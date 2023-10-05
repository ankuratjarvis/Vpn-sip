package com.example.myapplication.analytics

interface Analytics {
    fun trackEvent(event:AnalyticsEvent,param:AnalyticsParam,data:String="")
    fun trackEvent(event:AnalyticsEvent,data: HashMap<AnalyticsParam, String>)
}