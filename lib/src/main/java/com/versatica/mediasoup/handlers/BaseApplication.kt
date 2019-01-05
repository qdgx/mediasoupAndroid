package com.versatica.mediasoup.handlers

import android.app.Application
import android.content.Context

open class BaseApplication: Application() {
    companion object {
        private lateinit var mAppContext: Context

        fun getAppContext(): Context{
            return mAppContext
        }
    }
    override fun onCreate() {
        super.onCreate()
        mAppContext = applicationContext
    }
}