package com.versatica.mediasoup.handlers

import com.dingsoft.sdptransform.SdpTransform
import com.versatica.eventemitter.EventEmitter
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.RoomOptions
import com.versatica.mediasoup.sdp.*
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack

val logger = Logger("Handle")

class Handle(
    direction: String,
    rtpParametersByKind: RTCExtendedRtpCapabilities,
    settings: RoomOptions
) : EventEmitter() {
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
    }

    lateinit var _pc: RTCPeerConnection
    lateinit var _rtpParametersByKind: RTCExtendedRtpCapabilities
    lateinit var _remoteSdp: RemoteSdp

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
        //_remoteSdp = createRemoteUnifiedPlanSdp()


    }

}