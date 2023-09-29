package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.util.Log
import android.widget.ListView
import android.widget.SimpleAdapter
import androidx.core.app.NotificationCompat
import com.example.myapplication.R
import com.example.myapplication.SipActivecall
import com.example.myapplication.SipActivity
import com.example.myapplication.SipApp
import com.example.myapplication.utils.MSG_TYPE
import com.example.myapplication.utils.MyAccount
import com.example.myapplication.utils.MyAppObserver
import com.example.myapplication.utils.MyBuddy
import com.example.myapplication.utils.MyCall
import com.example.myapplication.utils.ServiceCallback
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsip_status_code
import java.util.Objects


class NotificationService : Service(), Handler.Callback, MyAppObserver {
    private val SIP_CHANNEL_ID = "VoipChannel"
    private val SIP_CHANNEL_NAME = "SIP Voip Channel"

    private val SIP_CALL_CHANNEL_ID = "VoipCall"
    private val SIP_CALL_CHANNEL_NAME = "Voip Call Channel"

    private val TAG = "NotificationService"
    var app: SipApp? = null
    var currentCall: MyCall? = null
    var account: MyAccount? = null
    var accCfg: AccountConfig? = null
    var intentFilter: IntentFilter? = null

    private var buddyListView: ListView? = null
    private var buddyListAdapter: SimpleAdapter? = null
    private var buddyListSelectedIdx = -1
    var buddyList = mutableListOf<Map<String, String>>()
    private var lastRegStatus = ""
    private val handler = Handler(this)
    var INSTANCE: NotificationService? = null

    val SIP_ACTIVE_NOTIFICATION_ID = 101
    val ACTIVE_CALL_NOTIFICATION_ID = 102

    private val mBinder = LocalBinder()


    private var serviceCallback: ServiceCallback? = null

    override fun onBind(p0: Intent?): IBinder? {
        return mBinder
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Inside on start command")
        initSIP(intent)

        return START_STICKY
    }

    private fun initSIP(intent: Intent?) {
        val b = intent?.getBundleExtra("data_bundle")
        val id = b?.getString("domain_id")
        val proxy = b?.getString("proxy")
        val username = b?.getString("username")
        val password = b?.getString("password")
        if (app == null) {
            app = SipApp()
            // Wait for GDB to init, for native debugging only
            if (false &&
                applicationInfo.flags and
                ApplicationInfo.FLAG_DEBUGGABLE != 0
            ) {
                try {
                    Thread.sleep(5000)
                } catch (e: InterruptedException) {
                }
            }
            app?.init(this, filesDir.absolutePath)
            if (app!!.accList.size == 0) {
                accCfg = AccountConfig()
                accCfg!!.idUri = "sip:localhost"
                accCfg?.natConfig?.iceEnabled = true
                accCfg?.videoConfig?.autoTransmitOutgoing = true
                accCfg?.videoConfig?.autoShowIncoming = true
                account = app?.addAcc(accCfg)
            } else {
                account = app?.accList?.get(0)!!
                accCfg = account?.cfg
            }
            dlgAccountSetting(id!!, proxy!!, username!!, password!!)
        }

    }

    fun dlgAccountSetting(_id: String, _proxy: String, _username: String, _password: String) {
        val registrar = _proxy
        val proxy = _proxy
        val username = _username
        val password = _password
        accCfg?.idUri = _id
        accCfg?.regConfig?.registrarUri = registrar
        val creds1 = accCfg?.sipConfig?.authCreds
        creds1?.clear()
        if (username.isNotEmpty()) {
            creds1?.add(
                AuthCredInfo(
                    "Digest", "*", username, 0,
                    password
                )
            )
        }
        val proxies1 =
            accCfg?.sipConfig?.proxies
        proxies1?.clear()
        if (proxy.isNotEmpty()) {
            proxies1?.add(proxy)
        }

        /* Enable ICE */accCfg?.natConfig?.iceEnabled = true

        /* Finally */lastRegStatus = ""
        account?.modify(accCfg)
//        startForeground(SIP_ACTIVE_NOTIFICATION_ID, showSipActiveNotification())
    }


    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sipChannel = NotificationChannel(
                SIP_CHANNEL_ID,
                SIP_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            val callChannel = NotificationChannel(
                SIP_CALL_CHANNEL_ID,
                SIP_CALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )


            sipChannel.description = "SIP Notifications"
            callChannel.description = "SIP Call Notifications"
            /* channel.setSound(
                 Uri.parse("android.resource://" + applicationContext.packageName + "/" + R.raw.ringtone),
                 AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                     .setLegacyStreamType(AudioManager.STREAM_RING)
                     .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build()
             )*/
            val channelList = mutableListOf(sipChannel, callChannel)
            Objects.requireNonNull(
                applicationContext.getSystemService(
                    NotificationManager::class.java
                )
            ).createNotificationChannels(channelList)
        }
    }

    fun registerCallback(callback: ServiceCallback) {
        serviceCallback = callback
        Log.d(TAG, "Csllback Registered --->$serviceCallback")

    }

    override fun handleMessage(m: Message): Boolean {
        if (m.what == 0) {
            app?.deinit()
            app = null;

//            finish();
            Runtime.getRuntime().gc();
//            android.os.Process.killProcess(android.os.Process.myPid());
//            stopNotificationService()

        } else if (m.what == MSG_TYPE.CALL_STATE) {
            val ci = m.obj as CallInfo
            if (currentCall == null || ci == null || ci.id != currentCall?.id) {
                println("Call state event received, but call info is invalid")
                return true
            }

            /* Forward the call info to CallActivity */if (SipActivecall.handler_ != null) {
                val m2 = Message.obtain(SipActivecall.handler_, MSG_TYPE.CALL_STATE, ci)
                m2.sendToTarget()
            }

            Log.d(TAG, "Call State----> ${ci.state}")
            if (ci.state == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                if (currentCall != null) {
                    currentCall?.delete()
                    currentCall = null
                    hangupCall()
                }

            }/*else if(ci.state == pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED){
                if(currentCall!=null){
                    currentCall?.delete()
                    currentCall = null
                    hangupCall()
                }
            }*/
        } else if (m.what == MSG_TYPE.CALL_MEDIA_STATE) {

            /* Forward the message to CallActivity */
            if (SipActivecall.handler_ != null) {
                val m2 = Message.obtain(
                    SipActivecall.handler_,
                    MSG_TYPE.CALL_MEDIA_STATE,
                    null
                )
                m2.sendToTarget()
            }
        } else if (m.what == MSG_TYPE.BUDDY_STATE) {
            val buddy = m.obj as MyBuddy
            val idx = account?.buddyList?.indexOf(buddy)

            /* Update buddy status text, if buddy is valid and
             * the buddy lists in account and UI are sync-ed.
             */if (idx!! >= 0 && account?.buddyList?.size == buddyList!!.size) {
                buddyList.add(idx, mapOf("status" to buddy.statusText))
//                !![idx].put("status", buddy.statusText)
                buddyListAdapter!!.notifyDataSetChanged()
                // TODO: selection color/mark is gone after this,
                //       dont know how to return it b TODO("Not yet implemented")ack.
                //buddyListView.setSelection(buddyListSelectedIdx);
                //buddyListView.performItemClick(buddyListView,
                //                                   buddyListSelectedIdx,
                //                                   buddyListView.
                //                  getItemIdAtPosition(buddyListSelectedIdx));

                /* Return back Call activity */

                notifyCallState(currentCall)
            }
        } else if (m.what == MSG_TYPE.REG_STATE) {
            val msg_str = m.obj as String
            lastRegStatus = msg_str
        } else if (m.what == MSG_TYPE.INCOMING_CALL) {

            /* Incoming call */
            val call = m.obj as MyCall
            val prm = CallOpParam()

            /* Only one call at anytime */if (currentCall != null) {
                /*
                prm.setStatusCode(pjsip_status_code.PJSIP_SC_BUSY_HERE);
                try {
                call.hangup(prm);
                } catch (Exception e) {}
                */
                // TODO: set status code
                call.delete()
                return true
            }

            /* Answer with ringing */prm.statusCode = pjsip_status_code.PJSIP_SC_RINGING
            try {
                call.answer(prm)
            } catch (e: java.lang.Exception) {
            }
            currentCall = call
            showCallActivity()
        } else if (m.what == MSG_TYPE.CHANGE_NETWORK) {
            app?.handleNetworkChange()
        } else {

            /* Message not handled */
            return false
        }

        return true
    }

    public fun notifyCallState() {
        notifyCallState(currentCall)
    }

    private fun stopNotificationService() {
//        clearActiveSipNotification()
        serviceCallback?.stopService()


    }

    fun hangupCall() {
        val prm = CallOpParam()
        prm.statusCode = pjsip_status_code.PJSIP_SC_DECLINE
        try {
            currentCall?.hangup(prm)
            serviceCallback?.onAnswer(false)
            clearActiveCallNotification()
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "Exception occured While Call Hangup${e.message}")
        }
    }

    fun stopSip() {
        val message = Message.obtain(handler, 0)
        message.sendToTarget()

    }

    private fun showCallActivity() {
        Log.d("SERVICE-->", "Incoming call.........")
        val prm = CallOpParam()
        prm.statusCode = pjsip_status_code.PJSIP_SC_OK
        try {
            currentCall?.answer(prm)

            serviceCallback?.onAnswer(true)

            val incomingCallNotification: Notification = showCallActiveNotification()
            startForeground(ACTIVE_CALL_NOTIFICATION_ID, incomingCallNotification)

            Thread.sleep(5000L)
            clearActiveCallNotification()
        } catch (e: Exception) {
            println(e.message)
        }
    }

    fun muteCall() {

        // Mute the call

    }

    override fun notifyRegState(code: Int, reason: String?, expiration: Long) {
        var msg_str = ""
        msg_str += if (expiration == 0L) "Unregistration" else "Registration"

        msg_str += if (code / 100 == 2) " successful" else " failed: $reason"

        val m = Message.obtain(handler, MSG_TYPE.REG_STATE, msg_str)
        m.sendToTarget()
    }

    override fun notifyIncomingCall(call: MyCall?) {
        val m = Message.obtain(handler, MSG_TYPE.INCOMING_CALL, call)
        m.sendToTarget()
    }

    override fun notifyCallState(call: MyCall?) {

        if (currentCall == null || call!!.id != currentCall?.id) return

        var ci: CallInfo? = null
        try {
            ci = call.info
        } catch (e: java.lang.Exception) {
        }

        if (ci == null) return

        val m = Message.obtain(handler, MSG_TYPE.CALL_STATE, ci)
        m.sendToTarget()
    }

    override fun notifyCallMediaState(call: MyCall?) {
        val m = Message.obtain(handler, MSG_TYPE.CALL_MEDIA_STATE, null)
        m.sendToTarget()
    }

    override fun notifyBuddyState(buddy: MyBuddy?) {
//        TODO("Not yet implemented")
    }

    override fun notifyChangeNetwork() {
//        TODO("Not yet implemented")
    }

    fun showSipActiveNotification(): Notification {
        val sipIntent = Intent(this, SipActivity::class.java)
        sipIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent =
            PendingIntent.getActivity(this, 12, sipIntent, PendingIntent.FLAG_IMMUTABLE)

        createChannel()

        var notificationBuilder: NotificationCompat.Builder?
        notificationBuilder = NotificationCompat.Builder(this, SIP_CHANNEL_ID)
            .setContentText("")
            .setContentTitle("SIP is Active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
//                .setSound(Uri.parse("android.resource://" + applicationContext.packageName + "/" + R.raw.ringtone))
        return notificationBuilder.build()
    }

    fun showCallActiveNotification(): Notification {
        val sipIntent = Intent(this, SipActivity::class.java)
        sipIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent =
            PendingIntent.getActivity(this, 12, sipIntent, PendingIntent.FLAG_IMMUTABLE)
        createChannel()
        var notificationBuilder: NotificationCompat.Builder?
        notificationBuilder = NotificationCompat.Builder(this, SIP_CALL_CHANNEL_ID)
            .setContentText("")
            .setContentTitle("Incoming Voice Call")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
//                .setSound(Uri.parse("android.resource://" + applicationContext.packageName + "/" + R.raw.ringtone))
            .setFullScreenIntent(pendingIntent, true)
        return notificationBuilder.build()
    }

    fun clearActiveCallNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ACTIVE_CALL_NOTIFICATION_ID)
    }

    fun clearActiveSipNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancel(SIP_ACTIVE_NOTIFICATION_ID)

    }

    inner class LocalBinder : Binder() {
        fun getInstance(): NotificationService {
            return this@NotificationService
        }
    }
}