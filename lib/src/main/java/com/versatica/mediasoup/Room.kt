package com.versatica.mediasoup

import com.versatica.mediasoup.handlers.sdp.RTCExtendedRtpCapabilities
import com.versatica.mediasoup.handlers.sdp.RTCRtpCapabilities
import com.versatica.mediasoup.handlers.sdp.canReceive

val logger = Logger("Transport")

/**
 * Room class.
 *
 * @param {Object} [options]
 * @param {Object} [options.roomSettings] Remote room settings, including its RTP
 * capabilities, mandatory codecs, etc. If given, no 'queryRoom' request is sent
 * to the server to discover them.
 * @param {Number} [options.requestTimeout=10000] - Timeout for sent requests
 * (in milliseconds). Defaults to 10000 (10 seconds).
 * @param {Object} [options.transportOptions] - Options for Transport created in mediasoup.
 * @param {Array<RTCIceServer>} [options.turnServers] - Array of TURN servers.
 * @param {RTCIceTransportPolicy} [options.iceTransportPolicy] - ICE transport policy.
 * @param {Boolean} [options.spy] - Whether this is a spy peer.
 *
 * @throws {Error} if device is not supported.
 *
 * @emits {request: Object, callback: Function, errback: Function} request
 * @emits {notification: Object} notify
 * @emits {peer: Peer} newpeer
 * @emits {originator: String, [appData]: Any} close
 */

class Room(
    options: RoomOptions,
    private var logger: Logger = Logger("Transport")
): EnhancedEventEmitter(logger){
    // Computed settings.
    private lateinit var _settings: RoomOptions

    // Room state
    private var _state = RoomState.NEW

    //My mediasoup Peer name.
    private var _peerName: String = String()

    // Map of Transports indexed by id.
    // @type {map<Number, Transport>}
    private val _transports: HashMap<String, Transport> = HashMap()

    // Map of Producers indexed by id.
    // @type {map<Number, Producer>}
    private val _producers: HashMap<String, Any> = HashMap()

    // Map of Peers indexed by name.
    // @type {map<String, Peer>}
    private val _peers: HashMap<String, Peer> = HashMap()

    // Extended RTP capabilities.
    // @type {Object}
    private var _extendedRtpCapabilities: RTCExtendedRtpCapabilities = RTCExtendedRtpCapabilities()

    // Whether we can send audio/video based on computed extended RTP
    // capabilities.
    // @type {Object}
    private val _canSendByKind = CanSendByKind()

    init {
        logger.debug("constructor() [options:$options]")

        // Computed settings.
        _settings = RoomOptions()
    }

    /**
     * Whether the Room is joined.
     *
     * @return {Boolean}
     */
    fun joined(): Boolean{
        return this._state === RoomState.JOINED
    }

    /**
     * Whether the Room is closed.
     *
     * @return {Boolean}
     */
    fun closed(): Boolean{
        return this._state === RoomState.CLOSED
    }

    /**
     * My mediasoup Peer name.
     *
     * @return {String}
     */
    fun peerName(): String{
        return this._peerName
    }

    /**
     * The list of Transports.
     *
     * @return {Array<Transport>}
     */
    fun transports(): ArrayList<Transport>{
        return ArrayList(_transports.values)
    }

    /**
     * The list of Producers.
     *
     * @return {Array<Producer>}
     */
    fun producers(): ArrayList<Any>{
        return ArrayList(_producers.values)
    }

    /**
     * The list of Peers.
     *
     * @return {Array<Peer>}
     */
    fun peers(): ArrayList<Peer>{
        return ArrayList(_peers.values)
    }

    /**
     * fun  the Transport with the given id.
     *
     * @param {Number} id
     *
     * @return {Transport}
     */
    fun getTransportById(id: String): Transport?{
        return this._transports[id]
    }

    /**
     * fun  the Producer with the given id.
     *
     * @param {Number} id
     *
     * @return {Producer}
     */
    fun getProducerById(id: String): Any?{
        return this._producers[id]
    }

    /**
     * fun  the Peer with the given name.
     *
     * @param {String} name
     *
     * @return {Peer}
     */
    fun getPeerByName(id: String): Peer?{
        return this._peers[id]
    }

    fun _handleConsumerData(consumerData: ConsumerData,
                            peer: Peer) {
        //val consumer = Consumer(id, kind, rtpParameters, peer, appData)
        val consumer = Consumer()
        val supported = canReceive(consumerData.rtpParameters, this._extendedRtpCapabilities)

//        if (supported)
//            consumer.setSupported(true)
//
//        if (consumerData.paused)
//            consumer.remotePause()

        peer.addConsumer(consumer)
    }
}

enum class RoomState(val v: String) {
    NEW("new"),
    JOINING("joining"),
    JOINED("joined"),
    CLOSED("closed")
}

class CanSendByKind{
    var audio: Boolean = false
    var video: Boolean = false
}

