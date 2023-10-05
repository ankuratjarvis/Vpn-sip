package com.example.myapplication.analytics

enum class AnalyticsEvent(val value:String) {

    ERROR("app_error"),
    APP_ACTIVE("active"),
    APP_NOT_ACTIVE("app_inactive"),
    VPN_START("vpn_start"),
    VPN_CONNECTED("vpn_connected"),
    VPN_DISCONNECTED("vpn_disconnected"),

    SIP_START("sip_start"),
    SIP_STOP("sip_stop"),
    INCOMING_CALL("incoming_call"),
    CALL_ENDED("call_ended")

}
enum class AnalyticsParam(val value:String){
    USER_SIP_ID("sip_id"),
    USER_SIP_DOMAIN("sip_domain"),
    USER_VPN_ID("vpn_id"),
    NO_INTERNET("no_internet"),
    CLICK("click"),
    PROCESS_KILLED("process_killed"),
    CALL_BRIDGING("call_bridging"),
    STOP("stop"),
    APP_ACTIVE_TRUE("true"),
    DEVICE("device"),

}