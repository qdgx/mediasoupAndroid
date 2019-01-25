package com.versatica.mediasoup.demo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.handlers.Handler
import com.versatica.mediasoup.handlers.sdp.RTCRtpCodecCapability
import com.versatica.mediasoup.handlers.sdp.RTCRtpHeaderExtensionCapability
import com.versatica.mediasoup.handlers.webRtc.GetUserMediaImpl
import com.versatica.mediasoup.handlers.webRtc.WebRTCModule
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        openCamera.setOnClickListener {
            var getUserMediaImpl = GetUserMediaImpl(WebRTCModule.getInstance(this),this)
            Observable.just(Unit)
                .flatMap {
                    var constraints = HashMap<String,HashMap<String,*>>()
                    //audio
                    var audioConstraints = HashMap<String,HashMap<String,*>>()
                    var audioMandatoryConstraints = HashMap<String,String>()
                    audioMandatoryConstraints[AUDIO_ECHO_CANCELLATION_CONSTRAINT] = "false"
                    audioMandatoryConstraints[AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT] = "false"
                    audioMandatoryConstraints[AUDIO_HIGH_PASS_FILTER_CONSTRAINT] = "false"
                    audioMandatoryConstraints[AUDIO_NOISE_SUPPRESSION_CONSTRAINT] = "false"

                    audioConstraints["mandatory"] = audioMandatoryConstraints
                    constraints["audio"] = audioConstraints

                    //video
                    var videoConstraints = HashMap<String,HashMap<String,*>>()
                    var videoMandatoryConstraints = HashMap<String,String>()
                    videoMandatoryConstraints[MINI_WIDTH] = "1280"
                    videoMandatoryConstraints[MINI_HEIGHT] = "720"
                    videoMandatoryConstraints[MINI_FRAMERATE] = "30"

                    videoConstraints["mandatory"] = videoMandatoryConstraints
                    constraints["video"] = videoConstraints

                    getUserMediaImpl.getUserMedia(constraints)
                }
        }

        test.setOnClickListener {

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
}
