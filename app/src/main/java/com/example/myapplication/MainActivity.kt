package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.net.sip.SipManager
import android.net.sip.SipProfile
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.viewmodels.SipViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mizuvoip.jvoip.SIPNotification
import com.mizuvoip.jvoip.SIPNotification.Chat
import com.mizuvoip.jvoip.SIPNotificationListener
import com.mizuvoip.jvoip.SipStack
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone


class MainActivity : AppCompatActivity() {
    lateinit var logView: TextView
    lateinit var sipStack: SipStack
    val TAG = MainActivity::class.java.simpleName
    private var sipProfile: SipProfile? = null
    var sipManager: SipManager? = null
    val username = "0603441510466"
    val domain = "sip-user.ttsl.tel"
    val pass = "UFDp7Oh^k8"
    val number = "9815403116"
    val viewModel: SipViewModel by viewModels()
    lateinit var navController: NavController

    //    lateinit var binding:ActivityMainBinding
    lateinit var vpnBtn: Button
    lateinit var btn: Button
    lateinit var stopBtn: Button
    lateinit var callET: EditText
    lateinit var startSipStack: Button
    lateinit var clearLog: ImageButton
    lateinit var fragmentTransaction: FragmentTransaction
    lateinit var sipFragment: SipFragment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        initViews()
        sipFragment = SipFragment()
        fragmentTransaction = supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragmentContainerView, sipFragment).addToBackStack("sip")
            commit()
        }

        checkPermissions()


        /*  sipStack = SipStack()


          sipStack.Init(this)*/

        //subscribe to notification events
//        val listener = MyNotificationListener()
//        sipStack.SetNotificationListener(listener)

        /*   sipStack.SetParameter("loglevel", "5")
           sipStack.SetParameter("serveraddress", domain)
           sipStack.SetParameter("username", username)
           sipStack.SetParameter("password", pass)
   */


    }

    private fun initViews() {
        vpnBtn = findViewById(R.id.connectVpn)
        btn = findViewById(R.id.callBtn)
        stopBtn = findViewById(R.id.stopBtn)
        startSipStack = findViewById(R.id.startSipStack)
        callET = findViewById(R.id.calltoET)
        logView = findViewById(R.id.logTextView)
        clearLog = findViewById(R.id.clearLog)
        logView.movementMethod = ScrollingMovementMethod()
        logView.DisplayLogs("hello hello how you doing")


//        navController.navigate(R.id.vpnFragment)

//        setListeners()

    }

    private fun setListeners() {
        vpnBtn.setOnClickListener {

        }
        startSipStack.setOnClickListener {
            startSipStack()

        }
        btn.setOnClickListener {

            if (callET.text.isNotEmpty() && callET.text.length == 10) {
                val num = callET.text.toString().trim()
                sipStack.Call(-1, num)
                logView.DisplayLogs("Calling initiated to $num ")
            } else {
                logView.DisplayLogs("Number required ")

            }


        }
        stopBtn.setOnClickListener {
            sipStack.Hangup()
            logView.DisplayLogs("Call hangup ")


        }
        clearLog.setOnClickListener {
            logView.text = ""
        }
        /* bottomNav.setOnItemSelectedListener {
             when (it.itemId) {
                 R.id.vpnFragment -> navController.navigate(R.id.vpnFragment)
                 R.id.sipFragment -> navController.navigate(R.id.sipFragment)
                 R.id.callFragment -> navController.navigate(R.id.callFragment)
                 else -> navController.navigate(R.id.vpnFragment)

             }
             return@setOnItemSelectedListener true

         }*/

    }

    fun startSipStack() {
        try {
            logView.DisplayLogs("Start on click")


            sipStack.Start()

            logView.DisplayLogs("SIPStack Started")
        } catch (e: Exception) {
            logView.DisplayLogs("Error Creating Stack: Reason-->${e.message}")
        }

    }


    fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= 23 && this.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            //we need RECORD_AUDIO permission before to make/receive any call
            logView.DisplayLogs("Microphone permission required")
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
        }
    }

    override fun onBackPressed() {
       return
    }

}