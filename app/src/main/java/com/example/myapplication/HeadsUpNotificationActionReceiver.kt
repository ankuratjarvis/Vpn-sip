package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.example.myapplication.common.Constants


class HeadsUpNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null && intent.extras != null) {
            val action = intent.getStringExtra(Constants.CALL_RESPONSE_ACTION_KEY)
            val data = intent.getBundleExtra(Constants.INCOMING_CALL)
            val callStr = intent.getStringExtra(Constants.INCOMING_CALL)
            action?.let {
                performClickAction(context!!, it, data!!,callStr)
            }

            // Close the notification after the click action is performed.
            val it = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            context!!.sendBroadcast(it)
            context.stopService(Intent(context, NotificationService::class.java))
        }
    }
    private fun performClickAction( context:Context,  action:String,  data: Bundle,callStr:String?) {
        if (action == Constants.CALL_RECEIVE_ACTION && callStr!=null) {
            var openIntent :Intent?;
            try {
                Log.d("HeadsUpReceiver","Trying to perform click action on receive call")
                openIntent =  Intent(context, SipActivity::class.java)
                openIntent.putExtra(Constants.INCOMING_CALL,callStr)
                openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(openIntent);
            } catch ( e:ClassNotFoundException) {
                e.printStackTrace();
            }
        } else if (action == Constants.CALL_RECEIVE_ACTION) {
            val openIntent :Intent?
            try {
                openIntent =  Intent(context, SipActivity::class.java)
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(openIntent);
            } catch ( e:ClassNotFoundException) {
                e.printStackTrace();
            }
        } else if (action == Constants.CALL_CANCEL_ACTION) {
            context.stopService( Intent(context, NotificationService::class.java))
            val it =  Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            context.sendBroadcast(it);
        }
    }

}
