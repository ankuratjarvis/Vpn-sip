package com.example.myapplication.common

interface Storage {
    fun  saveData(key:String,value:Any)
    fun   readData (key:String):Boolean?
    fun   readFileUri ():String?
    fun saveVpnUser(id :String,pass:String)
    fun fetchVpnUser():Pair<String,String>?

    fun saveSipCred(domain:String,id:String,pass:String)
    fun fetchSipUser():Triple<String,String,String>
    fun hasSipUser():Boolean
    fun hasUser():Boolean?
    fun clearIsCallActive()
    fun clearIsServiceRunning()
//    fun clearIsCallActive()
    fun clearData()
    fun delete(key:String)
}