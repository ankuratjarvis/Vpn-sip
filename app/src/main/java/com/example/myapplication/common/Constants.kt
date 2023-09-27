package com.example.myapplication.common

object Constants {

    const val SIP_USERNAME = "1000000099"
    const val SIP_DOMAIN = "103.168.211.98"
    const val SIP_PASSWORD = "info@79102#"

    const val VPN_PASSWORD = "^08uOf&yvQ"
    const val VPN_USERNAME = "0603441510939"
    const val VPN_AGENT_USERNAME = "0603441511147"
    const val VPN_AGENT_PASSWORD = "c9Bq!8RHPh"
    const val VPN_AGENT_DOMAIN = "sip:sip-user.ttsl.tel"
    const val VPN_DOMAIN_ID = "sip:$VPN_AGENT_USERNAME@sip-user.ttsl.tel"

    const val SIP_DOMAIN_ID = "sip:${SIP_USERNAME}@103.168.211.98"
    const val SIP_DOMAIN_PROXY = "sip:$SIP_DOMAIN"

    const val INCOMING_CALL= "incoming_call"
    const val CALL_RESPONSE_ACTION_KEY= "response_action_key"
    const val CALL_RECEIVE_ACTION= "receive_action"
    const val CALL_CANCEL_ACTION= "cancel_action"
    const val IS_CALL_ACTIVE= "is_call_active"
    const val SERVICE= "service"
    const val IS_VPN_ACTIVE= "is_vpn_active"



}