package com.example.myapplication.common

interface Storage {
    fun  saveData(key:String,value:Any)
    fun   readData (key:String):Boolean?
    fun   readFileUri ():String?
    fun clearIsCallActive()
    fun clearIsServiceRunning()
//    fun clearIsCallActive()
    fun clearData()
    fun delete(key:String)
}