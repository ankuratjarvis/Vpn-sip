package com.example.myapplication

import android.Manifest
import android.app.Activity
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
import android.os.RemoteException
import android.telecom.Call
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myapplication.common.Constants.SIP_DOMAIN
import com.example.myapplication.common.Constants.SIP_PASSWORD
import com.example.myapplication.common.Constants.SIP_USERNAME
import com.example.myapplication.common.Constants.VPN_PASSWORD
import com.example.myapplication.common.Constants.VPN_USERNAME
import com.example.myapplication.common.DisplayLogs
import com.example.myapplication.common.isAppInForeground
import com.example.myapplication.common.netCheck
import com.example.myapplication.databinding.FragmentSipBinding
import com.example.myapplication.model.Server
import com.example.myapplication.model.UserCredentials
import com.example.myapplication.viewmodels.SipViewModel
import com.mizuvoip.jvoip.SIPNotification
import de.blinkt.openvpn.OpenVpnApi
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.OpenVPNThread
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


class SipFragment : Fragment() {
    private val TAG = SipFragment::class.java.simpleName


    var vpnStart = false
    lateinit var server: Server
    lateinit var binding: FragmentSipBinding
    lateinit var viewModel: SipViewModel


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        Log.d(TAG, "Fragment---> $TAG Created")
        binding = FragmentSipBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        viewModel = (activity as MainActivity).viewModel
        server = Server("bangalore.ovpn", VPN_USERNAME, VPN_PASSWORD)
        initViews()
        // Checking is vpn already running or not
        isServiceRunning()

        VpnStatus.initLogCache(requireActivity().cacheDir)

    }

    private fun initViews() {


        binding.username.setText(SIP_USERNAME)
        binding.domain.setText(SIP_DOMAIN)
        binding.password.setText(SIP_PASSWORD)


        binding.stopSIP.isEnabled = false
        binding.logTextView.movementMethod = ScrollingMovementMethod()

        initListeners()
    }

    private fun initListeners() {
        binding.startSipStack.setOnClickListener {
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


        viewModel.notificationStatus.observe(viewLifecycleOwner) {
            Log.d(TAG, "*********${it.status}*********")
            if (it.status == SIPNotification.Status.STATUS_CALL_RINGING) {
                /*viewModel.job.launch(Dispatchers.Main) {
                    viewModel.acceptCall(it.getLine())
                    showCallActiveFragment()

                }*/

            } else if (it.status == SIPNotification.Status.STATUS_CALL_CONNECT) {

            }
        }

        viewModel.status.observe(viewLifecycleOwner) {
//            if (it == "Incoming call connected") {
//                showCallActiveFragment()
//            }
            /*var newStr = ""
            if(it.contains("-1,Speaking")){
                var time = it.split("(")
               var str =  time[1]
                if(str.contains("sec")){
                    newStr =  str.removeRange(str.length-1,str.length-5)
                }else{
                    newStr = str.removeRange(str.length-1,str.length-2)
                }
                Log.d(TAG, "****Call Status***  - <> -  $newStr")

            }*/
            binding.logTextView.DisplayLogs(it)
        }
        binding.vpnBtn.setOnClickListener {
            /* if (vpnStart) {
                 confirmDisconnect()
             } else {
                 prepareVpn()
             }*/

            showCallActiveFragment()
        }

        binding.endCallButton.setOnClickListener{

            viewModel.endCall()
        }

    }

    private fun showCallActiveFragment() {
        (activity as MainActivity).addFragment<CallFragment>(true)
    }

    private fun confirmDisconnect() {
        val builder = AlertDialog.Builder(
            requireActivity()
        )
        builder.setMessage(requireActivity().getString(R.string.connection_close_confirm))
        builder.setPositiveButton(
            requireActivity().getString(R.string.yes)
        ) { dialog, id -> stopVpn() }
        builder.setNegativeButton(
            requireActivity().getString(R.string.no)
        ) { dialog, id ->
            // User cancelled the dialog
        }

        // Create the AlertDialog
        val dialog = builder.create()
        dialog.show()
    }

    private fun prepareVpn() {
        if (!vpnStart) {
            if (getInternetStatus()) {

                // Checking permission for network monitor
                val intent = VpnService.prepare(requireContext())
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
        return netCheck(requireActivity())
    }

    private fun isServiceRunning() {
        setStatus(OpenVPNService.getStatus())
    }

    private fun startVpn() {
        try {
            // .ovpn file
            val conf = requireActivity().assets.open(server.ovpn)
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
                requireContext(), config, "India", server.username, server.password
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

    private fun stopSip() {
        viewModel.stopSip()
        binding.domain.isEnabled = true
        binding.username.isEnabled = true
        binding.password.isEnabled = true


        binding.stopSIP.isEnabled = false
        binding.startSipStack.isEnabled = true

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

    private fun startSip() {
        val userCred = UserCredentials(
            binding.username.text.toString().trim(),
            binding.domain.text.toString().trim(),
            binding.password.text.toString().trim()
        )
        viewModel.startSipStack(requireContext(), userCred){
                if(it.status == SIPNotification.Status.STATUS_CALL_RINGING){
                    showCallActiveFragment()
                }


        }


        binding.domain.isEnabled = false
        binding.username.isEnabled = false
        binding.password.isEnabled = false
//
        binding.stopSIP.isEnabled = true
        binding.startSipStack.isEnabled = false
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    private fun status(status: String) {
        when (status) {
            "connect" -> {
                binding.vpnBtn.text = requireContext().getString(R.string.connect)
            }

            "connecting" -> {
                binding.vpnBtn.text = requireContext().getString(R.string.connecting)
            }

            "connected" -> {
                binding.vpnBtn.text = requireContext().getString(R.string.disconnect)
            }

            "tryDifferentServer" -> {
                binding.vpnBtn.text = requireContext().getString(R.string.try_different_server)

            }

            "loading" -> {
                binding.vpnBtn.text = requireContext().getString(R.string.loading_server)
            }

            "invalidDevice" -> {
                binding.vpnBtn.text = requireContext().getString(R.string.invalid_device)
            }

            "authenticationCheck" -> {
                binding.vpnBtn.text = requireContext().getString(R.string.authentication_checking)
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
        LocalBroadcastManager.getInstance(requireActivity())
            .registerReceiver(broadcastReceiver, IntentFilter("connectionState"))

        super.onResume()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
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
                context?.getSystemService(NotificationManager::class.java) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, CallFragment::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(requireContext(), CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentTitle("Incoming Call")
            .setContentText("Incoming Call From ....")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
        val notificationManager = NotificationManagerCompat.from(requireContext())
        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
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
}