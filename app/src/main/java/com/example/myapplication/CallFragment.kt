package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.fragment.findNavController
import com.example.myapplication.viewmodels.SipViewModel
import java.io.Serializable

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [CallFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class CallFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private val TAG = CallFragment::class.java.simpleName
    lateinit var endCallBtn: Button
    lateinit var speakerBtn: Button
    lateinit var muteBtn: Button
    lateinit var viewModel: SipViewModel
    var isMute = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        Log.d(TAG, "Fragment---> $TAG Created")
        val v = inflater.inflate(R.layout.fragment_call, container, false)
        viewModel = (activity as MainActivity).viewModel
        initViews(v)
        return v
    }

    private fun initViews(v: View) {
        endCallBtn = v.findViewById(R.id.endCallButton)
        muteBtn = v.findViewById(R.id.muteBtn)
        initListeners()
    }

    private fun initListeners() {
        endCallBtn.setOnClickListener {
//            findNavController().popBackStack()
            val manager = requireActivity().supportFragmentManager
            manager.popBackStackImmediate()

            viewModel.endCall()
        }
        muteBtn.setOnClickListener {
            if (isMute) {
                viewModel.callUnmute()
            } else {
                viewModel.callMute()
            }
        }
        viewModel.isMute.observe(viewLifecycleOwner) {
            isMute = if (it) {
                muteBtn.text = "Unmute"
                it
            } else {
                muteBtn.text = "mute"
                it
            }
        }
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment CallFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            CallFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}