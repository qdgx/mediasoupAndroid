package com.versatica.mediasoup.demo

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.handlers.BaseApplication
import com.versatica.mediasoup.handlers.Handler
import com.versatica.mediasoup.handlers.sdp.RTCRtpCodecCapability
import com.versatica.mediasoup.handlers.sdp.RTCRtpHeaderExtensionCapability
import com.versatica.mediasoup.handlers.webRtc.GetUserMediaImpl
import com.versatica.mediasoup.handlers.webRtc.WebRTCModule
import com.yanzhenjie.permission.Action
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.Permission
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.VideoTrack
import java.lang.Exception
import java.util.*
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {
    private var logger: Logger = Logger("MainActivity")
    private val AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
    private val AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
    private val AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter"
    private val AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"

    private val MINI_WIDTH = "minWidth"
    private val MINI_HEIGHT = "minHeight"
    private val MINI_FRAMERATE = "minFrameRate"
    private val ENVIRONMENT_FACINGMODE = "environment"
    private val USER_FACINGMODE = "user"


    private var isCameraOpen = false
    private var trackId = ""
    private var faceMode = USER_FACINGMODE
    private val webRTCModule = _getWebRTCModule()

    private var app: App? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //create app
        app = App("room1","csb",this)

        openCamera.setOnClickListener {
//            if (isCameraOpen) {
//                //关闭
//                closeCamera()
//            } else {
//                //开启
//                openCamera()
//            }
            app!!.joinRoom()
        }

        switchCamera.setOnClickListener {
//            if (trackId.isNotEmpty()) {
//                if (faceMode == ENVIRONMENT_FACINGMODE) {
//                    faceMode = USER_FACINGMODE
//                } else {
//                    faceMode = ENVIRONMENT_FACINGMODE
//                }
//                webRTCModule.mediaStreamTrackSwitchCamera(trackId)
//            }
            app!!.switchCamera()
        }

        safeEmit.setOnClickListener {
            val eventEmitterImpl: EventEmitterImpl = EventEmitterImpl()
            eventEmitterImpl.on("key") { args ->
                var sb: String = String()
                for (arg in args) {
                    sb += (" " + arg)
                }
                logger.debug("key: $sb")
            }
            eventEmitterImpl.safeEmit("key", 101)
            eventEmitterImpl.safeEmit("key", "hello world")
            eventEmitterImpl.safeEmit("key", "hello world", 101, 102.3f, "end")
        }

        safeEmitAsPromiseTimmer.setOnClickListener {
            val eventEmitterImpl: EventEmitterImpl = EventEmitterImpl()
            eventEmitterImpl.on("@request") { args ->
                var timer: Timer? = Timer()
                timer!!.schedule(object : TimerTask() {
                    override fun run() {
                        var length = args.size
                        if (length > 0) {
                            var result = args.get(1) as Int
                            //success callBack
                            var successCallBack = args.get(length - 2) as Function1<Any, Unit>
                            successCallBack.invoke("Result $result")
                        }
                    }
                }, 2000)
            }

            Observable.just(123)
                .flatMap(addFlatMapTimer())
                .flatMap(eventEmitterImpl.testPromise())
                .subscribe {
                    logger.debug(it as String)
                }
        }

        safeEmitAsPromiseThread.setOnClickListener {
            val eventEmitterImpl: EventEmitterImpl = EventEmitterImpl()
            eventEmitterImpl.on("@request") { args ->
                Thread(object : Runnable {
                    override fun run() {
                        var length = args.size
                        if (length > 0) {
                            var result = args.get(1) as Int
                            //success callBack
                            var successCallBack = args.get(length - 2) as Function1<Any, Unit>
                            successCallBack.invoke("Result $result")
                        }
                    }
                }).start()
            }

            Observable.just(123)
                .flatMap(addFlatMapThread())
                .flatMap(eventEmitterImpl.testPromiseThread())
                .subscribe {
                    logger.debug(it as String)
                }

        }


        safeEmitAsPromiseMultiple.setOnClickListener {
            val eventEmitterImpl: EventEmitterImpl = EventEmitterImpl()
            eventEmitterImpl.on("@request") { args ->
                var length = args.size
                if (length > 0) {
                    var result = args.get(1) as Int
                    var successCallBack = args.get(length - 2) as Function1<Any, Unit>
                    Observable.just(result)
                        .flatMap(addFlatMapTimer())
                        .subscribe {
                            //success callBack
                            successCallBack.invoke("Result $it")
                        }
                }
            }

            Observable.just(123)
                .flatMap(addFlatMapThread())
                .flatMap(eventEmitterImpl.testPromiseThread())
                .subscribe {
                    logger.debug(it as String)
                }

        }


        commandQueueTest.setOnClickListener {
            val eventEmitterImpl = EventEmitterImpl()
            eventEmitterImpl.on("@request") { args ->
                var timer: Timer? = Timer()
                timer!!.schedule(object : TimerTask() {
                    override fun run() {
                        var length = args.size
                        if (length > 0) {
                            var method = args.get(0) as String
                            var data = args.get(1) as String
                            //success callBack
                            var successCallBack = args.get(length - 2) as Function1<Any, Unit>
                            successCallBack.invoke("Result $data")
                        }
                    }
                }, 2000)
            }

            Observable.just("Add Producer")
                .flatMap {
                    eventEmitterImpl.addProducer(it)
                }
                .subscribe {
                    logger.debug(it as String)
                }
        }

        getNativeRtpCapabilitiesTest.setOnClickListener {
            Handler.getNativeRtpCapabilities().subscribe(
                {
                    var codecs: MutableCollection<RTCRtpCodecCapability> = it.codecs
                    var headerExtensions: MutableCollection<RTCRtpHeaderExtensionCapability> = it.headerExtensions
                    var fecMechanisms: MutableList<Any>? = it.fecMechanisms
                },
                {

                })
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

    private fun openCamera() {
        if (AndPermission.hasPermissions(this, Permission.Group.CAMERA)) {
            startCamera()
        } else {
            AndPermission.with(this)
                .runtime()
                .permission(Permission.Group.CAMERA)
                .onGranted {
                    startCamera()
                }
                .onDenied {
                    Toast.makeText(this@MainActivity, "No permission", Toast.LENGTH_SHORT).show()
                }.start()
        }
    }

    @SuppressLint("CheckResult")
    private fun startCamera() {
        Observable.just(Unit)
            .flatMap {
                val constraints = HashMap<String, HashMap<String, *>>()
                //audio
                val audioConstraints = HashMap<String, Any>()
                val audioMandatoryConstraints = HashMap<String, String>()
                audioMandatoryConstraints[AUDIO_ECHO_CANCELLATION_CONSTRAINT] = "false"
                audioMandatoryConstraints[AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT] = "false"
                audioMandatoryConstraints[AUDIO_HIGH_PASS_FILTER_CONSTRAINT] = "false"
                audioMandatoryConstraints[AUDIO_NOISE_SUPPRESSION_CONSTRAINT] = "false"

                audioConstraints["mandatory"] = audioMandatoryConstraints
                constraints["audio"] = audioConstraints

                //video
                val videoConstraints = HashMap<String, Any>()
                val videoMandatoryConstraints = HashMap<String, String>()
                videoMandatoryConstraints[MINI_WIDTH] = "1280"
                videoMandatoryConstraints[MINI_HEIGHT] = "720"
                videoMandatoryConstraints[MINI_FRAMERATE] = "30"

                videoConstraints["mandatory"] = videoMandatoryConstraints
                videoConstraints["facingMode"] = faceMode

                constraints["video"] = videoConstraints

                webRTCModule.getUserMedia(constraints)
            }
            .subscribe(
                { mediaStream ->
                    val videoTrack = mediaStream.videoTracks[0]
                    webRTCView.setVideoTrack(videoTrack)
                    trackId = videoTrack.id()
                    isCameraOpen = true
                    openCamera.setText("closeCamera")
                },
                { throwable ->
                    Toast.makeText(this@MainActivity, throwable.cause.toString(), Toast.LENGTH_SHORT).show()
                })
    }

    fun closeCamera() {
        try {
            if (trackId.isNotEmpty()) {
                webRTCModule.mediaStreamTrackStop(trackId)
                isCameraOpen = false
                openCamera.setText("openCamera")
                webRTCView.cleanSurfaceViewRenderer()
            }
        } catch (e: Throwable) {
            logger.error(e.message!!)
        }

    }


    private fun addFlatMap(): Function<Int, Observable<Int>> {
        return Function { input ->
            Observable.create(ObservableOnSubscribe<Int> {
                val result = input + input
                it.onNext(result)
            }).subscribeOn(Schedulers.io())
        }
    }

    private fun addFlatMapTimer(): Function<Int, Observable<Int>> {
        return Function { input ->
            Observable.create {
                var timer: Timer? = Timer()
                timer!!.schedule(object : TimerTask() {
                    override fun run() {
                        val result = input + input
                        it.onNext(result)
                    }
                }, 1000)
            }
        }
    }

    private fun addFlatMapThread(): Function<Int, Observable<Int>> {
        return Function { input ->
            Observable.create {
                Thread(object : Runnable {
                    override fun run() {
                        val result = input + input
                        it.onNext(result)
                    }
                }).start()
            }
        }
    }

    private fun _getWebRTCModule(): WebRTCModule {
        return WebRTCModule.getInstance(BaseApplication.getAppContext())
    }
}
