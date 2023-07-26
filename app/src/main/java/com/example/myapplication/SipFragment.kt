package com.example.myapplication

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.os.RemoteException
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myapplication.common.Constants.SIP_DOMAIN
import com.example.myapplication.common.Constants.SIP_PASSWORD
import com.example.myapplication.common.Constants.SIP_USERNAME
import com.example.myapplication.common.Constants.VPN_PASSWORD
import com.example.myapplication.common.Constants.VPN_USERNAME
import com.example.myapplication.common.DisplayLogs
import com.example.myapplication.common.netCheck
import com.example.myapplication.databinding.FragmentSipBinding
import com.example.myapplication.model.Server
import com.example.myapplication.model.UserCredentials
import com.example.myapplication.viewmodels.SipViewModel
import de.blinkt.openvpn.OpenVpnApi
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.OpenVPNThread
import de.blinkt.openvpn.core.VpnStatus
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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        Log.d(TAG, "Fragment---> $TAG Created")
        binding = FragmentSipBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = (activity as MainActivity).viewModel
        server = Server("bangalore.ovpn", VPN_USERNAME, VPN_PASSWORD)
        initViews()
        // Checking is vpn already running or not
        isServiceRunning()

        VpnStatus.initLogCache(activity!!.cacheDir)

    }

    private fun initViews() {


        binding.username.setText(SIP_USERNAME)
        binding.domain.setText(SIP_DOMAIN)
        binding.password.setText(SIP_PASSWORD)


        binding.callBtn.isEnabled = false
        binding.stopSIP.isEnabled = false
        binding.numberET.isEnabled = false
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
            viewModel.stopSip()
            binding.domain.isEnabled = true
            binding.username.isEnabled = true
            binding.password.isEnabled = true

            binding.numberET.setText("")
            binding.numberET.isEnabled = false

            binding.stopSIP.isEnabled = false
            binding.startSipStack.isEnabled = true
            binding.callBtn.isEnabled = false

        }

        binding.callBtn.setOnClickListener {
            if (binding.numberET.text.isNotEmpty() && binding.numberET.text.length == 10) {
                showToast("calling number ${binding.numberET.text}")
                viewModel.makeCall(binding.numberET.text.toString().trim())
                val transaction = requireActivity().supportFragmentManager.beginTransaction()
                transaction.add(R.id.fragmentContainerView, CallFragment(), "CallFragment")
                transaction.addToBackStack(null)
                transaction.commit()

//                findNavController().navigate(R.id.action_sipFragment_to_callFragment)
            } else {
                showToast("Enter number to call")

            }

        }

        viewModel.status.observe(viewLifecycleOwner) {
            binding.logTextView.DisplayLogs(it)
        }
        binding.vpnBtn.setOnClickListener {
            if (vpnStart) {
                confirmDisconnect()
            } else {
                prepareVpn()
            }
        }

    }

    private fun confirmDisconnect() {
        val builder = AlertDialog.Builder(
            activity!!
        )
        builder.setMessage(activity!!.getString(R.string.connection_close_confirm))
        builder.setPositiveButton(
            activity!!.getString(R.string.yes)
        ) { dialog, id -> stopVpn() }
        builder.setNegativeButton(
            activity!!.getString(R.string.no)
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
                    Log.d(TAG,"Starting VPN--->")

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
            val conf = activity!!.assets.open(server.ovpn)
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
                requireContext(),
                config,
                "India",
                server.username,
                server.password
            )


            // Update log
//           binding.logTV.setText("Connecting...")
            vpnStart = true
        } catch (e: IOException) {
           Log.d(TAG,"Exception---> ${e.printStackTrace()}")
        } catch (e: RemoteException) {
           Log.d(TAG, "Exception---> ${e.printStackTrace()}")
        }
    }

    private fun stopVpn(): Boolean {
        try {
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
        Log.d(TAG,"Connection State--->$connectionState")

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
        viewModel.startSipStack(requireContext(), userCred)



        binding.domain.isEnabled = false
        binding.username.isEnabled = false
        binding.password.isEnabled = false

        binding.numberET.isEnabled = true
        binding.stopSIP.isEnabled = true
        binding.startSipStack.isEnabled = false
        binding.callBtn.isEnabled = true
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    private fun status(status: String) {
        when (status) {
            "connect" -> {
                binding.vpnBtn.text = context!!.getString(R.string.connect)
            }
            "connecting" -> {
                binding.vpnBtn.text = context!!.getString(R.string.connecting)
            }
            "connected" -> {
                binding.vpnBtn.text = context!!.getString(R.string.disconnect)
            }
            "tryDifferentServer" -> {
                binding.vpnBtn.text = context!!.getString(R.string.try_different_server)

            }
            "loading" -> {
                binding.vpnBtn.text = context!!.getString(R.string.loading_server)
            }
            "invalidDevice" -> {
                binding.vpnBtn.text = context!!.getString(R.string.invalid_device)
            }
            "authenticationCheck" -> {
                binding.vpnBtn.text =context!!.getString(R.string.authentication_checking)
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
        duration: String,
        lastPacketReceive: String,
        byteIn: String,
        byteOut: String
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
        LocalBroadcastManager.getInstance(activity!!)
            .registerReceiver(broadcastReceiver, IntentFilter("connectionState"))

        super.onResume()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(activity!!).unregisterReceiver(broadcastReceiver)
        super.onPause()
    }


}