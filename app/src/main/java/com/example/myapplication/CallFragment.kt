package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.example.myapplication.databinding.FragmentCallBinding
import com.example.myapplication.viewmodels.SipViewModel
import java.io.Serializable


class CallFragment : Fragment() {
    private var param1: String? = null
    private val TAG = CallFragment::class.java.simpleName

    lateinit var viewModel: SipViewModel
    var isMute = false

    lateinit var binding: FragmentCallBinding
    var listener: (() -> Unit)? = null
    var muteCall: ((Boolean) -> Unit)? = null
//    var listener: (() -> Unit)? = null

    companion object{

    }
    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "onAttach Called")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate Called")

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView Called")
        binding = FragmentCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated Called")

//        viewModel = (activity as MainActivity).viewModel
        initViews()

    }

    private fun initViews() {

        initListeners()
    }

    private fun initListeners() {

        binding.endCallButton.setOnClickListener {
            listener?.invoke()
//             viewModel.endCall()
//             (activity as MainActivity).popBackStack()
        }
        binding.muteBtn.setOnClickListener {
            if((activity as MainActivity).isMute){
                muteCall?.invoke(true)
                binding.muteBtn.text = "Unmute"

            }else{
                muteCall?.invoke(false)
                binding.muteBtn.text = "Mute"

            }
           /* if (isMute) {
                viewModel.callUnmute()
            } else {
                viewModel.callMute()
            }*/
        }
         /*viewModel.isMute.observe(viewLifecycleOwner) {
             isMute = if (it) {
                 binding.muteBtn.text = "Unmute"
                 it
             } else {
                 binding.muteBtn.text = "mute"
                 it
             }
         }
         viewModel.status.observe(viewLifecycleOwner) {
             binding.callTimeTv.text = it
         }*/


    }

   fun addListener(action :()->Unit){
       listener = action
   }
    fun addMuteCallListener(action:(Boolean)->Unit){
        muteCall = action
    }

}