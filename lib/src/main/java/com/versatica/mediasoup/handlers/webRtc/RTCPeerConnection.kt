package com.versatica.mediasoup.handlers.webRtc

import com.versatica.eventemitter.EventEmitter
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.handlers.BaseApplication
import com.versatica.mediasoup.webrtc.Callback
import io.reactivex.Observable
import org.webrtc.*

class RTCPeerConnection (configuration: HashMap<String,Any>): EventEmitter(), PeerConnection.Observer{
    val logger = Logger("RTCPeerConnection")

    companion object {
        private var nextPeerConnectionId: Int = 0
    }
    lateinit var localDescription: SessionDescription
    lateinit var remoteDescription: SessionDescription

    var signalingState: PeerConnection.SignalingState? = PeerConnection.SignalingState.STABLE
    var iceGatheringState: PeerConnection.IceGatheringState? = PeerConnection.IceGatheringState.NEW
    var iceConnectionState: PeerConnection.IceConnectionState? = PeerConnection.IceConnectionState.NEW


    private var _peerConnectionId : Int = 0
    //PeerConnection对象
    private var pc: PeerConnection? = null

    init {
        this._peerConnectionId = nextPeerConnectionId++
        pc = _getWebRTCModule().peerConnectionInit(configuration,this)
    }

    fun createOffer(options: MediaConstraints): Observable<SessionDescription> {
        return Observable.create {
            peerConnectionCreateOffer(options,callback = Callback { args ->
                var successful = args[0] as Boolean
                if (successful){
                    var sdp = args[1] as SessionDescription
                    it.onNext(sdp)
                }else{
                    var data = args[1] as String
                    it.onError(Throwable(data))
                }
            })
        }
    }

    private fun peerConnectionCreateOffer(
        constraints: MediaConstraints,
        callback: Callback){
        ThreadUtil.runOnExecutor(Runnable {
            peerConnectionCreateOfferAsync(constraints, callback)
        })
    }

    private fun peerConnectionCreateOfferAsync(
        constraints: MediaConstraints,
        callback: Callback) {
        if (pc != null) {
            pc?.createOffer(object : SdpObserver {
                override fun onCreateFailure(s: String) {
                    callback.invoke(false, s)
                }

                override fun onCreateSuccess(sdp: SessionDescription) {
                    callback.invoke(true, sdp)
                }

                override fun onSetFailure(s: String) {}

                override fun onSetSuccess() {}
            }, constraints)
        } else {
            logger.debug( "peerConnectionCreateOffer() peerConnection is null")
            callback.invoke(false, "peerConnection is null")
        }
    }

    fun createAnswer(options: MediaConstraints): Observable<SessionDescription> {
        return Observable.create {
            peerConnectionCreateAnswer(options,callback = Callback { args ->
                var successful = args[0] as Boolean
                if (successful){
                    var sdp = args[1] as SessionDescription
                    it.onNext(sdp)
                }else{
                    var data = args[1] as String
                    it.onError(Throwable(data))
                }
            })
        }
    }

    private fun peerConnectionCreateAnswer(
        constraints: MediaConstraints,
        callback: Callback
    ) {
        ThreadUtil.runOnExecutor(Runnable {
            peerConnectionCreateAnswerAsync(constraints, callback)
        })
    }

    private fun peerConnectionCreateAnswerAsync(
        constraints: MediaConstraints,
        callback: Callback
    ) {
        if (pc != null) {
            pc!!.createAnswer(object : SdpObserver {
                override fun onCreateFailure(s: String) {
                    callback.invoke(false, s)
                }

                override fun onCreateSuccess(sdp: SessionDescription) {
                    callback.invoke(true, sdp)
                }

                override fun onSetFailure(s: String) {}

                override fun onSetSuccess() {}
            }, constraints)
        } else {
            logger.debug("peerConnectionCreateAnswer() peerConnection is null")
            callback.invoke(false, "peerConnection is null")
        }
    }

    fun setLocalDescription(sessionDescription: SessionDescription): Observable<Unit> {
        return Observable.create {
            peerConnectionSetLocalDescription(sessionDescription,callback = Callback { args ->
                var successful = args[0] as Boolean
                if (successful){
                    this.localDescription = sessionDescription
                    it.onNext(Unit)
                }else{
                    var data = args[1] as String
                    it.onError(Throwable(data))
                }
            })
        }
    }

    private fun peerConnectionSetLocalDescription(
        sdp: SessionDescription,
        callback: Callback
    ) {
        ThreadUtil.runOnExecutor(Runnable {
            peerConnectionSetLocalDescriptionAsync(sdp, callback)
        })
    }

    private fun peerConnectionSetLocalDescriptionAsync(
        sdp: SessionDescription,
        callback: Callback) {
        logger.debug("peerConnectionSetLocalDescription() start")
        if (pc != null) {
            pc!!.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {}

                override fun onSetSuccess() {
                    callback.invoke(true)
                }

                override fun onCreateFailure(s: String) {}

                override fun onSetFailure(s: String) {
                    callback.invoke(false, s)
                }
            }, sdp)
        } else {
            logger.debug("peerConnectionSetLocalDescription() peerConnection is null")
            callback.invoke(false, "peerConnection is null")
        }
        logger.debug("peerConnectionSetLocalDescription() end")
    }

    fun setRemoteDescription(sessionDescription: SessionDescription): Observable<Unit> {
        return Observable.create {
            peerConnectionSetRemoteDescription(sessionDescription,callback = Callback { args ->
                var successful = args[0] as Boolean
                if (successful){
                    this.remoteDescription = sessionDescription
                    it.onNext(Unit)
                }else{
                    var data = args[1] as String
                    it.onError(Throwable(data))
                }
            })
        }
    }

    private fun peerConnectionSetRemoteDescription(
        sdp: SessionDescription,
        callback: Callback
    ) {
        ThreadUtil.runOnExecutor(Runnable {
            peerConnectionSetRemoteDescriptionAsync(sdp, callback)
        })
    }

    private fun peerConnectionSetRemoteDescriptionAsync(
        sdp: SessionDescription,
        callback: Callback
    ) {
        logger.debug("peerConnectionSetRemoteDescription() start")
        if (pc != null) {
            pc!!.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {}

                override fun onSetSuccess() {
                    callback.invoke(true)
                }

                override fun onCreateFailure(s: String) {}

                override fun onSetFailure(s: String) {
                    callback.invoke(false, s)
                }
            }, sdp)
        } else {
            logger.debug("peerConnectionSetRemoteDescription() peerConnection is null")
            callback.invoke(false, "peerConnection is null")
        }
        logger.debug("peerConnectionSetRemoteDescription() end")
    }

    fun setConfiguration(configuration: HashMap<String,Any>){
        setConfiguration(_getWebRTCModule().parseRTCConfiguration(configuration))
    }

    fun setConfiguration(configuration: PeerConnection.RTCConfiguration){
        peerConnectionSetConfiguration(configuration)
    }

    private fun peerConnectionSetConfiguration(
        configuration: PeerConnection.RTCConfiguration
    ) {
        ThreadUtil.runOnExecutor(Runnable {
            peerConnectionSetConfigurationAsync(configuration)
        })
    }

    private fun peerConnectionSetConfigurationAsync(
        configuration: PeerConnection.RTCConfiguration
    ) {
        pc?.setConfiguration(configuration)
    }

    fun getSenders():List<RtpSender>{
        return peerConnectionGetSenders()
    }

    private fun peerConnectionGetSenders(): List<RtpSender> {
        logger.debug("peerConnectionGetSenders() start")
        if (pc != null) {
            logger.debug("peerConnectionGetSenders() end")
            return pc!!.getSenders()
        } else {
            logger.debug( "peerConnectionGetSenders() peerConnection is null")
            return ArrayList()
        }
    }

    fun getReceivers():List<RtpReceiver>{
        return peerConnectionGetReceivers()
    }

    fun peerConnectionGetReceivers(): List<RtpReceiver> {
        logger.debug("peerConnectionGetReceivers() start")
        if (pc != null) {
            logger.debug("peerConnectionGetReceivers() end")
            return pc!!.getReceivers()
        } else {
            logger.debug("peerConnectionGetReceivers() peerConnection is null")
            return ArrayList()
        }
    }

    fun addTrack(mediaStreamTrack:MediaStreamTrack,
                 streamIds: List<String> = emptyList()):RtpSender?{
        return peerConnectionAddTrack(mediaStreamTrack,streamIds)
    }

    private fun peerConnectionAddTrack(
        mediaStreamTrack: MediaStreamTrack,
        streamIds: List<String>
    ): RtpSender? {
        logger.debug("peerConnectionAddTrack() start")
        if (pc != null) {
            logger.debug("peerConnectionAddTrack() end")
            return pc!!.addTrack(mediaStreamTrack, streamIds)
        } else {
            logger.debug("peerConnectionAddTrack() peerConnection is null")
            return null
        }
    }

    fun removeTrack(sender:RtpSender):Boolean{
        return peerConnectionRemoveTrack(sender)
    }

    private fun peerConnectionRemoveTrack(
        sender: RtpSender
    ): Boolean {
        logger.debug("peerConnectionRemoveTrack() start")
        if (pc != null) {
            logger.debug( "peerConnectionRemoveTrack() end")
            return pc!!.removeTrack(sender)
        } else {
            logger.debug("peerConnectionRemoveTrack() peerConnection is null")
            return false
        }
    }

    fun getTransceivers():List<RtpTransceiver>{
        return peerConnectionGetTransceivers()
    }

    private fun peerConnectionGetTransceivers(): List<RtpTransceiver> {
        logger.debug("getTransceivers() start")
        if (pc != null) {
            logger.debug("getTransceivers() end")
            return pc!!.getTransceivers()
        } else {
            logger.debug("getTransceivers() peerConnection is null")
            return ArrayList()
        }
    }

    fun addTransceiver(track: MediaStreamTrack,
                       init: RtpTransceiver.RtpTransceiverInit = RtpTransceiver.RtpTransceiverInit()
    ): RtpTransceiver?{
        return peerConnectionAddTransceiver(track,init)
    }

    private fun peerConnectionAddTransceiver(
        track: MediaStreamTrack,
        init: RtpTransceiver.RtpTransceiverInit?
    ): RtpTransceiver? {
        logger.debug("addTransceiver() start")
        if (pc != null) {
            logger.debug("addTransceiver() end")
            return pc!!.addTransceiver(track, init)
        } else {
            logger.debug("addTransceiver( ) peerConnection is null")
            return null
        }
    }

    fun addTransceiver(mediaType: MediaStreamTrack.MediaType,
                       init: RtpTransceiver.RtpTransceiverInit = RtpTransceiver.RtpTransceiverInit()
    ): RtpTransceiver?{
        return peerConnectionAddTransceiver(mediaType,init)
    }

    private fun peerConnectionAddTransceiver(
        mediaType: MediaStreamTrack.MediaType,
        init: RtpTransceiver.RtpTransceiverInit?
    ): RtpTransceiver? {
        logger.debug("addTransceiver() start")
        if (pc != null) {
            logger.debug("addTransceiver() end")
            return pc!!.addTransceiver(mediaType, init)
        } else {
            logger.debug("addTransceiver( ) peerConnection is null")
            return null
        }
    }

    fun close(){
        pc?.close()
        pc?.dispose()
    }

    fun getNativePeerConnection(): PeerConnection?{
        return pc
    }

    private fun _getWebRTCModule(): WebRTCModule {
        return WebRTCModule.getInstance(BaseApplication.getAppContext())
    }

    /********************** PeerConnection.Observer **********************/
    override fun onIceCandidate(iceCandidate: IceCandidate?) {
        this.emit("onIceCandidate",iceCandidate!!)
    }

    override fun onDataChannel(dataChannel: DataChannel?) {
        this.emit("onDataChannel",dataChannel!!)
    }

    override fun onIceConnectionReceivingChange(isChange: Boolean) {
        this.emit("onIceConnectionReceivingChange",isChange)
    }

    override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {
        this.iceConnectionState = iceConnectionState
        this.emit("onIceConnectionChange",iceConnectionState!!)
    }

    override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState?) {
        this.iceGatheringState = iceGatheringState
        this.emit("onIceGatheringChange",iceGatheringState!!)
    }

    override fun onAddStream(mediaStream: MediaStream?) {
        this.emit("onAddStream",mediaStream!!)
    }

    override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) {
        this.signalingState = signalingState
        this.emit("onSignalingChange",signalingState!!)
    }

    override fun onIceCandidatesRemoved(iceCandidates: Array<out IceCandidate>?) {
        this.emit("onIceCandidatesRemoved",iceCandidates!!)
    }

    override fun onRemoveStream(mediaStream: MediaStream?) {
        this.emit("onRemoveStream",mediaStream!!)
    }

    override fun onRenegotiationNeeded() {
        this.emit("onRenegotiationNeeded")
    }

    override fun onAddTrack(rtpReceiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        this.emit("onAddTrack",rtpReceiver!!,mediaStreams!!)
    }

    override fun onTrack(rtpTransceiver: RtpTransceiver?) {
        this.emit("onTrack",rtpTransceiver!!)
    }
}