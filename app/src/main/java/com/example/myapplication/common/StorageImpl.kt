package com.example.myapplication.common

import android.content.Context
import android.content.SharedPreferences

class StorageImpl(val context: Context?):Storage {
    val pref = context?.getSharedPreferences("my_prefs",Context.MODE_PRIVATE)
    val editor :SharedPreferences.Editor? = pref?.edit()


    override fun  saveData(key: String, value: Any) {
        when(value){
            is Boolean -> editor?.putBoolean(key,value)?.apply()
            is String -> editor?.putString(key,value)?.apply()
        }
    }

    override fun  readData(key: String): Boolean? {
       return pref?.getBoolean(key,false)
    }

    override fun readFileUri(): String? {
        return pref?.getString(Constants.FILE_URI,null)
    }

    override fun clearIsCallActive() {
        editor?.remove(Constants.IS_CALL_ACTIVE)?.apply()

    }

    override fun saveVpnUser(id: String, pass: String) {
        editor?.putString(Constants.PREF_VPN_ID_KEY,id)?.apply()
        editor?.putString(Constants.PREF_VPN_PASS_KEY,pass)?.apply()
    }

    override fun fetchVpnUser(): Pair<String, String>? {
      val userId = pref?.getString(Constants.PREF_VPN_ID_KEY,null)
      val userPass = pref?.getString(Constants.PREF_VPN_PASS_KEY,null)
        return Pair(userId.toString(),userPass.toString())
    }

    override fun hasUser(): Boolean? {
        return (pref?.contains(Constants.PREF_VPN_ID_KEY) == true && pref.contains(Constants.PREF_VPN_PASS_KEY))
    }

    override fun saveSipCred(domain: String, id: String, pass: String) {
        editor?.putString(Constants.PREF_DOMAIN_KEY,domain)?.apply()
        editor?.putString(Constants.PREF_SIP_ID_KEY,id)?.apply()
        editor?.putString(Constants.PREF_SIP_PASS_KEY,pass)?.apply()
    }

    override fun hasSipUser(): Boolean {
        return (pref?.contains(Constants.PREF_SIP_ID_KEY) == true && pref.contains(Constants.PREF_SIP_PASS_KEY) && pref.contains(Constants.PREF_DOMAIN_KEY) )
    }

    override fun fetchSipUser(): Triple<String, String, String> {
        val domain = pref?.getString(Constants.PREF_DOMAIN_KEY,null)
        val sipId = pref?.getString(Constants.PREF_SIP_ID_KEY,null)
        val sipPass = pref?.getString(Constants.PREF_SIP_PASS_KEY,null)
        return Triple(domain.toString(),sipId.toString(),sipPass.toString())
    }

    override fun clearIsServiceRunning() {
        editor?.remove(Constants.SERVICE)?.apply()
    }

    override fun clearData() {
      editor?.clear()?.apply()
    }

    override fun delete(key: String) {
        editor?.remove(key)?.apply()
    }


}