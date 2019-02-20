package com.versatica.mediasoup.handlers

import com.alibaba.fastjson.JSON
import com.dingsoft.sdptransform.SdpTransform
import com.versatica.mediasoup.*
import com.versatica.mediasoup.handlers.sdp.*
import com.versatica.mediasoup.handlers.webRtc.RTCPeerConnection
import com.versatica.mediasoup.handlers.webRtc.WebRTCModule
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import org.webrtc.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

val logger = Logger("Handler")

open class Handler(
    direction: String,
    rtpParametersByKind: HashMap<String, RTCRtpParameters>,
    settings: RoomOptions,
    private var logger: Logger = Logger("Handler")
) : EnhancedEventEmitter(logger) {
    companion object {
        fun getNativeRtpCapabilities(): Observable<RTCRtpCapabilities> {
            logger.debug("getNativeRtpCapabilities()")

            val config = HashMap<String, Any>()
            config["iceTransportPolicy"] = "all"
            config["bundlePolicy"] = "max-bundle"
            config["rtcpMuxPolicy"] = "require"
            config["sdpSemantics"] = "unified_plan"
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

            pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO)
            pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO)
            return pc.createOffer(mediaConstraints).flatMap { offer ->
                Observable.create(ObservableOnSubscribe<RTCRtpCapabilities> {
                    try {
                        pc.close()

                        val sdpObj = SdpTransform().parse(offer.description)
                        val nativeRtpCapabilities = CommonUtils.extractRtpCapabilities(sdpObj)
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
        ): Handler? {
            val rtpParametersByKind: HashMap<String, RTCRtpParameters> = HashMap()
            when (direction) {
                "send" -> {
                    rtpParametersByKind["audio"] = Ortc.getSendingRtpParameters("audio", extendedRtpCapabilities)
                    rtpParametersByKind["video"] = Ortc.getSendingRtpParameters("video", extendedRtpCapabilities)
                    return SendHandler(rtpParametersByKind, settings)
                }
                "recv" -> {
                    rtpParametersByKind["audio"] = Ortc.getReceivingFullRtpParameters("audio", extendedRtpCapabilities)
                    rtpParametersByKind["video"] = Ortc.getReceivingFullRtpParameters("video", extendedRtpCapabilities)
                    return RecvHandler(rtpParametersByKind, settings)
                }
                else -> return null
            }
        }
    }

    protected var _pc: RTCPeerConnection
    protected var _rtpParametersByKind: HashMap<String, RTCRtpParameters>
    protected var _remoteSdp: RemoteUnifiedPlanSdp.RemoteSdp?
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
        config["sdpSemantics"] = "unified_plan"
        _pc = RTCPeerConnection(config)

        // Generic sending RTP parameters for audio and video.
        _rtpParametersByKind = rtpParametersByKind

        // Remote SDP handler.
        _remoteSdp = RemoteUnifiedPlanSdp.newInstance(direction, rtpParametersByKind)

        //Handle RTCPeerConnection connection status.
        this._pc.on("onIceConnectionChange") {
            when (this._pc.iceConnectionState) {
                PeerConnection.IceConnectionState.CHECKING -> this.emit("@connectionstatechange", "connecting")
                PeerConnection.IceConnectionState.CONNECTED -> this.emit("@connectionstatechange", "connected")
                PeerConnection.IceConnectionState.COMPLETED -> this.emit("@connectionstatechange", "connected")
                PeerConnection.IceConnectionState.FAILED -> this.emit("@connectionstatechange", "failed")
                PeerConnection.IceConnectionState.DISCONNECTED -> this.emit("@connectionstatechange", "disconnected")
                PeerConnection.IceConnectionState.CLOSED -> this.emit("@connectionstatechange", "closed")
                else -> Unit
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

    fun addProducer(producer: Producer): Observable<RTCRtpParameters> {
        val track = producer.track

        logger.debug("addProducer() [id:${producer.id}, kind:${producer.kind()}, trackId:${track.id()}]")

        if(this._trackIds.contains(track.id()))
            return Observable.create {
                //next
                it.onError(Throwable("track already added"))
            }

        // Add the track id to the Set.
        this._trackIds.add(track.id())

        var rtpSender: RtpSender?
        var transceiver: RtpTransceiver? = null
        var localSdpObj: com.dingsoft.sdptransform.SessionDescription = com.dingsoft.sdptransform.SessionDescription()

        return Observable.just(Unit)
            .flatMap {
                // Let's check if there is any inactive transceiver for same kind and
                // reuse it if so.
                transceiver = this._pc.getTransceivers().find {
                    it.receiver.track()!!.kind() == track.kind() && it.direction == RtpTransceiver.RtpTransceiverDirection.INACTIVE
                }

                if (transceiver != null){
                    logger.debug("addProducer() | reusing an inactive transceiver")

                    transceiver!!.direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY

                    transceiver!!.sender.setTrack(track,true)
                } else {
                    transceiver = this._pc.addTransceiver(track, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
                }

                Observable.create(ObservableOnSubscribe<Unit> {
                    it.onNext(Unit)
                })
            }
            .flatMap {
//                // Add the stream to the PeerConnection.
//                rtpSender = this._pc.addTrack(track, _mediaStreamLabels)

                this._pc.createOffer(MediaConstraints())
            }.flatMap { offer ->
                //clone offer
                var cloneOffer = SessionDescription(offer.type, offer.description)

                // If simulcast is set, mangle the offer.
                if (producer.simulcast is HashMap<*, *>) {
                    logger.debug("addProducer() | enabling simulcast")

                    val sdpObject = SdpTransform().parse(cloneOffer.description)

                    UnifiedPlanUtils.addPlanBSimulcast(sdpObject, track, transceiver!!.mid)

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

                val remoteSdp = (this._remoteSdp as RemoteUnifiedPlanSdp.SendRemoteSdp).createAnswerSdp(localSdpObj)

                val answer = SessionDescription(SessionDescription.Type.fromCanonicalForm("answer"), remoteSdp)

                logger.debug("addProducer() | calling pc.setRemoteDescription() [answer:${answer.toString()}]")

                this._pc.setRemoteDescription(answer)
            }.flatMap {
                val rtpParameters = this._rtpParametersByKind[producer.kind()]

                // Fill the RTP parameters for this track.
                UnifiedPlanUtils.fillRtpParametersForTrack(rtpParameters!!, localSdpObj, track, transceiver!!.mid, true)

                Observable.create(ObservableOnSubscribe<RTCRtpParameters> {
                    //next
                    it.onNext(rtpParameters)
                })
            }
    }

    fun removeProducer(producer: Producer): Observable<Any> {
        val track = producer.track

        if (!this._trackIds.contains(track.id()))
            return Observable.create {
                it.onError(Throwable("track not found"))
            }

        logger.debug("removeProducer() [id:${producer.id}, kind:${producer.kind()}, trackId:${track.id()}]")

        return Observable.just(Unit)
            .flatMap {
                // Get the associated RTCRtpSender.
                val rtpSender = this._pc.getSenders().find {
                    it.track() == track
                }

                if (rtpSender == null)
                    throw Throwable("RTCRtpSender not found")

                // Remove the associated RtpSender.
                this._pc.removeTrack(rtpSender)

                // Remove the track id from the Set.
                this._trackIds.remove(track.id())

                this._pc.createOffer(MediaConstraints())
            }.flatMap { offer ->
                logger.debug("removeProducer() | calling pc.setLocalDescription() [offer:$offer]")

                this._pc.setLocalDescription(offer)
            }.flatMap {
                if (this._pc.signalingState == RTCSignalingState.stable) {
                    Observable.create {
                        it.onNext(Unit)
                    }
                } else {
                    val localSdpObj = SdpTransform().parse(this._pc.localDescription.description)
                    val remoteSdp = (this._remoteSdp as RemoteUnifiedPlanSdp.SendRemoteSdp).createAnswerSdp(localSdpObj)
                    val answer = SessionDescription(SessionDescription.Type.fromCanonicalForm("answer"), remoteSdp)

                    logger.debug("removeProducer() | calling pc.setRemoteDescription() [answer:$answer]")

                    this._pc.setRemoteDescription(answer)
                }
            }
    }

    fun replaceProducerTrack(
        producer: Producer,
        track: MediaStreamTrack
    ): Observable<Any> {
        logger.debug("replaceProducerTrack() [id:${producer.id}, kind:${producer.kind()}, trackId:${track.id()}]")

        val oldTrack = producer.track

        return Observable.just(Unit)
            .flatMap {
                // Get the associated RTCRtpSender.
                val rtpSender = this._pc.getSenders().find {
                    it.track() == oldTrack
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
                val remoteSdp = (this._remoteSdp as RemoteUnifiedPlanSdp.SendRemoteSdp).createAnswerSdp(localSdpObj)
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
                val dtlsParameters = CommonUtils.extractDtlsParameters(sdpObj)

                // Let's decide that we'll be DTLS server (because we can).
                dtlsParameters.role = RTCDtlsRole.server

                transportLocalParameters.dtlsParameters = dtlsParameters

                // Provide the remote SDP handler with transport local parameters.
                (this._remoteSdp as RemoteUnifiedPlanSdp.SendRemoteSdp).transportLocalParameters = transportLocalParameters

                // We need transport remote parameters
                Observable.create(ObservableOnSubscribe<Any> {
                    //next
                    this.safeEmitAsPromise(it, "@needcreatetransport", transportLocalParameters).subscribe()
                })
            }
            .flatMap { transportRemoteParameters ->
                // Provide the remote SDP handler with transport remote parameters.
                (this._remoteSdp as RemoteUnifiedPlanSdp.SendRemoteSdp).transportRemoteParameters =
                        JSON.parseObject(transportRemoteParameters as String ,TransportRemoteIceParameters::class.java)

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
    private val _kinds: HashSet<String> = HashSet()

    // Map of Consumers information indexed by consumer.id.
    // - kind {String}
    // - trackId {String}
    // - ssrc {Number}
    // - rtxSsrc {Number}
    // - cname {String}
    // @type {Map<Number, Object>}
    private val _consumerInfos: MutableMap<Int, ConsumerInfo> = mutableMapOf()

    init {
        // Got transport remote parameters.
        this._transportCreated = false

        // Got transport local parameters.
        this._transportUpdated = false
    }

    fun addConsumer(consumer: Consumer): Observable<MediaStreamTrack> {
        logger.debug("addConsumer() [id:${consumer.id}, kind:${consumer.kind}]")

        if (this._consumerInfos.containsKey(consumer.id))
            return Observable.create {
                it.onError(Error("Consumer already added"))
            }

        val encoding = consumer.rtpParameters.encodings?.get(0)
        val cname = consumer.rtpParameters.rtcp?.cname
        val consumerInfo: ConsumerInfo = ConsumerInfo(
            consumer.kind,
            "recv-stream-${consumer.id}",
            "consumer-${consumer.kind}-${consumer.id}",
            encoding?.ssrc!!,
            cname!!,
            "${consumer.kind.get(0)}${consumer.id}",
            consumer.closed
        )

        //csb mybe wrong
        if (encoding.rtx != null && encoding.rtx!!["ssrc"] != null)
            consumerInfo.rtxSsrc = encoding.rtx!!["ssrc"]

        this._consumerInfos[consumer.id] = consumerInfo
        this._kinds.add(consumer.kind)

        return Observable.just(Unit)
            .flatMap {
                if (!this._transportCreated) {
                    this._setupTransport()
                } else {
                    Observable.create {
                        //next
                        it.onNext(Unit)
                    }
                }
            }
            .flatMap {
                val remoteSdp = (this._remoteSdp as RemoteUnifiedPlanSdp.RecvRemoteSdp).createOfferSdp(
                    ArrayList(_consumerInfos.values)
                )
                val offer = SessionDescription(SessionDescription.Type.fromCanonicalForm("offer"), remoteSdp)

                logger.debug(
                    "addConsumer() | calling pc.setRemoteDescription() [offer:${offer.description}]"
                )

                this._pc.setRemoteDescription(offer)
            }
            .flatMap {
                this._pc.createAnswer(MediaConstraints())
            }
            .flatMap { answer ->
                logger.debug(
                    "addConsumer() | calling pc.setLocalDescription() [answer:${answer.description}]"
                )
                this._pc.setLocalDescription(answer)
            }.flatMap {
                if (!this._transportUpdated) {
                    _updateTransport()
                } else {
                    Observable.create {
                        //next
                        it.onNext(Unit)
                    }
                }
            }.flatMap {
                val transceiver = this._pc.getTransceivers().find{
                    it.mid == consumerInfo.mid
                }

                if (transceiver == null)
                    throw Throwable("remote track not found")


                Observable.create(ObservableOnSubscribe<MediaStreamTrack> {
                    //next
                    it.onNext(transceiver.receiver.track()!!)
                })
            }
    }

    fun removeConsumer(consumer: Consumer): Observable<Any> {
        logger.debug("removeConsumer() [id:${consumer.id}, kind:${consumer.kind}]")

        if (!this._consumerInfos.contains(consumer.id))
            return Observable.create {
                it.onError(Throwable("Consumer not found"))
            }

        return Observable.just(Unit)
            .flatMap {
                val remoteSdp = (this._remoteSdp as RemoteUnifiedPlanSdp.RecvRemoteSdp).createOfferSdp(
                    ArrayList(_consumerInfos.values)
                )
                val offer = SessionDescription(SessionDescription.Type.fromCanonicalForm("offer"), remoteSdp)

                logger.debug(
                    "removeConsumer() | calling pc.setRemoteDescription() [offer:${offer.description}]"
                )

                this._pc.setRemoteDescription(offer)
            }
            .flatMap {
                this._pc.createAnswer(MediaConstraints())
            }
            .flatMap { answer ->
                logger.debug(
                    "removeConsumer() | calling pc.setLocalDescription() [answer:${answer.description}]"
                )
                this._pc.setLocalDescription(answer)
            }
    }

    fun restartIce(remoteIceParameters: RTCIceParameters): Observable<Any> {
        logger.debug("restartIce()")

        // Provide the remote SDP handler with new remote ICE parameters.
        (this._remoteSdp as RemoteUnifiedPlanSdp.RecvRemoteSdp).updateTransportRemoteIceParameters(remoteIceParameters)

        return Observable.just(Unit)
            .flatMap {
                val remoteSdp = (this._remoteSdp as RemoteUnifiedPlanSdp.RecvRemoteSdp).createOfferSdp(
                    ArrayList(_consumerInfos.values)
                )
                val offer = SessionDescription(SessionDescription.Type.fromCanonicalForm("offer"), remoteSdp)

                logger.debug(
                    "restartIce() | calling pc.setRemoteDescription() [offer:${offer.description}]"
                )

                this._pc.setRemoteDescription(offer)
            }
            .flatMap {
                this._pc.createAnswer(MediaConstraints())
            }
            .flatMap { answer ->
                logger.debug(
                    "restartIce() | calling pc.setLocalDescription() [answer:${answer.description}]"
                )
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
                    this.safeEmitAsPromise(it, "@needcreatetransport",Unit).subscribe()
                })
            }
            .flatMap { transportRemoteParameters ->
                // Provide the remote SDP handler with transport remote parameters.
                (this._remoteSdp as RemoteUnifiedPlanSdp.RecvRemoteSdp).transportRemoteParameters =
                        JSON.parseObject(transportRemoteParameters as String ,TransportRemoteIceParameters::class.java)

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
        val transportLocalParameters = TransportRemoteIceParameters()

        val sdp = this._pc.localDescription.description
        val sdpObj = SdpTransform().parse(sdp)
        val dtlsParameters = CommonUtils.extractDtlsParameters(sdpObj)
        transportLocalParameters.dtlsParameters = dtlsParameters

        // We need to provide transport local parameters.
        this.safeEmit("@needupdatetransport", transportLocalParameters)

        this._transportUpdated = true

        return Observable.create {
            //next
            it.onNext(Unit)
        }
    }
}