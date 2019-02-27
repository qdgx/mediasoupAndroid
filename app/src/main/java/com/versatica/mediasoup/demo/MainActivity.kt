package com.versatica.mediasoup.demo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.handlers.BaseApplication
import com.versatica.mediasoup.handlers.webRtc.WebRTCModule
import kotlinx.android.synthetic.main.activity_main.*
class MainActivity : AppCompatActivity() {
    private var logger: Logger = Logger("MainActivity")
    private var app: App? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //create app
        app = App("room1","csb",this)

        openCamera.setOnClickListener {
            app!!.joinRoom()
        }

        switchCamera.setOnClickListener {
            app!!.switchCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        //reopen camera
//        if (isCameraOpen) {
//            //webRTCModule.mediaStreamTrackSetEnabled(trackId,true)
//            startCamera()
//        }
    }

    private fun _getWebRTCModule(): WebRTCModule {
        return WebRTCModule.getInstance(BaseApplication.getAppContext())
    }
}
