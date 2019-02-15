package com.versatica.mediasoup.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.widget.LinearLayout
import android.widget.Toast
import com.alibaba.fastjson.JSON
import com.versatica.mediasoup.*
import com.versatica.mediasoup.handlers.BaseApplication
import com.versatica.mediasoup.handlers.webRtc.EglUtil
import com.versatica.mediasoup.handlers.webRtc.WebRTCModule
import com.versatica.mediasoup.handlers.webRtc.WebRTCView
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.Permission
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.socket.client.Ack
import io.socket.client.IO
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.*


/**
 * @author wolfhan
 */

class App(val roomId: String, val peerName: String, val context: Context) {

    val logger = Logger("App")
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

    val socket = IO.socket("http://172.16.70.213:8080", IO.Options().also {
        it.query = "roomId=$roomId&peerName=$peerName"
    }).connect()

    // Transport for sending our media.
    var sendTransport: Transport? = null
    // Transport for receiving media from remote Peers.
    var recvTransport: Transport? = null

    // Create a local Room instance associated to the remote roomObj.
    val roomObj: Room = Room()

    init {
        // Event fired by local room when a new remote Peer joins the Room
        roomObj.on("newpeer") {
            val peer = it[0] as Peer
            logger.debug("A new Peer joined the Room: ${peer.name}")

            // Handle the Peer.
            handlePeer(peer)
        }

        // Event fired by local room
        roomObj.on("request") {
            val size = it.size
            val request = it[0]
            val ack: Ack?
            if (size == 3) {
                val callback = it[1] as Function1<Any, *>
                val errCallback = it[2] as Function1<Any, *>
                ack = Ack { subArgs ->
                    val err = subArgs[0]
                    if (err == null || err == false) {
                        val response = subArgs[1]
                        if (response != null){
                            val responseSt = (response as org.json.JSONObject).toString()
                            logger.error(responseSt)
                            // Success response, so pass the mediasoup response to the local roomObj.
                            callback(responseSt)
                        }else{
                            callback("")
                        }

                    } else {
                        errCallback(Throwable(err as String))
                    }
                }
            } else {
                ack = null
            }
            val requestSt = JSON.toJSONString(request)
            val requestJson = org.json.JSONObject(requestSt)
            logger.error("REQUEST: $requestJson")
            socket.emit("mediasoup-request", requestJson, ack)
        }

        // Be ready to send mediaSoup client notifications to our remote mediaSoup Peer
        roomObj.on("notify") { args ->
            val notification = args[0]
            var notificationJson =  org.json.JSONObject(JSON.toJSONString(notification))
            logger.debug("New notification from local room: $notificationJson")
            socket.emit("mediasoup-notification", notificationJson)
        }

        // Handle notifications from server, as there might be important info, that affects stream
        socket.on("mediasoup-notification") {
            val notification = it[0] as org.json.JSONObject
            logger.debug("New notification came from server: $notification")
            roomObj.receiveNotification(notification).subscribe(
                {

                },
                {
                    (context as Activity).runOnUiThread{
                        ////UI thread
                        Toast.makeText(context, it.cause.toString(), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    @SuppressLint("CheckResult")
    fun joinRoom() {
        roomObj.join(peerName)
            .flatMap {
                // Create the Transport for sending our media.
                sendTransport = roomObj.createTransport("send")
                // Create the Transport for receiving media from remote Peers.
                recvTransport = roomObj.createTransport("recv")

                val peers = it as MutableList<Peer>
                peers.forEach { peer ->
                    handlePeer(peer)
                }
                Observable.create(ObservableOnSubscribe<Unit> { observableEmitter ->
                    observableEmitter.onNext(Unit)
                })
            }.flatMap {
                // Get our mic and camera
                openCameraRx()
            }.subscribe(
                { mediaStream ->
                    val audioTrack = mediaStream.audioTracks[0]
                    val videoTrack = mediaStream.videoTracks[0]

                    trackId = videoTrack.id()
                    isCameraOpen = true

                    //Show local stream
                    (context as MainActivity).runOnUiThread{
                        ////UI thread
                        val localWebRTCView = context.webRTCView
                        localWebRTCView.setVideoTrack(videoTrack)
                    }

//                    // Create Producers for audio and video.
//                    val audioProducer = roomObj.createProducer(audioTrack)
                      val videoProducer = roomObj.createProducer(videoTrack)
//
//                    // Send our audio.
//                    audioProducer.send(sendTransport!!).subscribe(
//                        {
//
//                        },
//                        {
//                            Toast.makeText(context, it.cause.toString(), Toast.LENGTH_SHORT).show()
//                        }
//                    )
                    // Send our video.
                    videoProducer.send(sendTransport!!).subscribe(
                        {

                        },
                        {
                            context.runOnUiThread{
                                ////UI thread
                                Toast.makeText(context, it.cause.toString(), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                { throwable ->
                    (context as Activity).runOnUiThread{
                        ////UI thread
                        Toast.makeText(context, throwable.cause.toString(), Toast.LENGTH_SHORT).show()
                    }
                })
    }

    fun closeCamera() {
        try {
            if (trackId.isNotEmpty()) {
                webRTCModule.mediaStreamTrackStop(trackId)
                isCameraOpen = false
            }
        } catch (e: Throwable) {
            logger.error(e.message!!)
        }

    }

    fun switchCamera() {
        if (trackId.isNotEmpty()) {
            if (faceMode == ENVIRONMENT_FACINGMODE) {
                faceMode = USER_FACINGMODE
            } else {
                faceMode = ENVIRONMENT_FACINGMODE
            }
            webRTCModule.mediaStreamTrackSwitchCamera(trackId)
        }
    }

    /**
     * Handles specified peer in the room
     *
     * @param peer
     */
    private fun handlePeer(peer: Peer) {
        // Handle all the Consumers in the Peer.
        peer.consumers().forEach {
            handleConsumer(it)
        }

        // Event fired when the remote Room or Peer is closed.
        peer.on("close") {
            logger.debug("Remote Peer closed")
        }

        // Event fired when the remote Peer sends a new media to mediasoup server.
        peer.on("newconsumer") {
            logger.debug("Got a new remote Consumer")
            // Handle the Consumer.
            handleConsumer(it[0] as Consumer)
        }
    }

    /**
     * Handles specified consumer
     *
     * @param consumer
     */
    @SuppressLint("CheckResult")
    private fun handleConsumer(consumer: Consumer) {
        // Receive the media over our receiving Transport.
        if (recvTransport != null) {
            consumer.receive(recvTransport as Transport)
                .flatMap {
                    logger.debug("Receiving a new remote MediaStreamTrack: ${consumer.kind}")

                    val track = it as MediaStreamTrack
                    // Attach the track to a MediaStream and play it.
                    if (consumer.kind == "video") {
                        //todo add video to ui view
                        //Show local stream
                        (context as MainActivity).runOnUiThread{
                            val videoTrack = track as VideoTrack

                            //UI thread
                            val webRTCView = WebRTCView(context)
                            val layoutParams = LinearLayout.LayoutParams(360, 360)
                            layoutParams.topMargin = 20
                            context.remoteVideoLl.addView(webRTCView,layoutParams)

                            webRTCView.setVideoTrack(videoTrack)

//                            val remoteView = SurfaceViewRenderer(context)
//                            remoteView.init(EglUtil.rootEglBaseContext, null)
//                            remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
//                            remoteView.setZOrderMediaOverlay(true)
//                            remoteView.setEnableHardwareScaler(false)
//                            remoteView.setMirror(true)
//
//                            val layoutParams = LinearLayout.LayoutParams(360, 360)
//                            layoutParams.topMargin = 20
//                            context.remoteVideoLl.addView(remoteView, layoutParams)
//
//                            videoTrack.addSink(remoteView)
                        }
                    }
                    if (consumer.kind == "audio") {
                        val audioTrack = track as AudioTrack
                        //todo add audio to ui view

                    }
                    Observable.create(ObservableOnSubscribe<Unit> { observableEmitter ->
                        observableEmitter.onNext(Unit)
                    })
                }.subscribe (
                    {

                    },
                    {
                        (context as Activity).runOnUiThread{
                            ////UI thread
                            Toast.makeText(context, it.cause.toString(), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
        }

        // Event fired when the Consumer is closed.
        consumer.on("close") {
            logger.debug("Consumer closed")
        }
    }

    private fun openCameraRx(): Observable<MediaStream> {
        return Observable.create(ObservableOnSubscribe<Unit> {
            //request permission
            if (AndPermission.hasPermissions(context, Permission.Group.CAMERA, Permission.Group.MICROPHONE)) {
                it.onNext(Unit)
            } else {
                AndPermission.with(context)
                    .runtime()
                    .permission(Permission.Group.CAMERA, Permission.Group.MICROPHONE)
                    .onGranted { permissions ->
                        it.onNext(Unit)
                    }
                    .onDenied { permissions ->
                        it.onError(Throwable("No permission"))
                    }.start()
            }
        }).flatMap {
            //start camera
            startCameraRx()
        }
    }

    private fun openCamera() {
        if (AndPermission.hasPermissions(context, Permission.Group.CAMERA)) {
            startCamera()
        } else {
            AndPermission.with(context)
                .runtime()
                .permission(Permission.Group.CAMERA)
                .onGranted {
                    startCamera()
                }
                .onDenied {
                    Toast.makeText(context, "No permission", Toast.LENGTH_SHORT).show()
                }.start()
        }
    }

    private fun startCameraRx(): Observable<MediaStream> {
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

        return webRTCModule.getUserMedia(constraints)
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
                    trackId = videoTrack.id()
                    isCameraOpen = true
                },
                { throwable ->
                    Toast.makeText(context, throwable.cause.toString(), Toast.LENGTH_SHORT).show()
                })
    }

    private fun _getWebRTCModule(): WebRTCModule {
        return WebRTCModule.getInstance(BaseApplication.getAppContext())
    }
}
