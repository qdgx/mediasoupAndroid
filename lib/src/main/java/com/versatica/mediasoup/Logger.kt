package com.versatica.mediasoup

import android.util.Log

class Logger(prefix: String) {
    // AppName
    private val APP_NAME: String = "mediasoup-client"
    // constructor
    private val logPrefix: String = prefix
    init {

    }

    /**
     * Log Debug
     *
     * @param message Message for log
     */
    public fun debug(message: String){
        if (!isDebuggable())
            return
        var tag = String()
        tag = tag.plus(APP_NAME).plus(":").plus(logPrefix)
        Log.d(tag,message)
    }

    /**
     * Log Warn
     *
     * @param message Message for log
     */
    public fun warn(message: String){
        if (!isDebuggable())
            return
        var tag = String()
        tag = tag.plus(APP_NAME).plus(":WARN:").plus(logPrefix)
        Log.w(tag,message)
    }

    /**
     * Log Error
     *
     * @param message Message for log
     */
    public fun error(message: String){
        if (!isDebuggable())
            return
        var tag = String()
        tag = tag.plus(APP_NAME).plus(":ERROR:").plus(logPrefix)
        Log.e(tag,message)
    }

    /**
     * Determine whether debugging is possible
     *
     * @return If build config is debug,return True, else return False
     */
    private fun isDebuggable(): Boolean{
        return BuildConfig.DEBUG
    }
}