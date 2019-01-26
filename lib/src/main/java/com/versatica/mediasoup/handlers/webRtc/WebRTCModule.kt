package com.versatica.mediasoup.handlers.webRtc

import android.content.Context
import com.versatica.mediasoup.Logger
import io.reactivex.Observable
import org.webrtc.*
import kotlin.collections.ArrayList

class WebRTCModule private constructor(context: Context) {
    private val logger = Logger("WebRTCModule")

    var mFactory: PeerConnectionFactory? = null
    private val context = context

    private var getUserMediaImpl: GetUserMediaImpl? = null

    companion object {
        @Volatile
        private var instance: WebRTCModule? = null

        fun getInstance(context: Context): WebRTCModule {
            if (instance == null) {
                synchronized(WebRTCModule::class) {
                    if (instance == null) {
                        instance =
                                WebRTCModule(context)
                    }
                }
            }
            return instance!!
        }
    }

    init {
        ThreadUtil.runOnExecutor(Runnable {
            initAsync(context)
        })
    }

    /**
     * Invoked asynchronously to initialize this `WebRTCModule` instance.
     */
    private fun initAsync(context: Context) {
        // Initialize EGL contexts required for HW acceleration.
        val eglContext = EglUtil.rootEglBaseContext

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                //csb
                //.setEnableVideoHwAcceleration(eglContext != null)
                .createInitializationOptions()
        )

        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory

        if (eglContext != null) {
            encoderFactory = DefaultVideoEncoderFactory(
                eglContext,
                /* enableIntelVp8Encoder */ true,
                ///* enableH264HighProfile */ false);
                /* enableH264HighProfile */ true
            )
            decoderFactory = DefaultVideoDecoderFactory(eglContext)
        } else {
            encoderFactory = SoftwareVideoEncoderFactory()
            decoderFactory = SoftwareVideoDecoderFactory()
        }

        mFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        if (eglContext != null) {
            //csb
            //mFactory.setVideoHwAccelerationOptions(eglContext, eglContext);
        }

        getUserMediaImpl = GetUserMediaImpl(this, context)
    }

    fun getUserMedia(constraints: HashMap<*, *>): Observable<MediaStream> {
        return getUserMediaImpl!!.getUserMedia(constraints)
    }

    fun mediaStreamTrackSetEnabled(id: String, enabled: Boolean) {
        ThreadUtil.runOnExecutor(
            Runnable {
                mediaStreamTrackSetEnabledAsync(id, enabled)
            }
        )
    }

    private fun mediaStreamTrackSetEnabledAsync(id: String, enabled: Boolean) {
        getUserMediaImpl!!.mediaStreamTrackSetEnabled(id, enabled)
    }

    //@ReactMethod
    fun mediaStreamTrackStop(trackId: String) {
        getUserMediaImpl!!.mediaStreamTrackStop(trackId)
    }

    //@ReactMethod
    fun mediaStreamTrackSwitchCamera(id: String) {
        getUserMediaImpl!!.switchCamera(id)
    }

    fun peerConnectionInit(configuration: HashMap<*, *>, observer: PeerConnection.Observer): PeerConnection? {
        val rtcConfiguration = parseRTCConfiguration(configuration)
        return peerConnectionInit(rtcConfiguration, observer)
    }

    fun peerConnectionInit(
        configuration: PeerConnection.RTCConfiguration,
        observer: PeerConnection.Observer
    ): PeerConnection? {
        return mFactory?.createPeerConnection(configuration, observer)
    }

    fun createIceServer(url: String): PeerConnection.IceServer {
        return PeerConnection.IceServer.builder(url).createIceServer()
    }

    fun createIceServer(url: String, username: String, credential: String): PeerConnection.IceServer {
        return PeerConnection.IceServer.builder(url)
            .setUsername(username)
            .setPassword(credential)
            .createIceServer()
    }

    fun createIceServers(iceServersArray: ArrayList<*>?): List<PeerConnection.IceServer> {
        val size = iceServersArray?.size ?: 0
        val iceServers = ArrayList<PeerConnection.IceServer>(size)
        for (i in 0 until size) {
            val iceServerMap = iceServersArray!![i] as HashMap<*, *>
            val hasUsernameAndCredential =
                iceServerMap.containsKey("username") && iceServerMap.containsKey("credential")
            if (iceServerMap.containsKey("url")) {
                if (hasUsernameAndCredential) {
                    iceServers.add(
                        createIceServer(
                            iceServerMap["url"] as String,
                            iceServerMap["username"] as String,
                            iceServerMap["credential"] as String
                        )
                    )
                } else {
                    iceServers.add(createIceServer(iceServerMap["url"] as String))
                }
            } else if (iceServerMap.containsKey("urls")) {
                if (iceServerMap["urls"] is String) {
                    if (hasUsernameAndCredential) {
                        iceServers.add(
                            createIceServer(
                                iceServerMap["urls"] as String,
                                iceServerMap["username"] as String,
                                iceServerMap["credential"] as String
                            )
                        )
                    } else {
                        iceServers.add(createIceServer(iceServerMap["urls"] as String))
                    }
                } else if (iceServerMap["urls"] is ArrayList<*>) {
                    val urls = iceServerMap["urls"] as ArrayList<*>
                    for (j in urls.indices) {
                        val url = urls[j] as String
                        if (hasUsernameAndCredential) {
                            iceServers.add(
                                createIceServer(
                                    url,
                                    iceServerMap["username"] as String,
                                    iceServerMap["credential"] as String
                                )
                            )
                        } else {
                            iceServers.add(createIceServer(url))
                        }
                    }
                }
            }
        }
        return iceServers
    }

    fun parseRTCConfiguration(map: HashMap<*, *>?): PeerConnection.RTCConfiguration {
        var iceServersArray: ArrayList<*>? = null
        if (map != null) {
            iceServersArray = map["iceServers"] as ArrayList<*>
        }
        val iceServers = createIceServers(iceServersArray)
        val conf = PeerConnection.RTCConfiguration(iceServers)
        if (map == null) {
            return conf
        }

        // iceTransportPolicy (public api)
        if (map.containsKey("iceTransportPolicy")
            && map["iceTransportPolicy"] is String
        ) {
            val v = map["iceTransportPolicy"] as String
            when (v) {
                "all" // public
                -> conf.iceTransportsType = PeerConnection.IceTransportsType.ALL
                "relay" // public
                -> conf.iceTransportsType = PeerConnection.IceTransportsType.RELAY
                "nohost" -> conf.iceTransportsType = PeerConnection.IceTransportsType.NOHOST
                "none" -> conf.iceTransportsType = PeerConnection.IceTransportsType.NONE
            }
        }

        // bundlePolicy (public api)
        if (map.containsKey("bundlePolicy")
            && map["bundlePolicy"] is String
        ) {
            val v = map["bundlePolicy"] as String
            when (v) {
                "balanced" // public
                -> conf.bundlePolicy = PeerConnection.BundlePolicy.BALANCED
                "max-compat" // public
                -> conf.bundlePolicy = PeerConnection.BundlePolicy.MAXCOMPAT
                "max-bundle" // public
                -> conf.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            }
        }

        // rtcpMuxPolicy (public api)
        if (map.containsKey("rtcpMuxPolicy")
            && map["rtcpMuxPolicy"] is String
        ) {
            val v = map["rtcpMuxPolicy"] as String
            when (v) {
                "negotiate" // public
                -> conf.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE
                "require" // public
                -> conf.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            }
        }

        // FIXME: peerIdentity of type DOMString (public api)
        // FIXME: certificates of type sequence<RTCCertificate> (public api)

        // iceCandidatePoolSize of type unsigned short, defaulting to 0
        if (map.containsKey("iceCandidatePoolSize")
            //&& map.getType("iceCandidatePoolSize") == ReadableType.Number
            && map["iceCandidatePoolSize"] is Number
        ) {
            val v = map["iceCandidatePoolSize"] as Int
            if (v > 0) {
                conf.iceCandidatePoolSize = v
            }
        }

        // === below is private api in webrtc ===

        // tcpCandidatePolicy (private api)
        if (map.containsKey("tcpCandidatePolicy")
            && map["tcpCandidatePolicy"] is String
        ) {
            val v = map["tcpCandidatePolicy"] as String
            when (v) {
                "enabled" -> conf.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                "disabled" -> conf.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            }
        }

        // candidateNetworkPolicy (private api)
        if (map.containsKey("candidateNetworkPolicy")
            //&& map.getType("candidateNetworkPolicy") == ReadableType.String
            && map["candidateNetworkPolicy"] is String
        ) {
            val v = map["candidateNetworkPolicy"] as String
            when (v) {
                "all" -> conf.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
                "low_cost" -> conf.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.LOW_COST
            }
        }

        // KeyType (private api)
        if (map.containsKey("keyType")
            && map["keyType"] is String
        ) {
            val v = map["keyType"] as String
            if (v != null) {
                when (v) {
                    "RSA" -> conf.keyType = PeerConnection.KeyType.RSA
                    "ECDSA" -> conf.keyType = PeerConnection.KeyType.ECDSA
                }
            }
        }

        // continualGatheringPolicy (private api)
        if (map.containsKey("continualGatheringPolicy")
            //&& map.getType("continualGatheringPolicy") == ReadableType.String
            && map["continualGatheringPolicy"] is String
        ) {
            val v = map["continualGatheringPolicy"] as String
            when (v) {
                "gather_once" -> conf.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
                "gather_continually" -> conf.continualGatheringPolicy =
                        PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }
        }

        // audioJitterBufferMaxPackets (private api)
        if (map.containsKey("audioJitterBufferMaxPackets")
            && map["audioJitterBufferMaxPackets"] is Number
        ) {
            val v = map["audioJitterBufferMaxPackets"] as Int
            if (v > 0) {
                conf.audioJitterBufferMaxPackets = v
            }
        }

        // iceConnectionReceivingTimeout (private api)
        if (map.containsKey("iceConnectionReceivingTimeout")
            && map["iceConnectionReceivingTimeout"] is Number
        ) {
            val v = map["iceConnectionReceivingTimeout"] as Int
            conf.iceConnectionReceivingTimeout = v
        }

        // iceBackupCandidatePairPingInterval (private api)
        if (map.containsKey("iceBackupCandidatePairPingInterval")
            && map["iceBackupCandidatePairPingInterval"] is Number
        ) {
            val v = map["iceBackupCandidatePairPingInterval"] as Int
            conf.iceBackupCandidatePairPingInterval = v
        }

        // audioJitterBufferFastAccelerate (private api)
        if (map.containsKey("audioJitterBufferFastAccelerate")
            && map["iceBackupCandidatePairPingInterval"] is Boolean
        ) {
            val v = map["audioJitterBufferFastAccelerate"] as Boolean
            conf.audioJitterBufferFastAccelerate = v
        }

        // pruneTurnPorts (private api)
        if (map.containsKey("pruneTurnPorts")
            && map["pruneTurnPorts"] is Boolean
        ) {
            val v = map["pruneTurnPorts"] as Boolean
            conf.pruneTurnPorts = v
        }

        // presumeWritableWhenFullyRelayed (private api)
        if (map.containsKey("presumeWritableWhenFullyRelayed")
            && map["presumeWritableWhenFullyRelayed"] is Boolean
        ) {
            val v = map["presumeWritableWhenFullyRelayed"] as Boolean
            conf.presumeWritableWhenFullyRelayed = v
        }

        return conf
    }

    fun parseMediaConstraints(constraints: HashMap<*, *>): MediaConstraints {
        val mediaConstraints = MediaConstraints()

        if (constraints.containsKey("mandatory")
            && constraints["mandatory"] is HashMap<*, *>
        ) {
            parseConstraints(
                constraints["mandatory"] as HashMap<String, String>,
                mediaConstraints.mandatory
            )
        } else {
            logger.debug("mandatory constraints are not a map")
        }

        if (constraints.containsKey("optional")
            && constraints["optional"] is ArrayList<*>
        ) {
            val optional = constraints["optional"] as ArrayList<*>
            var i = 0
            val size = optional.size
            while (i < size) {
                if (optional[i] is HashMap<*, *>) {
                    parseConstraints(
                        optional[i] as HashMap<String, String>,
                        mediaConstraints.optional
                    )
                }
                i++
            }
        } else {
            logger.debug("optional constraints are not an array")
        }

        return mediaConstraints
    }

    private fun parseConstraints(
        src: HashMap<String, String>,
        dst: MutableList<MediaConstraints.KeyValuePair>
    ) {
        for (key in src.keys) {
            val value = src[key]
            dst.add(MediaConstraints.KeyValuePair(key, value))
        }
    }
}