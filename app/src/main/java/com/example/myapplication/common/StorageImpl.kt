package com.example.myapplication.common

import android.content.Context
import android.content.SharedPreferences

class StorageImpl(val context: Context?):Storage {
    val pref = context?.getSharedPreferences("my_prefs",Context.MODE_PRIVATE)
    val editor :SharedPreferences.Editor? = pref?.edit()


    override fun  saveData(key: String, value: Any) {
        when(value){
            is Boolean -> editor?.putBoolean(key,value)?.apply()
        }
    }

    override fun  readData(key: String): Boolean? {
       return pref?.getBoolean(key,false)
    }

    override fun clearData() {
      editor?.clear()?.apply()
    }


}