package com.example.myapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.databinding.DataBindingUtil
import com.example.myapplication.databinding.ActivityActiveCallBinding
import com.example.myapplication.viewmodels.SipViewModel
import com.mizuvoip.jvoip.SIPNotification

class ActiveCallActivity : AppCompatActivity() {
    lateinit var binding:ActivityActiveCallBinding
    lateinit var viewModel: SipViewModel
    var isMute = false
    private val TAG = ActiveCallActivity::class.java.simpleName


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActiveCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val b  = intent.extras
        viewModel = b?.getSerializable("viewmodel") as SipViewModel
        initObservers()

    }

    fun initObservers() {
        binding.endCallButton.setOnClickListener {

            viewModel.endCall()
            finish()
        }


        binding.muteBtn.setOnClickListener {
            if (isMute) {
                viewModel.callUnmute()
            } else {
                viewModel.callMute()
            }
        }

        viewModel.notificationStatus.observe(this){
            if(it.status == SIPNotification.Status.STATUS_CALL_FINISHED){
                finish()
            }
        }
        viewModel.isMute.observe(this) {
            isMute = if (it) {
                binding.muteBtn.text = "Unmute"
                it
            } else {
                binding.muteBtn.text = "mute"
                it
            }
        }
        viewModel.status.observe(this) {
            binding.callTimeTv.text = it

        }
    }
}