package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateIntSizeAsState
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myapplication.common.Constants
import com.example.myapplication.common.Storage
import com.example.myapplication.common.StorageImpl
import com.example.myapplication.common.netCheck
import com.example.myapplication.model.Server
import com.example.myapplication.service.NotificationService
import com.example.myapplication.utils.MyAccount
import com.example.myapplication.utils.MyCall
import com.example.myapplication.utils.ServiceCallback
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.karumi.dexter.listener.single.PermissionListener
import de.blinkt.openvpn.OpenVpnApi
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.OpenVPNThread
import de.blinkt.openvpn.core.VpnStatus
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.pjsip_status_code
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.security.Permission

class SipActivity : Activity(), ServiceCallback {
    private var myService: NotificationService? = null
    private val lastRegStatus = ""
    private var durationTv: TextView? = null
    private var lastPacketReceiveTv: TextView? = null
    private var logTV: TextView? = null
    private var byteInTv: TextView? = null
    private var byteOutTv: TextView? = null
    private lateinit var vpnBtn: Button
    private lateinit var startSipStack: Button
    private lateinit var stopSipStack: Button
    private lateinit var endCall: Button
    lateinit var accDialog: ImageButton
    private var activeCallView: RelativeLayout? = null
    private var server: Server? = null
    lateinit var logTextView: TextView
    private var foregroundServiceIntent: Intent? = null
    private var storage: Storage? = null

    private val permissionList =
        arrayListOf(Manifest.permission.RECORD_AUDIO/*,Manifest.permission.BIND_VPN_SERVICE,Manifest.permission.CALL_PHONE*/)


    private var isServiceRunning: Boolean = false
    private var isCallActive: Boolean = false
    private var vpnStart: Boolean = false
    private var isServiceBound: Boolean = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        /* if (intent != null) {
            stopService(foregroundServiceIntent);
            String data = intent.getStringExtra(Constants.INCOMING_CALL);
            Log.d(TAG, "On New Intent---->" + data);

        }*/
    }

    companion object {
        lateinit var instance: SipActivity
        private const val TAG = "SipActivity"
        var currentCall: MyCall? = null
        var account: MyAccount? = null


    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_pjsip)
        checkPermissions()

        instance = this

        initViews()
        storage = StorageImpl(this)
//        storage!!.delete(Constants.IS_VPN_ACTIVE)
        foregroundServiceIntent = Intent(this, NotificationService::class.java)
        isServiceRunning()

        if (java.lang.Boolean.TRUE == storage!!.readData(Constants.SERVICE)) {
            isServiceRunning = true
        }
        if (java.lang.Boolean.TRUE == storage!!.readData(Constants.IS_CALL_ACTIVE)) {
            bindService(foregroundServiceIntent!!, serviceConnection, BIND_AUTO_CREATE)
            isCallActive = true
            storage!!.clearData()
            onAnswer(true)
        } else {
            isCallActive = false
            onAnswer(false)
        }

        if (isServiceRunning) {
            setStopAndStartSipDisabled(false)
        }

    }

    private fun initViews() {
        vpnBtn = findViewById(R.id.vpn_Btn)
        durationTv = findViewById(R.id.duration_Tv)
        logTV = findViewById(R.id.log_TV)
        lastPacketReceiveTv = findViewById(R.id.lastPacketReceive_Tv)
        byteInTv = findViewById(R.id.byteIn_Tv)
        byteOutTv = findViewById(R.id.byteOut_Tv)
        startSipStack = findViewById(R.id.startSipStack_btn)
        stopSipStack = findViewById(R.id.stopSIP)

        logTextView = findViewById(R.id.log_Text_View)
        accDialog = findViewById(R.id.buttonEditBuddy)
        activeCallView = findViewById(R.id.active_call_Container)
        endCall = findViewById(R.id.endCallButton)

        logTextView.movementMethod = ScrollingMovementMethod()

        initListeners()
    }

    private fun initListeners() {
        vpnBtn.setOnClickListener {
            initiateVpn()
        }
        startSipStack.setOnClickListener {
            logTextView.text = ""
            dlgAccountSetting()
        }
        stopSipStack.setOnClickListener {
            if (isServiceRunning) {
                myService!!.stopSip()
                setStopAndStartSipDisabled(false)
            } else {
                showToast("Service Not Active Yet...!")
            }
        }
        endCall.setOnClickListener {
            if (myService != null) {
                myService!!.notifyCallState()
            }
        }
        accDialog.setOnClickListener { dlgAccountSetting() }

    }

    private fun initiateVpn() {
        if (!netCheck(this)) {
            showToast("check Your internet")
            return
        }
        if (storage?.readData("permission")!!) {
            if (vpnStart) {

                confirmDisconnect()
            } else {
                prepareVpn()
            }
        } else {
            checkPermissions()
//            showToast("Permissions are Required")
        }

    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            val binder = iBinder as NotificationService.LocalBinder
            myService = binder.getInstance()
            Log.d(TAG, "Invoking Service Callback")
            if (myService != null) {
                myService!!.registerCallback(this@SipActivity)
                showToast("Service connected")
                Log.d(TAG, "Service Callback Registered")
            }
            isServiceBound = true
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            isServiceBound = false
        }
    }

    private fun dlgAccountSetting(
        id: String?,
        proxy: String?,
        username: String?,
        password: String?
    ) {
        foregroundServiceIntent = Intent(this, NotificationService::class.java)
        val bundle = Bundle()
        bundle.putString("domain_id", id)
        bundle.putString("proxy", proxy)
        bundle.putString("username", username)
        bundle.putString("password", password)
        foregroundServiceIntent!!.putExtra("data_bundle", bundle)
        startService(foregroundServiceIntent)
        isServiceRunning = true
        Log.d(TAG, "Initial Service Started")
        bindService(foregroundServiceIntent!!, serviceConnection, BIND_AUTO_CREATE)
        setStopAndStartSipDisabled(true)
    }

    private fun dlgAccountSetting() {
        val li = LayoutInflater.from(this)
        val view = li.inflate(R.layout.dlg_account_config, null)
        if (lastRegStatus.length != 0) {
            val tvInfo = view.findViewById<View>(R.id.textViewInfo) as TextView
            tvInfo.text = "Last status: $lastRegStatus"
        }
        val adb = AlertDialog.Builder(this)
        adb.setView(view)
        adb.setTitle("Account Settings")
        val etId = view.findViewById<View>(R.id.editTextId) as EditText
        val etReg = view.findViewById<View>(R.id.editTextRegistrar) as EditText
        val etProxy = view.findViewById<View>(R.id.editTextProxy) as EditText
        val etUser = view.findViewById<View>(R.id.editTextUsername) as EditText
        val etPass = view.findViewById<View>(R.id.editTextPassword) as EditText

        etId.setText(Constants.VPN_DOMAIN_ID)
        etProxy.setText(Constants.VPN_AGENT_DOMAIN)
        etReg.setText(Constants.VPN_AGENT_DOMAIN)
        etUser.setText(Constants.VPN_AGENT_USERNAME)
        etPass.setText(Constants.VPN_AGENT_PASSWORD)
        adb.setCancelable(false)
        adb.setPositiveButton(
            "OK"
        ) { dialog: DialogInterface?, id: Int ->
            val acc_id = etId.text.toString()
            val registrar = etReg.text.toString()
            val proxy = etProxy.text.toString()
            val username = etUser.text.toString()
            val password = etPass.text.toString()
            dlgAccountSetting(acc_id, proxy, username, password)
        }
        adb.setNegativeButton(
            "Cancel"
        ) { dialog: DialogInterface, id: Int -> dialog.cancel() }
        val ad = adb.create()
        ad.show()
    }

    private fun isServiceRunning() {
        setStatus(OpenVPNService.getStatus())
    }

    fun setStatus(connectionState: String?) {
        if (connectionState != null) when (connectionState) {
            "DISCONNECTED" -> {
                status("connect")
                vpnStart = false
                OpenVPNService.setDefaultStatus()
                logTV!!.text = ""
            }

            "CONNECTED" -> {
                vpnStart = true // it will use after restart this activity
                status("connected")
                logTV!!.text = ""
            }

            "WAIT" -> logTV!!.text = "Connection Status waiting for server connection!!"
            "AUTH" -> logTV!!.text = "Connection Status server authenticating!!"
            "RECONNECTING" -> {
                status("connecting")
                logTV!!.text = "Connection Status Reconnecting..."
            }

            "NONETWORK" -> logTV!!.text = "Connection Status No network connection"
        }
    }

    private fun startVpn() {
        server = Server("bangalore.ovpn", Constants.VPN_USERNAME, Constants.VPN_PASSWORD)
        isServiceRunning()
        VpnStatus.initLogCache(cacheDir)
        try {
            // .ovpn file
            val conf = assets.open(server!!.ovpn)
            Log.d("Sip Fragment", "File name----> \${server.ovpn}")
            val isr = InputStreamReader(conf)
            val br = BufferedReader(isr)
            val config = StringBuilder()
            var line: String?
            while (true) {
                line = br.readLine()
                if (line == null) break
                config.append(line).append("\n")
            }
            br.readLine()
            OpenVpnApi.startVpn(
                this, config.toString(), "India", server!!.username, server!!.password
            )


            // Update log
//            logTV.setText("Connecting...");
            vpnStart = true
        } catch (e: IOException) {
            Log.d(TAG, "Exception---> " + e.message)
        } catch (e: RemoteException) {
            Log.d(TAG, "Exception--->" + e.message)
        }
    }

    private fun status(status: String) {
        when (status) {
            "connect" -> {
                vpnBtn.text = getString(R.string.connect)
            }

            "connecting" -> {
                vpnBtn.text = getString(R.string.connecting)
            }

            "connected" -> {
                vpnBtn.text = getString(R.string.disconnect)
            }

            "tryDifferentServer" -> {
                vpnBtn.text = getString(R.string.try_different_server)
            }

            "loading" -> {
                vpnBtn.text = getString(R.string.loading_server)
            }

            "invalidDevice" -> {
                vpnBtn.text = getString(R.string.invalid_device)
            }

            "authenticationCheck" -> {
                vpnBtn.text = getString(R.string.authentication_checking)
            }
        }
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                setStatus(intent.getStringExtra("state"))
            } catch (e: Exception) {
                Log.d(TAG, e.message!!)
            }
            try {
                var duration = intent.getStringExtra("duration")
                var lastPacketReceive = intent.getStringExtra("lastPacketReceive")
                var byteIn = intent.getStringExtra("byteIn")
                var byteOut = intent.getStringExtra("byteOut")
                if (duration == null) duration = "00:00:00"
                if (lastPacketReceive == null) lastPacketReceive = "0"
                if (byteIn == null) byteIn = " "
                if (byteOut == null) byteOut = " "
                updateConnectionStatus(duration, lastPacketReceive, byteIn, byteOut)
            } catch (e: Exception) {
                Log.d(TAG, e.message!!)
            }
        }
    }

    fun updateConnectionStatus(
        duration: String, lastPacketReceive: String, byteIn: String, byteOut: String
    ) {
        durationTv!!.text = "Duration: $duration"
        lastPacketReceiveTv!!.text = "Packet Received: $lastPacketReceive second ago"
        byteInTv!!.text = "Bytes In: $byteIn"
        byteOutTv!!.text = "Bytes Out:$byteOut"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {

            //Permission granted, start the VPN
            startVpn()
        } else {
            showToast("Permission Deny !! ")
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter("connectionState"))
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onPause()
    }


    private fun prepareVpn() {
        if (!vpnStart) {
            if (internetStatus) {

                // Checking permission for network monitor
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 1)
                } else {
                    Log.d(TAG, "Starting VPN--->")
                    startVpn()
                } //have already permission

                // Update confection status
                status("connecting")
            } else {

                // No internet connection available
                showToast("you have no internet connection !!")
            }
        } else if (stopVpn()) {

            // VPN is stopped, show a Toast message.
            showToast("Disconnect Successfully")
        }
    }

    private val internetStatus: Boolean
        get() = netCheck(this)

    private fun stopVpn(): Boolean {
        try {
//        stopSip();
            OpenVPNThread.stop()
            if (isServiceRunning) {
                stopService()

            }
            status("connect")
            vpnStart = false
            return true
        } catch (e: Exception) {
            Log.d(TAG, e.message!!)
        }
        return false
    }

    private fun checkPermissions() {
        Dexter.withContext(this).withPermissions(permissionList).withListener(permissionListener)
            .check()
    }

    private fun confirmDisconnect() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(getString(R.string.connection_close_confirm))
        builder.setPositiveButton("yes") { _, which -> stopVpn() }
        builder.setNegativeButton("no") { _: DialogInterface?, _: Int -> }


        // Create the AlertDialog
        val dialog = builder.create()
        dialog.show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }


    private fun setStopAndStartSipDisabled(flag: Boolean) {
        stopSipStack.isEnabled = flag
        startSipStack.isEnabled = !flag
    }

    private val permissionListener = object : MultiplePermissionsListener {
        override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
            val isGranted = p0?.areAllPermissionsGranted() ?: false
            if (isGranted) {
                storage?.saveData("permission", true)
            }
        }

        override fun onPermissionRationaleShouldBeShown(
            p0: MutableList<PermissionRequest>?,
            p1: PermissionToken?
        ) {
            p1?.continuePermissionRequest()
        }


    }

    override fun onAnswer(isActive: Boolean) {
        Log.d(TAG, "OnAnswered Triggered")
        if (isActive) {
            isCallActive = true
            activeCallView!!.visibility = View.VISIBLE
            logTextView.visibility = View.GONE
        } else {
            isCallActive = false
            activeCallView!!.visibility = View.GONE
            logTextView.visibility = View.VISIBLE
        }
    }

    override fun stopService() {

        myService!!.stopSip()
        isServiceRunning = false
        isCallActive = false
        logTextView.text = " "
        stopService(foregroundServiceIntent)
    }


    fun log(msg: String) {
        runOnUiThread {
            logTextView.append(
                """
    $msg
    
    
    """.trimIndent()
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG,"kj sjsjdjkjkjkjkjdfkjkj kj")


        if (isCallActive) {
            storage!!.saveData(Constants.IS_CALL_ACTIVE, true)
        } else {
            storage!!.saveData(Constants.IS_CALL_ACTIVE, false)
        }
        if (isServiceRunning) {
            storage!!.saveData(Constants.SERVICE, true)
            unbindService(serviceConnection)
        }
        if (!vpnStart && !isCallActive && !isServiceRunning) {
            storage!!.clearData()

        }
    }


}