package com.example.myapplication.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myapplication.model.UserCredentials
import com.mizuvoip.jvoip.SIPNotification
import com.mizuvoip.jvoip.SIPNotificationListener
import com.mizuvoip.jvoip.SipStack

class SipViewModel: ViewModel() {

    lateinit var sipStack :SipStack

    private val _status = MutableLiveData<String>()
    val status : LiveData<String> get()=_status

    private val _mute = MutableLiveData<Boolean>(false)
    val isMute :LiveData<Boolean> get()=_mute




    fun testFunction(){
        Log.d("SipViewModel","chj jdhjjhjhdjshjjsjshjdshj")
    }

    fun startSipStack( context: Context,userCredentials: UserCredentials){
         sipStack = SipStack()
        sipStack.Init(context)
        sipStack.SetParameter("loglevel", "5")
        sipStack.SetParameter("serveraddress",userCredentials.serverAddress )
        sipStack.SetParameter("username", userCredentials.username)
        sipStack.SetParameter("password",userCredentials.password)
        sipStack.Start()
        sipStack.SetNotificationListener(listener)

    }
    fun stopSip(){
        sipStack.Stop()

    }
    fun endCall(){
        sipStack.Hangup()
    }
    fun callMute(){
        sipStack.Mute(-1,true)
        _mute.postValue(true)

    }
    fun callUnmute(){
        sipStack.Mute(-1,false)
        _mute.postValue(false)

    }

    fun makeCall(number:String){
        sipStack.Call(-1,number)
    }




    private val listener = object : SIPNotificationListener() {
        override fun onAll(e: SIPNotification?) {
            _status.postValue(
                e?.notificationTypeText + " notification received: " + e.toString(),
            )

        }

        override fun onRegister(e: SIPNotification.Register?) {
            if (!e?.isMain!!) return  //we ignore secondary accounts here
            when (e.getStatus()) {
                SIPNotification.Register.STATUS_INPROGRESS -> _status.postValue("Registering...")
                SIPNotification.Register.STATUS_SUCCESS -> _status.postValue(
                    "Registered successfully."
                )

                SIPNotification.Register.STATUS_FAILED -> _status.postValue(
                    "Register failed because " + e.getReason()

                )

                SIPNotification.Register.STATUS_UNREGISTERED -> _status.postValue(
                    "Unregistered."

                )
            }

        }

        override fun onStatus(e: SIPNotification.Status?) {
            if (e?.getLine() == -1) return  //we are ignoring the global state here (but you might check only the global state instead or look for the particular lines separately if you must handle multiple simultaneous calls)

            //log call state
            if (e?.getStatus()!! >= SIPNotification.Status.STATUS_CALL_SETUP && e?.getStatus()!! <= SIPNotification.Status.STATUS_CALL_FINISHED) {
                _status.postValue("Call state is: " + e.statusText)
            }

            //catch outgoing call connect
            if (e.getStatus() == SIPNotification.Status.STATUS_CALL_CONNECT && e.endpointType == SIPNotification.Status.DIRECTION_OUT) {
                _status.postValue("Outgoing call connected to " + e.peer)


                //there are many things we can do on call connect. for example:
                //mysipclient.Dtmf(e.getLine(),"1"); //send DTMF digit 1
                //mysipclient.PlaySound(e.getLine(), "mysound.wav", 0, false, true, true, -1,"",false); //stream an audio file
            } else if (e.getStatus() == SIPNotification.Status.STATUS_CALL_RINGING && e.endpointType == SIPNotification.Status.DIRECTION_IN) {
                _status.postValue("Incoming call from " + e.peerDisplayname)

                //auto accepting the incoming call (instead of auto accept, you might present an Accept/Reject button for the user which will call Accept / Reject)
                sipStack.Accept(e.getLine())
            } else if (e.getStatus() == SIPNotification.Status.STATUS_CALL_CONNECT && e.endpointType == SIPNotification.Status.DIRECTION_IN) {
                _status.postValue("Incoming call connected")
            }

        }

        override fun onEvent(e: SIPNotification.Event?) {
            _status.postValue("Important event: " + e?.getText())

        }

        override fun onChat(e: SIPNotification.Chat?) {
            _status.postValue("Message from " + e?.peer + ": " + e?.getMsg())

            sipStack.SendChat(-1, e?.peer, "Received")

        }
    }

}