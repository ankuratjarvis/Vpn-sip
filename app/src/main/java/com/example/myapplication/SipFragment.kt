package com.example.myapplication

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.example.myapplication.viewmodels.SipViewModel
import com.mizuvoip.jvoip.SIPNotification
import com.mizuvoip.jvoip.SIPNotificationListener
import com.mizuvoip.jvoip.SipStack
import java.io.Serializable

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [SipFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SipFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private val TAG = SipFragment::class.java.simpleName
    private var param1: String? = null
    private var param2: String? = null

    lateinit var numberTextField: EditText
    lateinit var usernameTextField: EditText
    lateinit var domainTextField: EditText
    lateinit var passwordTextField: EditText
    lateinit var startSipBtn: Button
    lateinit var stopSipBtn: Button
    lateinit var callBtn: Button
    lateinit var logView: TextView
    lateinit var viewModel:SipViewModel
    var isFirstTime = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        Log.d(TAG, "Fragment---> $TAG Created")

        val v = inflater.inflate(R.layout.fragment_sip, container, false)
        viewModel =(activity as MainActivity).viewModel
        viewModel.testFunction()
        initViews(v)

        return v
    }

    private fun initViews(v: View) {
        numberTextField = v.findViewById(R.id.numberET)
        usernameTextField = v.findViewById(R.id.username)
        domainTextField = v.findViewById(R.id.domain)
        passwordTextField = v.findViewById(R.id.password)

        usernameTextField.setText("0603441510466")
        domainTextField.setText("sip-user.ttsl.tel")
        passwordTextField.setText("UFDp7Oh^k8")

        startSipBtn = v.findViewById(R.id.startSipStack)
        stopSipBtn = v.findViewById(R.id.stopSIP)
        callBtn = v.findViewById(R.id.callBtn)
        logView = v.findViewById(R.id.logTextView)
        callBtn.isEnabled = false
        stopSipBtn.isEnabled = false
        numberTextField.isEnabled = false
        logView.movementMethod = ScrollingMovementMethod()

        initListeners()
    }

    private fun initListeners() {
        startSipBtn.setOnClickListener {
            if (usernameTextField.text.isNotEmpty() && domainTextField.text.isNotEmpty() && passwordTextField.text.isNotEmpty()) {
                startSip()
                showToast("Starting SIP Stack")
            } else {
                showToast("Please enter required Details")
            }
        }

        stopSipBtn.setOnClickListener {
            viewModel.stopSip()
            domainTextField.isEnabled = true
            usernameTextField.isEnabled = true
            passwordTextField.isEnabled = true

            numberTextField.setText("")
            numberTextField.isEnabled = false

            stopSipBtn.isEnabled = false
            startSipBtn.isEnabled = true
            callBtn.isEnabled = false

        }

        callBtn.setOnClickListener {
            if (numberTextField.text.isNotEmpty() && numberTextField.text.length == 10) {
                showToast("calling number ${numberTextField.text}")
                viewModel.makeCall(numberTextField.text.toString().trim())
               val transaction =  requireActivity().supportFragmentManager.beginTransaction()
                transaction.add(R.id.fragmentContainerView,CallFragment(),"CallFragment")
                transaction.addToBackStack(null)
                transaction.commit()

//                findNavController().navigate(R.id.action_sipFragment_to_callFragment)
            } else {
                showToast("Enter number to call")

            }

        }

        viewModel.status.observe(viewLifecycleOwner) {
            logView.DisplayLogs(it)
        }


    }

    private fun startSip() {
        val userCred = UserCredentials(usernameTextField.text.toString().trim(),domainTextField.text.toString().trim(),passwordTextField.text.toString().trim())
        viewModel.startSipStack(requireContext(),userCred)



        domainTextField.isEnabled = false
        usernameTextField.isEnabled = false
        passwordTextField.isEnabled = false

        numberTextField.isEnabled = true
        stopSipBtn.isEnabled = true
        startSipBtn.isEnabled = false
        callBtn.isEnabled = true
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment SipFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            SipFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }



}