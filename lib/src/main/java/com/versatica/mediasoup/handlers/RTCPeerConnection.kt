package com.versatica.mediasoup.handlers

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.versatica.eventemitter.EventEmitter
import com.versatica.mediasoup.sdp.RTCIceConnectionState
import com.versatica.mediasoup.sdp.RTCIceGathererState
import com.versatica.mediasoup.sdp.RTCSignalingState
import com.versatica.mediasoup.webrtc.WebRTCModule
import io.reactivex.Observable
import org.webrtc.*

class RTCPeerConnection (configuration: HashMap<String,Any>): EventEmitter() {
    enum class PEER_CONNECTION_EVENTS(val v: String) {
        CONNECTION_STATE_CHANGE("connectionstatechange"),
        ICE_CANDIDATE("icecandidate"),
        ICE_CANDIDATE_ERROR("icecandidateerror"),
        ICE_CONNECTION_STATE_CHANGE("iceconnectionstatechange"),
        ICE_GATHERING_STATE_CHANGE("icegatheringstatechange"),
        NEGOTIATION_NEEDED("negotiationneeded"),
        SIGNALING_STATE_CHANGE("signalingstatechange")
    }

    companion object {
        private var nextPeerConnectionId: Int = 0
    }
    lateinit var localDescription: SessionDescription
    lateinit var remoteDescription: SessionDescription

    var signalingState: RTCSignalingState = RTCSignalingState.STABLE
    var iceGatheringState: RTCIceGathererState = RTCIceGathererState.NEW
    var iceConnectionState: RTCIceConnectionState = RTCIceConnectionState.NEW


    private var _peerConnectionId : Int = 0

    init {
        this._peerConnectionId = nextPeerConnectionId++
        _getWebRTCModule().peerConnectionInit(configuration,this._peerConnectionId)
        _registerEvents()
    }

    fun createOffer(options: MediaConstraints): Observable<SessionDescription> {
        return Observable.create {
            _getWebRTCModule().peerConnectionCreateOffer(this._peerConnectionId, options){ args ->
                var successful = args[0] as Boolean
                if (successful){
                    var sdp = args[1] as SessionDescription
                    it.onNext(sdp)
                }else{
                    var data = args[1] as String
                    it.onError(Throwable(data))
                }
            }
        }
    }

    fun createAnswer(options: MediaConstraints): Observable<SessionDescription> {
        return Observable.create {
            _getWebRTCModule().peerConnectionCreateAnswer(this._peerConnectionId, options){ args ->
                var successful = args[0] as Boolean
                if (successful){
                    var sdp = args[1] as SessionDescription
                    it.onNext(sdp)
                }else{
                    var data = args[1] as String
                    it.onError(Throwable(data))
                }
            }
        }
    }

    fun setLocalDescription(sessionDescription: SessionDescription): Observable<Unit> {
        return Observable.create {
            _getWebRTCModule().peerConnectionSetLocalDescription(sessionDescription,this._peerConnectionId){ args ->
                var successful = args[0] as Boolean
                if (successful){
                    this.localDescription = sessionDescription
                    it.onNext(Unit)
                }else{
                    var data = args[1] as String
                    it.onError(Throwable(data))
                }
            }
        }
    }

    fun setRemoteDescription(sessionDescription: SessionDescription): Observable<Unit> {
        return Observable.create {
            _getWebRTCModule().peerConnectionSetRemoteDescription(sessionDescription,this._peerConnectionId){ args ->
                var successful = args[0] as Boolean
                if (successful){
                    this.remoteDescription = sessionDescription
                    it.onNext(Unit)
                }else{
                    var data = args[1] as String
                    it.onError(Throwable(data))
                }
            }
        }
    }

    fun setConfiguration(configuration: HashMap<String,Any>){
        _getWebRTCModule().peerConnectionSetConfiguration(configuration,this._peerConnectionId)
    }

//    fun getTransceivers():List<RtpTransceiver>{
//        return _getWebRTCModule().peerConnectionGetTransceivers(this._peerConnectionId)
//    }
//
//    fun addTransceiver(track: MediaStreamTrack): RtpTransceiver?{
//        return _getWebRTCModule().peerConnectionAddTransceiver(this._peerConnectionId,track)
//    }
//
//    fun addTransceiver(track: MediaStreamTrack,
//                       init: RtpTransceiver.RtpTransceiverInit
//    ): RtpTransceiver?{
//        return _getWebRTCModule().peerConnectionAddTransceiver(this._peerConnectionId,track,init)
//    }
//
//    fun addTransceiver(mediaType: MediaStreamTrack.MediaType): RtpTransceiver?{
//        return _getWebRTCModule().peerConnectionAddTransceiver(this._peerConnectionId,mediaType)
//    }
//
//    fun addTransceiver(mediaType: MediaStreamTrack.MediaType,
//                       init: RtpTransceiver.RtpTransceiverInit
//    ): RtpTransceiver?{
//        return _getWebRTCModule().peerConnectionAddTransceiver(this._peerConnectionId,mediaType,init)
//    }

    fun getSenders():List<RtpSender>{
        return _getWebRTCModule().peerConnectionGetSenders(this._peerConnectionId)
    }

    fun getReceivers():List<RtpReceiver>{
        return _getWebRTCModule().peerConnectionGetReceivers(this._peerConnectionId)
    }

    fun addTrack(mediaStreamTrack:MediaStreamTrack):RtpSender{
        return _getWebRTCModule().peerConnectionAddTrack(this._peerConnectionId,mediaStreamTrack)
    }

    fun addTrack(mediaStreamTrack:MediaStreamTrack,
                 streamIds: List<String>):RtpSender{
        return _getWebRTCModule().peerConnectionAddTrack(this._peerConnectionId,mediaStreamTrack,streamIds)
    }

    fun removeTrack(sender:RtpSender):Boolean{
        return _getWebRTCModule().peerConnectionRemoveTrack(this._peerConnectionId,sender)
    }

    fun close(){
        _getWebRTCModule().peerConnectionClose(this._peerConnectionId)
    }


    private fun _registerEvents(){
        this.on("peerConnectionOnRenegotiationNeeded"){
            this.emit("negotiationneeded")
        }

        this.on("peerConnectionIceConnectionChanged"){
            var param = it[0] as HashMap<String,Any>
            var state = param["iceConnectionState"] as String
            this.iceConnectionState = RTCIceConnectionState.valueOf(state)
            this.emit("iceconnectionstatechange")
            if (state === "closed"){
                // This PeerConnection is done, clean up event handlers.
                this._unregisterEvents()
            }
        }

        this.on("peerConnectionSignalingStateChanged"){
            var param = it[0] as HashMap<String,Any>
            var state = param["signalingState"] as String
            this.signalingState = RTCSignalingState.valueOf(state)
            this.emit("signalingstatechange")
        }

        this.on("mediaStreamTrackMuteChanged"){
            //TODO
        }

        this.on("peerConnectionGotICECandidate"){
            var param = it[0] as HashMap<String,Any>
            var iceCandidate = param["nativeIceCandidate"] as IceCandidate
            this.emit("signalingstatechange",iceCandidate)
        }

        this.on("peerConnectionIceGatheringChanged"){
            var param = it[0] as HashMap<String,Any>
            var state = param["iceGatheringState"] as String
            this.iceGatheringState = RTCIceGathererState.valueOf(state)
            if (this.iceGatheringState == RTCIceGathererState.COMPLETE){
                this.emit("icecandidate")
            }else{
                this.emit("signalingstatechange")
            }
        }

        this.on("peerConnectionDidOpenDataChannel"){
            //TODO
        }
    }


    private fun _unregisterEvents(){
        this.removeAllListeners()
    }

    private fun _getWebRTCModule():WebRTCModule{
        return WebRTCModule.getInstance(BaseApplication.getAppContext())
    }
}