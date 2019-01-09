package com.versatica.mediasoup.handlers

import com.dingsoft.sdptransform.SdpTransform
import com.versatica.eventemitter.EventEmitter
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.handlers.sdp.RTCExtendedRtpCapabilities
import com.versatica.mediasoup.handlers.sdp.RTCRtpCapabilities
import com.versatica.mediasoup.handlers.sdp.extractRtpCapabilities
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import org.webrtc.MediaConstraints

val logger = Logger("Handle")

class Handle(direction: String,
             rtpParametersByKind: RTCExtendedRtpCapabilities,
             settings: HashMap<String,Any> ): EventEmitter(){
    companion object {
        fun getNativeRtpCapabilities():Observable<RTCRtpCapabilities>{
            logger.debug("getNativeRtpCapabilities()")

            var config = HashMap<String,Any>()
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
                    }catch (e: Exception){
                        it.onError(Throwable())
                    }
                })
            }
        }
    }


}