package com.versatica.mediasoup.handlers

import com.alibaba.fastjson.JSON
import com.dingsoft.sdptransform.SdpTransform
import com.versatica.mediasoup.EnhancedEventEmitter
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.RoomOptions
import com.versatica.mediasoup.handlers.sdp.*
import com.versatica.mediasoup.webrtc.WebRTCModule
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import java.util.*
import kotlin.collections.ArrayList
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.RtpSender
import org.webrtc.SessionDescription

val logger = Logger("Handle")

open class Handler(
    direction: String,
    rtpParametersByKind: HashMap<String, RTCRtpParameters>,
    settings: RoomOptions
) : EnhancedEventEmitter(logger) {
    companion object {
        fun getNativeRtpCapabilities(): Observable<RTCRtpCapabilities> {
            logger.debug("getNativeRtpCapabilities()")

            val config = HashMap<String, Any>()
            config["iceTransportPolicy"] = "all"
            config["bundlePolicy"] = "max-bundle"
            config["rtcpMuxPolicy"] = "require"
            config["sdpSemantics"] = "plan-b"
            val pc = RTCPeerConnection(config)
            val mediaConstraints = MediaConstraints()
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
                        val sdpObj = SdpTransform().parse(offer.description)
                        val nativeRtpCapabilities = extractRtpCapabilities(sdpObj)
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
        ):Handler? {
            val rtpParametersByKind: HashMap<String, RTCRtpParameters> = HashMap()
            when (direction) {
                "send" -> {
                    rtpParametersByKind["audio"] = getSendingRtpParameters("audio", extendedRtpCapabilities)
                    rtpParametersByKind["video"] = getSendingRtpParameters("video", extendedRtpCapabilities)
                    return SendHandler(rtpParametersByKind, settings)
                }
                "recv" -> {
                    rtpParametersByKind["audio"] = getReceivingFullRtpParameters("audio", extendedRtpCapabilities)
                    rtpParametersByKind["video"] = getReceivingFullRtpParameters("video", extendedRtpCapabilities)
                    return RecvHandler(rtpParametersByKind, settings)
                }
                else -> return  null
            }
        }
    }

    protected var _pc: RTCPeerConnection
    protected var _rtpParametersByKind: HashMap<String, RTCRtpParameters>
    protected var _remoteSdp: RemotePlanBSdp.RemoteSdp?
    protected var _transportReady: Boolean = false
    protected var _transportUpdated: Boolean = false
    protected var _transportCreated: Boolean = false

    init {
        // RTCPeerConnection instance.
        val config = HashMap<String, Any>()
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
                else-> Unit
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
) : Handler("send", rtpParametersByKind, settings) {

    //Local stream ids
    private val _mediaStreamLabels: List<String> = listOf("ARDAMS")

    // Ids of alive local tracks.
    private val _trackIds: HashSet<String> = HashSet()

    init {
        // Got transport local and remote parameters.
        this._transportReady = false
    }

    fun addProducer(producer: Any): Observable<RTCRtpParameters> {
        //only for test
        val track = MediaStreamTrack(1)
        val id = 123
        val simulcast = true
        val kind = "video"

        logger.debug("addProducer() [id:$id, kind:$kind, trackId:${track.id()}]")

        // Add the track id to the Set.
        this._trackIds.add(track.id())

        var rtpSender: RtpSender
        var localSdpObj: com.dingsoft.sdptransform.SessionDescription = com.dingsoft.sdptransform.SessionDescription()

        return Observable.just(Unit)
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

                    val sdpObject = SdpTransform().parse(cloneOffer.description)

                    addPlanBSimulcast(sdpObject, track)

                    val offerSdp = SdpTransform().write(sdpObject)

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
                fillRtpParametersForTrack(rtpParameters!!, localSdpObj, track)

                Observable.create(ObservableOnSubscribe<RTCRtpParameters> {
                    //next
                    it.onNext(rtpParameters)
                })
            }
    }

    fun removeProducer(producer: Any): Observable<Any> {
        //only for test
        val track = MediaStreamTrack(1)
        val id = 123
        val kind = "video"

        if (!this._trackIds.contains(track.id()))
            return Observable.create {
                it.onError(Throwable("track not found"))
            }

        logger.debug("removeProducer() [id:$id, kind:$kind, trackId:${track.id()}]")

        return Observable.just(Unit)
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

                this._pc.setLocalDescription(offer)
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

    fun replaceProducerTrack(producer: Any,
                             track: MediaStreamTrack): Observable<Any> {
        //only for test
        val id = 123
        val kind = "video"

        val oldTrack = MediaStreamTrack(2)

        logger.debug("replaceProducerTrack() [id:$id, kind:$kind, trackId:${track.id()}]")

        return Observable.just(Unit)
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

    fun restartIce(remoteIceParameters: RTCIceParameters): Observable<Any> {
        logger.debug("restartIce()")

        // Provide the remote SDP handler with new remote ICE parameters.
        this._remoteSdp?.updateTransportRemoteIceParameters(remoteIceParameters)

        return Observable.just(Unit)
            .flatMap {
                val mediaConstraints = MediaConstraints()
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

    private fun _setupTransport(): Observable<Unit> {
        logger.debug("_setupTransport()")
        return Observable.just(Unit)
            .flatMap {
                // Get our local DTLS parameters.
                val transportLocalParameters = TransportRemoteIceParameters()
                val sdp = this._pc.localDescription.description
                val sdpObj = SdpTransform().parse(sdp)
                val dtlsParameters = extractDtlsParameters(sdpObj)

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


class RecvHandler(
    rtpParametersByKind: HashMap<String, RTCRtpParameters>,
    settings: RoomOptions
) : Handler("recv", rtpParametersByKind, settings) {

    // Seen media kinds.
    private val _kinds:HashSet<String> = HashSet()

    // Map of Consumers information indexed by consumer.id.
    // - kind {String}
    // - trackId {String}
    // - ssrc {Number}
    // - rtxSsrc {Number}
    // - cname {String}
    // @type {Map<Number, Object>}
    private val _consumerInfos: HashMap<Int,ConsumerInfo> = HashMap()

    init {
        // Got transport remote parameters.
        this._transportCreated = false

        // Got transport local parameters.
        this._transportUpdated = false
    }

    fun addConsumer(consumer: Any): Observable<MediaStreamTrack> {
        //only for test
        val id = 123
        val kind = "video"
        val encoding = RtpEncoding()
        val cname = "cname"
        val rtxSsrc = 234

        val consumerInfo: ConsumerInfo = ConsumerInfo(
            kind,
            "recv-stream-$id",
            "consumer-$kind-$id",
            0,
            cname,
            "",
            false,
            rtxSsrc
        )

        logger.debug("addConsumer() [id:$id, kind:$kind]")

        if (encoding.rtx !=null && encoding.rtx!![encoding.ssrc] !=null)
            consumerInfo.rtxSsrc = encoding.rtx!![encoding.ssrc]

        this._consumerInfos[id] = consumerInfo
        this._kinds.add(kind)

        return Observable.just(Unit)
            .flatMap {
                if (!this._transportCreated){
                     this._setupTransport()
                }else{
                    Observable.create{
                        //next
                        it.onNext(Unit)
                    }
                }
            }
            .flatMap {
                //only for test
                val _consumerInfos: HashMap<Int,com.versatica.mediasoup.handlers.sdp.ConsumerInfo> = HashMap()

                val remoteSdp = (this._remoteSdp as RemotePlanBSdp.RecvRemoteSdp).createOfferSdp(ArrayList<String>(_kinds),ArrayList(_consumerInfos.values))
                val offer = SessionDescription(SessionDescription.Type.fromCanonicalForm("offer"), remoteSdp)

                logger.debug(
                    "addConsumer() | calling pc.setRemoteDescription() [offer:${offer.description}]")

                this._pc.setRemoteDescription(offer)
            }
            .flatMap {
                this._pc.createAnswer(MediaConstraints())
            }
            .flatMap { answer ->
                logger.debug(
                    "addConsumer() | calling pc.setLocalDescription() [answer:${answer.description}]")
                this._pc.setLocalDescription(answer)
            }.flatMap {
                if (!this._transportUpdated) {
                    _updateTransport()
                } else {
                    Observable.create{
                        //next
                        it.onNext(Unit)
                    }
                }
            }.flatMap {
                val newRtpReceiver = this._pc.getReceivers().find {
                    val track = it.track()
                    if (track == null){
                        false
                    }else{
                        track.id() === consumerInfo.trackId
                    }
                }

                if (newRtpReceiver == null)
                    throw Throwable("RTCRtpSender not found")

                Observable.create(ObservableOnSubscribe<MediaStreamTrack> {
                    //next
                    it.onNext(newRtpReceiver.track()!!)
                })
            }
    }

    fun removeConsumer(consumer: Any): Observable<Any> {
        //only for test
        val id = 123
        val kind = "video"

        logger.debug("removeConsumer() [id:$id, kind:$kind]")

        if (!this._consumerInfos.contains(id))
            return Observable.create {
                it.onError(Throwable("Consumer not found"))
            }

        return Observable.just(Unit)
            .flatMap {
                //only for test
                val _consumerInfos: HashMap<Int,com.versatica.mediasoup.handlers.sdp.ConsumerInfo> = HashMap()

                val remoteSdp = (this._remoteSdp as RemotePlanBSdp.RecvRemoteSdp).createOfferSdp(ArrayList<String>(_kinds),ArrayList(_consumerInfos.values))
                val offer = SessionDescription(SessionDescription.Type.fromCanonicalForm("offer"), remoteSdp)

                logger.debug(
                    "removeConsumer() | calling pc.setRemoteDescription() [offer:${offer.description}]")

                this._pc.setRemoteDescription(offer)
            }
            .flatMap {
                this._pc.createAnswer(MediaConstraints())
            }
            .flatMap { answer ->
                logger.debug(
                    "removeConsumer() | calling pc.setLocalDescription() [answer:${answer.description}]")
                this._pc.setLocalDescription(answer)
            }
    }

    fun restartIce(remoteIceParameters: RTCIceParameters): Observable<Any> {
        logger.debug("restartIce()")

        // Provide the remote SDP handler with new remote ICE parameters.
        (this._remoteSdp as RemotePlanBSdp.RecvRemoteSdp).updateTransportRemoteIceParameters(remoteIceParameters)

        return Observable.just(Unit)
            .flatMap {
                //only for test
                val _consumerInfos: HashMap<Int,com.versatica.mediasoup.handlers.sdp.ConsumerInfo

                        > = HashMap()

                val remoteSdp = (this._remoteSdp as RemotePlanBSdp.RecvRemoteSdp).createOfferSdp(ArrayList<String>(_kinds),ArrayList(_consumerInfos.values))
                val offer = SessionDescription(SessionDescription.Type.fromCanonicalForm("offer"), remoteSdp)

                logger.debug(
                    "restartIce() | calling pc.setRemoteDescription() [offer:${offer.description}]")

                this._pc.setRemoteDescription(offer)
            }
            .flatMap {
                this._pc.createAnswer(MediaConstraints())
            }
            .flatMap { answer ->
                logger.debug(
                    "restartIce() | calling pc.setLocalDescription() [answer:${answer.description}]")
                this._pc.setLocalDescription(answer)
            }
    }


    private fun _setupTransport(): Observable<Unit> {

        logger.debug("_setupTransport()")

        return Observable.just(Unit)
            .flatMap {
                // We need transport remote parameters
                Observable.create(ObservableOnSubscribe<Any> {
                    //next
                    this.safeEmitAsPromise(it, "@needcreatetransport").subscribe()
                })
            }
            .flatMap { transportRemoteParameters ->
                // Provide the remote SDP handler with transport remote parameters.
                (this._remoteSdp as RemotePlanBSdp.SendRemoteSdp).transportRemoteParameters = (transportRemoteParameters as TransportRemoteIceParameters)

                this._transportCreated = true

                Observable.create(ObservableOnSubscribe<Unit> {
                    //next
                    it.onNext(Unit)
                })
            }
    }

    private fun _updateTransport(): Observable<Unit> {
        logger.debug("_updateTransport()")
        // Get our local DTLS parameters.
        // const transportLocalParameters = {}
        val sdp = this._pc.localDescription.description
        val sdpObj = SdpTransform().parse(sdp)
        val dtlsParameters = extractDtlsParameters(sdpObj)
        //val transportLocalParameters = { dtlsParameters }
        val transportLocalParameters = JSON.toJSONString(dtlsParameters)

        // We need to provide transport local parameters.
        this.safeEmit("@needupdatetransport", transportLocalParameters)

        this._transportUpdated = true

        return Observable.create{
            //next
            it.onNext(Unit)
        }
    }
}