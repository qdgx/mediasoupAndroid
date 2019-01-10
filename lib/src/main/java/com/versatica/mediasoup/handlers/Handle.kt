package com.versatica.mediasoup.handlers

import com.dingsoft.sdptransform.SdpTransform
import com.versatica.mediasoup.EnhancedEventEmitter
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.RoomOptions
import com.versatica.mediasoup.handlers.sdp.*
import com.versatica.mediasoup.webrtc.WebRTCModule
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.RtpSender
import org.webrtc.SessionDescription

val logger = Logger("Handle")

open class Handle(
    direction: String,
    rtpParametersByKind: HashMap<String, RTCRtpParameters>,
    settings: RoomOptions
) : EnhancedEventEmitter(logger) {
    companion object {
        fun getNativeRtpCapabilities(): Observable<RTCRtpCapabilities> {
            logger.debug("getNativeRtpCapabilities()")

            var config = HashMap<String, Any>()
            config["iceTransportPolicy"] = "all"
            config["bundlePolicy"] = "max-bundle"
            config["rtcpMuxPolicy"] = "require"
            config["sdpSemantics"] = "plan-b"
            var pc = RTCPeerConnection(config)
            var mediaConstraints = MediaConstraints()
            mediaConstraints.mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
            )
            mediaConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo", "true"
                )
            )
            return pc.createOffer(mediaConstraints).flatMap { offer ->
                Observable.create(ObservableOnSubscribe<RTCRtpCapabilities> {
                    try {
                        var sdpObj = SdpTransform().parse(offer.description)
                        var nativeRtpCapabilities = extractRtpCapabilities(sdpObj)
                        it.onNext(nativeRtpCapabilities)
                    } catch (e: Exception) {
                        it.onError(Throwable())
                    }
                })
            }
        }

        fun getHandle(
            direction: String,
            extendedRtpCapabilities: RTCExtendedRtpCapabilities,
            settings: RoomOptions
        ) {
            var rtpParametersByKind: HashMap<String, RTCRtpParameters> = HashMap()
            when (direction) {
                "send" -> {
                    rtpParametersByKind["audio"] = getSendingRtpParameters("audio", extendedRtpCapabilities)
                    rtpParametersByKind["video"] = getSendingRtpParameters("video", extendedRtpCapabilities)
                }
                "recv" -> {
                    rtpParametersByKind["audio"] = getReceivingFullRtpParameters("audio", extendedRtpCapabilities)
                    rtpParametersByKind["video"] = getReceivingFullRtpParameters("video", extendedRtpCapabilities)
                }
                else -> null
            }
        }
    }

    protected lateinit var _pc: RTCPeerConnection
    protected lateinit var _rtpParametersByKind: HashMap<String, RTCRtpParameters>
    protected var _remoteSdp: RemotePlanBSdp.RemoteSdp?
    protected var _transportReady: Boolean = false
    protected var _transportUpdated: Boolean = false

    init {
        // RTCPeerConnection instance.
        var config = HashMap<String, Any>()
        config["iceServers"] = settings.turnServers
        config["iceTransportPolicy"] = settings.iceTransportPolicy
        config["bundlePolicy"] = "max-bundle"
        config["rtcpMuxPolicy"] = "require"
        config["sdpSemantics"] = "plan-b"
        _pc = RTCPeerConnection(config)

        // Generic sending RTP parameters for audio and video.
        _rtpParametersByKind = rtpParametersByKind

        // Remote SDP handler.
        _remoteSdp = RemotePlanBSdp.newInstance(direction, rtpParametersByKind)

        //Handle RTCPeerConnection connection status.
        this._pc.on("iceconnectionstatechange") {
            when (this._pc.iceConnectionState) {
                RTCIceConnectionState.CHECKING -> this.emit("@connectionstatechange", "connecting")
                RTCIceConnectionState.CONNECTED -> this.emit("@connectionstatechange", "connected")
                RTCIceConnectionState.COMPLETED -> this.emit("@connectionstatechange", "connected")
                RTCIceConnectionState.FAILED -> this.emit("@connectionstatechange", "failed")
                RTCIceConnectionState.DISCONNECTED -> this.emit("@connectionstatechange", "disconnected")
                RTCIceConnectionState.CLOSED -> this.emit("@connectionstatechange", "closed")
            }
        }
    }

    fun close() {
        logger.debug("close()")

        // Close RTCPeerConnection.
        try {
            this._pc.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun remoteClosed() {
        logger.debug("remoteClosed()")

        this._transportReady = false

        if (this._transportUpdated)
            this._transportUpdated = false
    }

    protected fun _getWebRTCModule(): WebRTCModule {
        return WebRTCModule.getInstance(BaseApplication.getAppContext())
    }
}

class SendHandler(
    rtpParametersByKind: HashMap<String, RTCRtpParameters>,
    settings: RoomOptions
) : Handle("send", rtpParametersByKind, settings) {

    //Local stream ids
    private val _mediaStreamLabels: List<String> = listOf("ARDAMS")

    // Ids of alive local tracks.
    private val _trackIds: HashSet<String> = HashSet()

    init {
        // Got transport local and remote parameters.
        this._transportReady = false
    }

    fun addProducer(): Observable<RTCRtpParameters> {
        //only for test
        var track = MediaStreamTrack(1)
        var id = 123
        var simulcast = true
        var kind = "video"

        logger.debug("addProducer() [id:$id, kind:$kind, trackId:${track.id()}]")

        // Add the track id to the Set.
        this._trackIds.add(track.id())

        var rtpSender: RtpSender
        var localSdpObj: com.dingsoft.sdptransform.SessionDescription = com.dingsoft.sdptransform.SessionDescription()

        return Observable.just("")
            .flatMap {
                // Add the stream to the PeerConnection.
                rtpSender = this._pc.addTrack(track, _mediaStreamLabels)

                this._pc.createOffer(MediaConstraints())
            }.flatMap { offer ->
                //clone offer
                var cloneOffer = SessionDescription(offer.type, offer.description)

                // If simulcast is set, mangle the offer.
                if (simulcast) {
                    logger.debug("addProducer() | enabling simulcast")

                    var sdpObject = SdpTransform().parse(cloneOffer.description)

                    addPlanBSimulcast(sdpObject, track)

                    var offerSdp = SdpTransform().write(sdpObject)

                    cloneOffer = SessionDescription(offer.type, offerSdp)
                }
                logger.debug("addProducer() | calling pc.setLocalDescription() [offer:${offer.toString()}]")

                this._pc.setLocalDescription(cloneOffer)
            }.flatMap {
                if (!this._transportReady) {
                    _setupTransport()
                } else {
                    Observable.create {
                        it.onNext(Unit)
                    }
                }
            }.flatMap {
                localSdpObj = SdpTransform().parse(this._pc.localDescription.description)

                val remoteSdp = (this._remoteSdp as RemotePlanBSdp.SendRemoteSdp).createAnswerSdp(localSdpObj)

                val answer = SessionDescription(SessionDescription.Type.fromCanonicalForm("answer"), remoteSdp)

                logger.debug("addProducer() | calling pc.setRemoteDescription() [answer:${answer.toString()}]")

                this._pc.setRemoteDescription(answer)
            }.flatMap {
                val rtpParameters = this._rtpParametersByKind[kind]

                // Fill the RTP parameters for this track.
                fillRtpParametersForTrack(rtpParameters!!, localSdpObj, track);

                Observable.create(ObservableOnSubscribe<RTCRtpParameters> {
                    //next
                    it.onNext(rtpParameters)
                })
            }
    }

    fun removeProducer(): Observable<Unit> {
        //only for test
        var track = MediaStreamTrack(1)
        var id = 123
        var kind = "video"

        if (!this._trackIds.contains(track.id()))
            return Observable.create {
                it.onError(Throwable("track not found"))
            }

        logger.debug("removeProducer() [id:$id, kind:$kind, trackId:${track.id()}]")

        return Observable.just("")
            .flatMap {
                // Get the associated RTCRtpSender.
                val rtpSender = this._pc.getSenders().find {
                    it.track() === track
                }

                if (rtpSender == null)
                    throw Throwable("RTCRtpSender not found")

                // Remove the associated RtpSender.
                this._pc.removeTrack(rtpSender)

                // Remove the track id from the Set.
                this._trackIds.remove(track.id())

                this._pc.createOffer(MediaConstraints())
            }.flatMap { offer ->
                logger.debug("removeProducer() | calling pc.setLocalDescription() [offer:${offer.toString()}]")

                this._pc.setLocalDescription(offer);
            }.flatMap {
                if (this._pc.signalingState === RTCSignalingState.STABLE) {
                    Observable.create {
                        it.onNext(Unit)
                    }
                } else {
                    val localSdpObj = SdpTransform().parse(this._pc.localDescription.description)
                    val remoteSdp = (this._remoteSdp as RemotePlanBSdp.SendRemoteSdp).createAnswerSdp(localSdpObj)
                    val answer = SessionDescription(SessionDescription.Type.fromCanonicalForm("answer"), remoteSdp)

                    logger.debug("removeProducer() | calling pc.setRemoteDescription() [answer:${answer.toString()}]")

                    this._pc.setRemoteDescription(answer)
                }
            }
    }

    fun replaceProducerTrack(): Observable<Unit> {
        //only for test
        var track = MediaStreamTrack(1)
        var id = 123
        var kind = "video"

        var oldTrack = MediaStreamTrack(2)

        logger.debug("replaceProducerTrack() [id:$id, kind:$kind, trackId:${track.id()}]")

        return Observable.just("")
            .flatMap {
                // Get the associated RTCRtpSender.
                val rtpSender = this._pc.getSenders().find {
                    it.track() === oldTrack
                }

                if (rtpSender == null)
                    throw Throwable("local track not found")

                rtpSender.setTrack(track, true)

                Observable.create(ObservableOnSubscribe<Unit> {
                    //next
                    it.onNext(Unit)
                })
            }
            .flatMap {
                // Remove the old track id from the Set.
                this._trackIds.remove(oldTrack.id())

                // Add the new track id to the Set.
                this._trackIds.add(track.id())

                Observable.create(ObservableOnSubscribe<Unit> {
                    //next
                    it.onNext(Unit)
                })
            }
    }

    fun restartIce(): Observable<Unit> {
        //only for test
        var remoteIceParameters = RTCIceParameters()

        logger.debug("restartIce()")

        // Provide the remote SDP handler with new remote ICE parameters.
        this._remoteSdp?.updateTransportRemoteIceParameters(remoteIceParameters)

        return Observable.just("")
            .flatMap {
                var mediaConstraints = MediaConstraints()
                mediaConstraints.mandatory.add(
                    MediaConstraints.KeyValuePair("iceRestart", "true")
                )

                this._pc.createOffer(mediaConstraints)
            }
            .flatMap { offer ->
                logger.debug("restartIce() | calling pc.setLocalDescription() [offer:${offer.toString()}]")

                this._pc.setLocalDescription(offer)
            }
            .flatMap {
                val localSdpObj = SdpTransform().parse(this._pc.localDescription.description)
                val remoteSdp = (this._remoteSdp as RemotePlanBSdp.SendRemoteSdp).createAnswerSdp(localSdpObj)
                val answer = SessionDescription(SessionDescription.Type.fromCanonicalForm("answer"), remoteSdp)

                logger.debug("restartIce() | calling pc.setRemoteDescription() [answer${answer.toString()}]")

                this._pc.setRemoteDescription(answer)
            }
    }

    fun _setupTransport(): Observable<Unit> {
        logger.debug("_setupTransport()")
        return Observable.just("")
            .flatMap {
                // Get our local DTLS parameters.
                var transportLocalParameters: TransportRemoteIceParameters = TransportRemoteIceParameters()
                val sdp = this._pc.localDescription.description
                val sdpObj = SdpTransform().parse(sdp)
                val dtlsParameters = extractDtlsParameters(sdpObj);

                // Let's decide that we'll be DTLS server (because we can).
                dtlsParameters.role = RTCDtlsRole.SERVER

                transportLocalParameters.dtlsParameters = dtlsParameters

                // Provide the remote SDP handler with transport local parameters.
                (this._remoteSdp as RemotePlanBSdp.SendRemoteSdp).transportLocalParameters = transportLocalParameters

                // We need transport remote parameters
                Observable.create(ObservableOnSubscribe<Any> {
                    //next
                    this.safeEmitAsPromise(it, "@needcreatetransport", transportLocalParameters).subscribe()
                })
            }
            .flatMap { transportRemoteParameters ->
                // Provide the remote SDP handler with transport remote parameters.
                (this._remoteSdp as RemotePlanBSdp.SendRemoteSdp).transportLocalParameters =
                        (transportRemoteParameters as TransportRemoteIceParameters)

                this._transportReady = true

                Observable.create(ObservableOnSubscribe<Unit> {
                    //next
                    it.onNext(Unit)
                })
            }
    }
}