package com.versatica.mediasoup.handlers

import com.versatica.eventemitter.EventEmitter
import com.versatica.mediasoup.sdp.RTCIceConnectionState
import com.versatica.mediasoup.sdp.RTCIceGathererState
import com.versatica.mediasoup.sdp.RTCSignalingState
import com.versatica.mediasoup.webrtc.WebRTCModule
import org.webrtc.MediaStream
import org.webrtc.SessionDescription

class RTCPeerConnection (configuration: HashMap<String,Any>): EventEmitter() {
    enum class PEER_CONNECTION_EVENTS(val v: String) {
        CONNECTION_STATE_CHANGE("connectionstatechange"),
        ICE_CANDIDATE("icecandidate"),
        ICE_CANDIDATE_ERROR("icecandidateerror"),
        ICE_CONNECTION_STATE_CHANGE("iceconnectionstatechange"),
        ICE_GATHERING_STATE_CHANGE("icegatheringstatechange"),
        NEGOTIATION_NEEDED("negotiationneeded"),
        SIGNALING_STATE_CHANGE("signalingstatechange"),
        DATA_CHANNEL("datachannel"),
        ADD_STREAM("addstream"),
        REMOVE_STREAM("removestream")
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
    private val _localStreams: ArrayList<MediaStream> = ArrayList()
    private val _remoteStreams: ArrayList<MediaStream> = ArrayList()

    init {
        this._peerConnectionId = nextPeerConnectionId++
        _getWebRTCModule().peerConnectionInit(configuration,this._peerConnectionId)
        _registerEvents()
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

        this.on("peerConnectionAddedStream"){

        }

        this.on("peerConnectionRemovedStream"){

        }

        this.on("mediaStreamTrackMuteChanged"){

        }

        this.on("peerConnectionGotICECandidate"){

        }

        this.on("peerConnectionIceGatheringChanged"){

        }

        this.on("peerConnectionDidOpenDataChannel"){
            //TODO
        }
    }


    private fun _unregisterEvents(){

    }

    private fun _getWebRTCModule():WebRTCModule{
        return WebRTCModule.getInstance(BaseApplication.getAppContext())
    }

}