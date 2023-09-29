package com.example.myapplication

import android.Manifest
import android.R.attr.data
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.net.VpnService
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
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import de.blinkt.openvpn.OpenVpnApi
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.OpenVPNThread
import de.blinkt.openvpn.core.VpnStatus
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


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
    lateinit var scrollView: ScrollView
    lateinit var loadVpnFile: ImageView
    private var activeCallView: RelativeLayout? = null
    private var server: Server? = null
    lateinit var logTextView: TextView
    private var foregroundServiceIntent: Intent? = null
    private var storage: Storage? = null
    private var fileUri: Uri? = null
    private val permissionList =
        arrayListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE/*,Manifest.permission.BIND_VPN_SERVICE,Manifest.permission.CALL_PHONE*/
        )


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
//        storage!!.clearIsServiceRunning()
//        storage!!.clearIsCallActive()
        foregroundServiceIntent = Intent(this, NotificationService::class.java)
        setStopAndStartSipDisabled(false)
        if (storage!!.readFileUri() != null) {
            fileUri = Uri.parse(storage!!.readFileUri())
        } else {
            fileUri = null
            vpnBtn.isEnabled = false
        }

        isVpnServiceRunning()

        if (java.lang.Boolean.TRUE == storage!!.readData(Constants.SERVICE)) {
            isServiceRunning = true
            setStopAndStartSipDisabled(true)

            bindService(foregroundServiceIntent!!, serviceConnection, BIND_AUTO_CREATE)

        }
        if (java.lang.Boolean.TRUE == storage!!.readData(Constants.IS_CALL_ACTIVE)) {
            isCallActive = true
            storage!!.clearIsCallActive()
            storage!!.clearIsServiceRunning()
            onAnswer(true)
        } else {

            isCallActive = false
            onAnswer(false)
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
        loadVpnFile = findViewById(R.id.load_vpn_file)
        scrollView = findViewById(R.id.log_container)

        logTextView.movementMethod = ScrollingMovementMethod()

        initListeners()
    }

    private fun initListeners() {
        vpnBtn.setOnClickListener {
//            initiateVpn()
            if(vpnStart){
                initiateVpn()
            }else {
                showVpnConnectDialog()

            }
        }
        startSipStack.setOnClickListener {
            logTextView.text = ""
            dlgAccountSetting()
        }
        stopSipStack.setOnClickListener {
            if (isServiceRunning) {
                myService!!.stopSip()
                isServiceRunning = false
                setStopAndStartSipDisabled(false)
            } else {
                showToast("Service Not Active Yet...!")
            }
        }
        endCall.setOnClickListener {
            if (myService != null) {
                myService!!.hangupCall()
            }
        }
        accDialog.setOnClickListener { dlgAccountSetting() }

        loadVpnFile.setOnClickListener {
            fileChooser()
        }

    }

    private fun initiateVpn(username:String?="",password: String?="") {
        if (!netCheck(this)) {
            showToast("check Your internet")
            return
        }
        if (storage?.readData("permission")!!) {
            if (vpnStart) {

                confirmDisconnect()
            } else {
                prepareVpn(username!!,password!!)
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

//        etId.setText(Constants.VPN_AGENT_USERNAME)
//        etProxy.setText(Constants.VPN_AGENT_DOMAIN)
//        etReg.setText(Constants.VPN_AGENT_DOMAIN)
//        etUser.setText(Constants.VPN_AGENT_USERNAME)
//        etPass.setText(Constants.VPN_AGENT_PASSWORD)
        adb.setCancelable(false)
        adb.setPositiveButton(
            "OK"
        ) { dialog: DialogInterface?, id: Int ->
            val acc_id = "sip:${etUser.text}@${etId.text}"
            val registrar = "sip:${etId.text}"
            val proxy = "sip:${etId.text}"
            val username = etUser.text.toString()
            val password = etPass.text.toString()
            Log.d(TAG,"$acc_id $registrar $proxy $username $password")
            dlgAccountSetting(acc_id, proxy, username, password)
        }
        adb.setNegativeButton(
            "Cancel"
        ) { dialog: DialogInterface, id: Int -> dialog.cancel() }
        val ad = adb.create()
        ad.show()
    }

    fun showVpnConnectDialog(){
        val li = LayoutInflater.from(this)
        val view = li.inflate(R.layout.dialog_vpn_setting, null)

        val adb = AlertDialog.Builder(this)
        adb.setView(view)
        adb.setTitle("Account Settings")
        val vpnUsername = view.findViewById<EditText>(R.id.username_vpn_et)
        val vpnPassword = view.findViewById<EditText>(R.id.password_vpn_et)


        adb.setCancelable(false)
        adb.setPositiveButton(
            "OK"
        ) { dialog: DialogInterface?, id: Int ->
            if(vpnUsername.text.isEmpty() || vpnPassword.text.isEmpty()){
                showToast("Enter required Fields ")

            }else{
                initiateVpn(vpnUsername.text.trim().toString(),vpnPassword.text.trim().toString())
            }
        }
        adb.setNegativeButton(
            "Cancel"
        ) { dialog: DialogInterface, id: Int -> dialog.cancel() }
        val ad = adb.create()
        ad.show()
    }

    private fun isVpnServiceRunning() {
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

    private fun startVpn(username: String? ="",password: String?="") {
        server = Server("bangalore.ovpn", username!!, password!!)
        isVpnServiceRunning()
        VpnStatus.initLogCache(cacheDir)
        try {
            // .ovpn file
//            val fis = FileInputStream(fileUri)

            val ois = contentResolver.openInputStream(fileUri!!)
//            val conf = assets.open(server!!.ovpn)
            val isr = InputStreamReader(ois)
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
            ois?.close()
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
        Log.d(TAG,"on Activity Result")

        if (resultCode == RESULT_OK && requestCode==112) {

            //Permission granted, start the VPN
            startVpn()
        }
        if ( requestCode == 111) {

            if (data.data?.path?.endsWith(".ovpn")!!) {
                fileUri = data.data
                vpnBtn.isEnabled = true
                val takeFlags: Int = (data.flags
                        and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))

                contentResolver.takePersistableUriPermission(
                    fileUri!!,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

            } else {
                showToast("Please load ovpn file only")
            }
            Log.d(TAG, "File Path --->${fileUri}")
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


    private fun prepareVpn(username: String,password: String) {
        if (!vpnStart) {
            if (internetStatus) {

                // Checking permission for network monitor
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 112)
                } else {
                    Log.d(TAG, "Starting VPN--->")
                    startVpn(username,password)
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
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun fileChooser() {
        val FILE_REQ_CODE = 111
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, FILE_REQ_CODE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (vpnStart && fileUri != null) {
            storage!!.saveData(Constants.FILE_URI, fileUri.toString())
        }
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
            storage!!.clearIsCallActive()
            storage!!.clearIsServiceRunning()

        }
    }


}