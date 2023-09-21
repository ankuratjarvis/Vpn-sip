package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.RemoteException
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myapplication.common.Constants
import com.example.myapplication.common.DisplayLogs
import com.example.myapplication.common.netCheck
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.model.Server
import com.example.myapplication.model.UserCredentials
//import com.example.myapplication.pjsip.SipMainActivity.MSG_TYPE
import com.mizuvoip.jvoip.SIPNotification
import com.mizuvoip.jvoip.SIPNotificationListener
import com.mizuvoip.jvoip.SipStack
import de.blinkt.openvpn.OpenVpnApi
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.OpenVPNThread
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


class MainActivity : AppCompatActivity() {
    val TAG = MainActivity::class.java.simpleName

    var vpnStart = false
    lateinit var server: Server
    lateinit var sipStack: SipStack

    lateinit var fragmentManager: FragmentManager
    lateinit var binding: ActivityMainBinding


    var job = lifecycleScope


    var isMute = false

    var inBackground = false

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        registerActivityLifecycleCallbacks(lifecycleCallbackListener)
        checkPermissions()
        server = Server("bangalore.ovpn", Constants.VPN_USERNAME, Constants.VPN_PASSWORD)
        fragmentManager = supportFragmentManager
//        if (savedInstanceState == null) {
//            addFragment<SipFragment>()
//        }
        isServiceRunning()

        VpnStatus.initLogCache(cacheDir)
        initViews()
    }

    private fun initViews() {

        binding.username.setText(Constants.SIP_USERNAME)
        binding.domain.setText(Constants.SIP_DOMAIN)
        binding.password.setText(Constants.SIP_PASSWORD)
        binding.stopSIP.isEnabled = false
        binding.logTextView.movementMethod = ScrollingMovementMethod()

        initListeners()
    }

    private fun initListeners() {
        binding.startSipStack.setOnClickListener {
//            startPjSip()
            if (binding.username.text.isNotEmpty() && binding.domain.text.isNotEmpty() && binding.password.text.isNotEmpty()) {
                startSip()
                showToast("Starting SIP Stack")
            } else {
                showToast("Please enter required Details")
            }
        }

        binding.stopSIP.setOnClickListener {
            stopSip()
        }


        /*   viewModel.notificationStatus.observe(this) {
               Log.d(TAG, "*********${it.status}*********")
               if (it.status == SIPNotification.Status.STATUS_CALL_RINGING) {
                   Log.d(TAG, "Incoming call ringing  from ${it.peerdisplayname}")
                   viewModel.acceptCall(it.getLine())
                   runOnUiThread {
                       showCallActiveFragment()

                   }


               } else if (it.status == SIPNotification.Status.STATUS_CALL_CONNECT) {
                   Log.d(TAG, "Incoming call Connected  from ${it.peerdisplayname}")
   //                activity?.runOnUiThread {

   //                }
               }
           }

           viewModel.status.observe(this) {
   //            if (it == "Incoming call connected") {
   //                showCallActiveFragment()
   //            }
               *//*var newStr = ""
            if(it.contains("-1,Speaking")){
                var time = it.split("(")
               var str =  time[1]
                if(str.contains("sec")){
                    newStr =  str.removeRange(str.length-1,str.length-5)
                }else{
                    newStr = str.removeRange(str.length-1,str.length-2)
                }
                Log.d(TAG, "****Call Status***  - <> -  $newStr")

            }*//*
            binding.logTextView.DisplayLogs(it)
        }*/
        binding.vpnBtn.setOnClickListener {
            if (vpnStart) {
                confirmDisconnect()
            } else {
                prepareVpn()
            }
        }
        binding.endCallButton.setOnClickListener {

//            viewModel.endCall()
            sipStack.Hangup()
            binding.logTextView.visibility = View.VISIBLE
            binding.activeCallContainer.visibility = View.GONE
        }

        fragmentManager.addFragmentOnAttachListener { fragmentManager, fragment ->

            (fragment as CallFragment).addListener {
                showToast("Call ended")
                endSipCall()
                popBackStack()
            }
            (fragment as CallFragment).addMuteCallListener {
                if (isMute) {
                    isMute = false
                    muteUnmuteCall(false)
                } else {
                    isMute = true
                    muteUnmuteCall(true)
                }
            }
        }

    }

    fun startPjSip(){
         /* val endpoint = Endpoint()
              endpoint.libCreate()
              val epConfig = EpConfig()
              endpoint.libInit(epConfig)


              val transport = TransportConfig()
              transport.port = 5060
              endpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP,transport)

              endpoint.libStart()

             val acc =  AccountConfig()
              acc.sipConfig = AccountSipConfig().apply {
              }*/
    }
    private fun showCallActiveFragment() {
        binding.fragmentContainer.visibility = View.VISIBLE
        addFragment<CallFragment>(true)

    }

    private fun isServiceRunning() {
        setStatus(OpenVPNService.getStatus())
    }

    fun setStatus(connectionState: String?) {
        Log.d(TAG, "VPN Status ---> $connectionState")
        if (connectionState != null) when (connectionState) {

            "DISCONNECTED" -> {
                status("connect")
                vpnStart = false
                OpenVPNService.setDefaultStatus()
                binding.logTV.text = ""
            }

            "CONNECTED" -> {
                vpnStart = true // it will use after restart this activity
                status("connected")
                binding.logTV.text = ""
            }

            "WAIT" -> binding.logTV.text = "Connection Status waiting for server connection!!"
            "AUTH" -> binding.logTV.text = "Connection Status server authenticating!!"
            "RECONNECTING" -> {
                status("connecting")
                binding.logTV.text = "Connection Status Reconnecting..."
            }

            "NONETWORK" -> binding.logTV.text = "Connection Status No network connection"
        }
    }

    private fun startVpn() {
        try {
            // .ovpn file
            val conf = assets.open(server.ovpn)
            Log.d("Sip Fragment", "File name----> ${server.ovpn}")
            val isr = InputStreamReader(conf)
            val br = BufferedReader(isr)
            var config = ""
            var line: String?
            while (true) {
                line = br.readLine()
                if (line == null) break
                config += """
                $line
                
                """.trimIndent()

            }
            br.readLine()
            OpenVpnApi.startVpn(
                this, config, "India", server.username, server.password
            )


            // Update log
//           binding.logTV.setText("Connecting...")
            vpnStart = true
        } catch (e: IOException) {
            Log.d(TAG, "Exception---> ${e.printStackTrace()}")
        } catch (e: RemoteException) {
            Log.d(TAG, "Exception---> ${e.printStackTrace()}")
        }
    }


    private fun startSip() {

//        job.launch(Dispatchers.IO) {
            val userCred = UserCredentials(
                binding.username.text.toString().trim(),
                binding.domain.text.toString().trim(),
                binding.password.text.toString().trim()
            )
//        viewModel.startSipStack(this, userCred)
            sipStack = SipStack()
            sipStack.Init(applicationContext)
//            sipStack.SetParameter("loglevel", "5")
            sipStack.SetParameter("serveraddress", userCred.serverAddress)
            sipStack.SetParameter("username", userCred.username)
            sipStack.SetParameter("password", userCred.password)

            sipStack.Start()

            sipStack.SetNotificationListener(object : SIPNotificationListener() {
                override fun onAll(e: SIPNotification?) {
                    binding.logTextView.DisplayLogs("${e?.notificationTypeText} ${e?.notification_string}")
                }

                override fun onRegister(e: SIPNotification.Register?) {
                    if (!e?.isMain!!) return  //we ignore secondary accounts here
                    when (e.getStatus()) {
                        SIPNotification.Register.STATUS_INPROGRESS -> binding.logTextView.DisplayLogs(
                            "InProgress"
                        )

                        SIPNotification.Register.STATUS_SUCCESS -> {
                            binding.logTextView.DisplayLogs("Registered successfully.")

                        }

                        SIPNotification.Register.STATUS_FAILED -> {
                            binding.logTextView.DisplayLogs("Register failed because " + e.getReason())


                        }

                        SIPNotification.Register.STATUS_UNREGISTERED -> {
                            binding.logTextView.DisplayLogs("Unregistered")

                        }

                    }
                }

                override fun onStatus(e: SIPNotification.Status) {

                    if (e?.getLine() == -1) return  //we are ignoring the global state here (but you might check only the global state instead or look for the particular lines separately if you must handle multiple simultaneous calls)

                    //log call state
                    if (e?.getStatus()!! >= SIPNotification.Status.STATUS_CALL_SETUP && e?.getStatus()!! <= SIPNotification.Status.STATUS_CALL_FINISHED) {
                        binding.logTextView.DisplayLogs("Call state is: " + e.statusText)

                    }

                    if (e.getStatus() == SIPNotification.Status.STATUS_CALL_RINGING && e.endpointType == SIPNotification.Status.DIRECTION_IN) {
                        binding.logTextView.DisplayLogs("Incoming call from " + e.peerDisplayname)
                        /*  job.launch(Dispatchers.Main) {
                              if (inBackground) {
                                  Log.d(TAG, "acamcashcaskjcbakjcbaskjcascbasjcb")
                                  showCallNotification()
                              }
                          }*/
                        sipStack.Accept(e.getLine())
//                        job.launch(Dispatchers.Main) {
//                            showCallActiveFragment()
//
//                        }

                    } else if (e.getStatus() == SIPNotification.Status.STATUS_CALL_CONNECT) {
                        binding.logTextView.DisplayLogs("Incoming call connected")

                    }

                }

                override fun onEvent(e: SIPNotification.Event?) {
                    binding.logTextView.DisplayLogs("Important event: " + e?.getText())

                }

                override fun onChat(e: SIPNotification.Chat?) {
                    binding.logTextView.DisplayLogs("Message from " + e?.peer + ": " + e?.getMsg())

                    sipStack.SendChat(-1, e?.peer, "Received")

                }
            })
//        }

        binding.domain.isEnabled = false
        binding.username.isEnabled = false
        binding.password.isEnabled = false

        binding.stopSIP.isEnabled = true
        binding.startSipStack.isEnabled = false
    }


    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun status(status: String) {
        when (status) {
            "connect" -> {
                binding.vpnBtn.text = getString(R.string.connect)
            }

            "connecting" -> {
                binding.vpnBtn.text = getString(R.string.connecting)
            }

            "connected" -> {
                binding.vpnBtn.text = getString(R.string.disconnect)
            }

            "tryDifferentServer" -> {
                binding.vpnBtn.text = getString(R.string.try_different_server)

            }

            "loading" -> {
                binding.vpnBtn.text = getString(R.string.loading_server)
            }

            "invalidDevice" -> {
                binding.vpnBtn.text = getString(R.string.invalid_device)
            }

            "authenticationCheck" -> {
                binding.vpnBtn.text = getString(R.string.authentication_checking)
            }
        }
    }

    private var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                setStatus(intent.getStringExtra("state"))
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
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
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateConnectionStatus(
        duration: String, lastPacketReceive: String, byteIn: String, byteOut: String
    ) {
        binding.durationTv.text = "Duration: $duration"
        binding.lastPacketReceiveTv.text = "Packet Received: $lastPacketReceive second ago"
        binding.byteInTv.text = "Bytes In: $byteIn"
        binding.byteOutTv.text = "Bytes Out: $byteOut"
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {

            //Permission granted, start the VPN
            startVpn()
        } else {
            showToast("Permission Deny !! ")
        }
    }

    override fun onResume() {
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter("connectionState"))

        super.onResume()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    private fun prepareVpn() {
        if (!vpnStart) {
            if (getInternetStatus()) {

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

    private fun getInternetStatus(): Boolean {
        return netCheck(this)
    }

    private fun stopVpn(): Boolean {
        try {
            stopSip()
            OpenVPNThread.stop()
            status("connect")
            vpnStart = false
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun stopSip() {
        sipStack.Stop()
//        viewModel.stopSip()
        binding.domain.isEnabled = true
        binding.username.isEnabled = true
        binding.password.isEnabled = true


        binding.stopSIP.isEnabled = false
        binding.startSipStack.isEnabled = true

    }

    private fun showCallNotification() {
        val CHANNEL_ID = "vpn_notification"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = descriptionText
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)

            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, CallFragment::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentTitle("Incoming Call")
            .setContentText("Incoming Call From ....")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
        val notificationManager = NotificationManagerCompat.from(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        notificationManager.notify(1001, builder.build())
    }


    inline fun <reified T : Fragment> addFragment(add: Boolean? = false) {

        fragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(
                R.anim.slide_in,
                R.anim.fade_out,
                R.anim.fade_in,
                R.anim.slide_out
            )
            add<T>(binding.fragmentContainer.id)



            if (add == true) {
                addToBackStack("sip")
            }
        }

    }

    fun popBackStack() {

        fragmentManager.popBackStackImmediate()
        binding.fragmentContainer.visibility = View.GONE
    }

    private fun confirmDisconnect() {
        val builder = AlertDialog.Builder(
            this
        )
        builder.setMessage(getString(R.string.connection_close_confirm))
        builder.setPositiveButton(
            getString(R.string.yes)
        ) { dialog, id -> stopVpn() }
        builder.setNegativeButton(
            getString(R.string.no)
        ) { dialog, id ->
            // User cancelled the dialog
        }

        // Create the AlertDialog
        val dialog = builder.create()
        dialog.show()
    }


    fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= 23 && this.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            //we need RECORD_AUDIO permission before to make/receive any call
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                555
            )
            /*
                better permission management: https://developer.android.com/training/permissions/requesting
                some example code:
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA))
                        {
                            Toast tst = Toast.makeText(ctx, "Audio record permission required", Toast.LENGTH_LONG);
                            tst.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                            tst.show();
                            Handler handlerRating = new Handler();
                            handlerRating.postDelayed(new Runnable()
                            {
                                public void run()
                                {
                                        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 555);
                                }
                            }, 1000);
                        }else
                        {
                            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 555);
                        }

                        //put this function outside:
                        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
                        {
                            switch (requestCode) {
                                case 555:{
                                        // If request is cancelled, the result arrays are empty.
                                        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                                        {
                                            //continue from here
                                        }else
                                        {
                                            //display failure
                                        }
                                }
                            }
                        }

            */
            /* startActivity<MainActivity> {

             }*/
        }
    }

    fun endSipCall() {
        sipStack.Hangup()
    }

    fun muteUnmuteCall(flag: Boolean) {
        sipStack.Mute(-1, flag)

    }

    /*private inline fun <reified T:Any> newIntent(context: Context) = Intent(context,T::class.java)
    private inline fun <reified T:Any> startActivity(noinline init:Intent.() ->Unit={}){
        val intent = newIntent<T>(this)
        intent.init()
        startActivity(intent)
    }*/
    private val lifecycleCallbackListener = object : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            inBackground = false
        }

        override fun onActivityStarted(activity: Activity) {
            inBackground = false

        }

        override fun onActivityResumed(activity: Activity) {
            if (inBackground) {
                inBackground = false
                job.launch(Dispatchers.Main) {
                }

            }


        }

        override fun onActivityPaused(activity: Activity) {
            inBackground = false

        }

        override fun onActivityStopped(activity: Activity) {
            inBackground = true

        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

        }

        override fun onActivityDestroyed(activity: Activity) {

        }

    }

}