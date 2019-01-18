package com.versatica.mediasoup

import com.versatica.mediasoup.handlers.Handler
import com.versatica.mediasoup.handlers.RecvHandler
import com.versatica.mediasoup.handlers.SendHandler
import com.versatica.mediasoup.handlers.sdp.*
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack

val DEFAULT_STATS_INTERVAL = 1000

class Transport(
    direction: String,
    extendedRtpCapabilities: RTCExtendedRtpCapabilities,
    settings: RoomOptions,
    appData: Any? = null,
    private var logger: Logger = Logger("Transport")
) : EnhancedEventEmitter(logger) {
    // Id.
    val _id = Utils.randomNumber()

    // Closed flag.
    var _closed = false

    // Direction.
    val _direction = direction

    // Room settings.
    val _settings = settings

    // App custom data.
    val _appData = appData

    // Periodic stats flag.
    var _statsEnabled = false

    // Commands handler.
    val _commandQueue = CommandQueue()

    // Device specific handler.
    var _handler = Handler.getHandle(direction, extendedRtpCapabilities, settings)

    // Transport state. Values can be:
    // "new"/"connecting"/"connected"/"failed"/"disconnected"/"closed"
    // @type {String}
    var _connectionState = "new"

    init {
        this._commandQueue.on("exec") { args ->
            _execCommand(args[0] as CommandQueue.Command, args[1] as CommandQueue.PromiseHolder)
        }

        //SendHandler RecvHandler
        when (direction) {
            "send" -> {
                _handler = _handler as SendHandler
            }
            "recv" -> {
                _handler = _handler as RecvHandler
            }
            else -> Unit
        }
        this._handleHandler()
    }

    /**
     * Transport id.
     *
     * @return {Number}
     */
    fun id(): Int {
        return this._id
    }

    /**
     * Whether the Transport is closed.
     *
     * @return {Boolean}
     */
    fun closed(): Boolean {
        return this._closed
    }

    /**
     * Transport direction.
     *
     * @return {String}
     */
    fun direction(): String {
        return this._direction
    }

    /**
     * App custom data.
     *
     * @return {Any}
     */
    fun appData(): Any? {
        return this._appData
    }

    /**
     * Connection state.
     *
     * @return {String}
     */
    fun connectionState(): String {
        return this._connectionState
    }

    /**
     * Device handler.
     *
     * @return {Handler}
     */
    fun handler(): Handler? {
        return this._handler
    }


    /**
     * Close the Transport.
     *
     * @param {Any} [appData] - App custom data.
     */
    fun close(appData: Any? = null) {
        logger.debug("close()")

        if (this._closed)
            return

        this._closed = true

        if (this._statsEnabled) {
            this._statsEnabled = false
            this.disableStats()
        }

        val closeTransportNotify = CloseTransportNotify()
        closeTransportNotify.id = this._id
        closeTransportNotify.appData = appData

        this.safeEmit("@notify", "closeTransport", closeTransportNotify)

        this.emit("@close")
        this.safeEmit("close", "local", appData!!)

        this._destroy()
    }

    /**
     * My remote Transport was closed.
     * Invoked via remote notification.
     *
     * @private
     *
     * @param {Any} [appData] - App custom data.
     * @param {Boolean} destroy - Whether the local transport must be destroyed.
     */
    fun remoteClose(appData: Any?, destroy: Boolean) {
        logger.debug("remoteClose() [destroy:$destroy]")

        if (this._closed)
            return

        if (!destroy) {
            this._handler?.remoteClosed()
            return
        }

        this._closed = true

        this.emit("@close")
        this.safeEmit("close", "remote", appData!!)

        this._destroy()
    }


    private fun _destroy() {
        // Close the CommandQueue.
        this._commandQueue.close()

        // Close the handler.
        this._handler?.close()
    }

    fun restartIce(): Observable<Any> {
        logger.debug("restartIce()")

        if (this._closed) {
            return Observable.create {
                //next
                it.onNext(Unit)
            }
        } else if (this._connectionState === "new") {
            return Observable.create {
                //next
                it.onNext(Unit)
            }
        }

        return Observable.just(Unit)
            .flatMap {
                val data = RestartTransportRequest()
                data.id = this._id

                Observable.create(ObservableOnSubscribe<Any> {
                    //next
                    this.safeEmitAsPromise(it, "@request", "restartTransport", data).subscribe()
                })
            }.flatMap { response ->
                //only for test
                //val remoteIceParameters = response.iceParameters
                val remoteIceParameters = RTCIceParameters("", "", true)

                this._commandQueue.push("restartIce", remoteIceParameters)
            }
    }

    fun enableStats(interval: Int = DEFAULT_STATS_INTERVAL) {
        logger.debug("enableStats() [interval:$interval]")

        var statsInterval = interval
        if (statsInterval < 1000) {
            statsInterval = DEFAULT_STATS_INTERVAL
        }

        this._statsEnabled = true

        val data = EnableTransportStatsNotify()
        data.id = this._id
        data.interval = statsInterval

        this.safeEmit("@notify", "enableTransportStats", data)
    }

    fun disableStats() {
        logger.debug("disableStats()")

        this._statsEnabled = false

        val data = DisableTransportStatsNotify()
        data.id = this._id

        this.safeEmit("@notify", "disableTransportStats", data)
    }

    /**
     * Send the given Producer over this Transport.
     *
     * @private
     *
     * @param {Producer} producer
     *
     * @return {Promise}
     */
    fun addProducer(producer: Producer): Observable<Any> {
        logger.debug("addProducer() [producer:$producer]")

        if (this._closed) {
            return Observable.create {
                //next
                it.onError(InvalidStateError("Transport closed"))
            }
        } else if (this._connectionState === "new") {
            return Observable.create {
                //next
                it.onError(Throwable("not a sending Transport"))
            }
        }

        // Enqueue command.
        return this._commandQueue.push("addProducer", producer)
    }

    /**
     * @private
     */
    fun removeProducer(
        producer: Producer,
        originator: String,
        appData: Any? = null
    ) {
        logger.debug("removeProducer() [producer:$producer]")

        // Enqueue command.
        if (!this._closed) {
            this._commandQueue.push("removeProducer", producer)
        }

        if (originator === "local") {
            val data = CloseProducerNotify()
            data.id = producer.id
            data.appData = appData

            this.safeEmit("@notify", "closeProducer", data)
        }
    }

    /**
     * @private
     */
    fun pauseProducer(
        producer: Producer,
        appData: Any
    ) {
        logger.debug("pauseProducer() [producer:$producer]")

        val data = PauseProducerNotify()
        data.id =  producer.id
        data.appData = appData

        this.safeEmit("@notify", "pauseProducer", data)
    }

    /**
     * @private
     */
    fun resumeProducer(
        producer: Producer,
        appData: Any
    ) {
        logger.debug("resumeProducer() [producer:$producer]")

        val data = ResumeProducerNotify()
        data.id =  producer.id
        data.appData = appData

        this.safeEmit("@notify", "resumeProducer", data)
    }

    /**
     * @private
     *
     * @return {Promise}
     */
    fun replaceProducerTrack(
        producer: Producer,
        track: MediaStreamTrack
    ): Observable<Any> {
        logger.debug("replaceProducerTrack() [producer:$producer]")

        return this._commandQueue.push(
            "replaceProducerTrack", ReplaceProducerTrackInfo(producer, track)
        )
    }

    /**
     * @private
     */
    fun enableProducerStats(
        producer: Producer,
        interval: Int
    ) {
        logger.debug("enableProducerStats() [producer:$producer]")

        val data = EnableProducerStatsNotify()
        data.id = producer.id
        data.interval = interval

        this.safeEmit("@notify", "enableProducerStats", data)
    }

    /**
     * @private
     */
    fun disableProducerStats(producer: Producer) {
        logger.debug("disableProducerStats() [producer:$producer]")

        val data = DisableProducerStatsNotify()
        data.id = producer.id

        this.safeEmit("@notify", "disableProducerStats", data)
    }

    /**
     * Receive the given Consumer over this Transport.
     *
     * @private
     *
     * @param {Consumer} consumer
     *
     * @return {Promise} Resolves to a remote MediaStreamTrack.
     */
    fun addConsumer(consumer: Consumer): Observable<Any> {
        logger.debug("addConsumer() [consumer:$consumer]")

        if (this._closed) {
            return Observable.create {
                //next
                it.onError(InvalidStateError("Transport closed"))
            }
        } else if (this._connectionState === "new") {
            return Observable.create {
                //next
                it.onError(Throwable("not a receiving Transport"))
            }
        }

        // Enqueue command.
        return this._commandQueue.push("addConsumer", consumer)
    }

    /**
     * @private
     */
    fun removeConsumer(consumer: Consumer) {
        logger.debug("removeConsumer () [consumer:$consumer]")

        // Enqueue command.
        this._commandQueue.push("removeConsumer", consumer)
    }

    /**
     * @private
     */
    fun pauseConsumer(
        consumer: Consumer,
        appData: Any
    ) {
        logger.debug("pauseConsumer () [consumer:$consumer]")

        val data = PauseConsumerNotify()
        data.id = consumer.id
        data.appData = appData

        this.safeEmit("@notify", "pauseConsumer", data)
    }

    /**
     * @private
     */
    fun resumeConsumer(
        consumer: Consumer,
        appData: Any
    ) {
        logger.debug("resumeConsumer () [consumer:$consumer]")

        val data = ResumeConsumerNotify()
        data.id = consumer.id
        data.appData = appData

        this.safeEmit("@notify", "resumeConsumer", data)
    }

    /**
     * @private
     */
    fun setConsumerPreferredProfile(
        consumer: Consumer,
        profile: String
    ) {
        logger.debug("setConsumerPreferredProfile () [consumer:$consumer]")

        val data = SetConsumerPreferredProfileNotify()
        data.id = consumer.id
        data.profile = profile

        this.safeEmit("@notify", "setConsumerPreferredProfile", data)
    }

    /**
     * @private
     */
    fun enableConsumerStats(
        consumer: Consumer,
        interval: Int
    ) {
        logger.debug("enableConsumerStats () [consumer:$consumer]")

        val data = EnableConsumerStatsNotify()
        data.id = consumer.id
        data.interval = interval

        this.safeEmit("@notify", "enableConsumerStats", data)
    }

    /**
     * @private
     */
    fun disableConsumerStats(consumer: Consumer) {
        logger.debug("disableConsumerStats () [consumer:$consumer]")

        val data = DisableConsumerStatsNotify()
        data.id = consumer.id

        this.safeEmit("@notify", "disableConsumerStats", data)
    }

    /**
     * Receive remote stats.
     *
     * @private
     *
     * @param {Object} stats
     */
    fun remoteStats(stats: ArrayList<RTCTransportStats>) {
        this.safeEmit("stats", stats)
    }

    private fun _handleHandler() {
        _handler?.on("@connectionstatechange") {
            val state = it[0] as String
            if (this._connectionState != state) {
                logger.debug("Transport connection state changed to $state")

                this._connectionState = state

                if (!this._closed)
                    this.safeEmit("connectionstatechange", state)
            }
        }

        _handler?.on("@needcreatetransport") {
            val transportLocalParameters = it[0] as TransportRemoteIceParameters
            val callback = it[1] as Function1<Any, Unit>
            val errback = it[2] as Function1<Any, Unit>

            val data = CreateTransportRequest()
            data.id = this._id
            data.direction = this._direction
            data.options = this._settings.transportOptions
            data.appData = this._appData

            if (transportLocalParameters.dtlsParameters != null)
                data.dtlsParameters = transportLocalParameters.dtlsParameters

            this.safeEmit("@request", "createTransport", data, callback, errback)
        }

        _handler?.on("@needupdatetransport") {
            val transportLocalParameters = it[0] as TransportRemoteIceParameters

            val data = UpdateTransportNotify()

            if (transportLocalParameters.dtlsParameters != null)
                data.dtlsParameters = transportLocalParameters.dtlsParameters

            this.safeEmit("@notify", "updateTransport", data)
        }
    }

    private fun _execCommand(command: CommandQueue.Command, promiseHolder: CommandQueue.PromiseHolder) {
        var promise: Observable<Any>?
        try {
            when (command.method) {
                "addProducer" -> {
                    val producer = command.data as Producer
                    promise = this._execAddProducer(producer)
                }
                "removeProducer" -> {
                    val producer = command.data as Producer
                    promise = this._execRemoveProducer(producer)
                }
                "replaceProducerTrack" -> {
                    val data = command.data as ReplaceProducerTrackInfo
                    promise = this._execReplaceProducerTrack(data.producer, data.track)
                }
                "addConsumer" -> {
                    val consumer = command.data as Consumer
                    promise = this._execAddConsumer(consumer)
                }
                "removeConsumer" -> {
                    val consumer = command.data as Consumer
                    promise = this._execRemoveConsumer(consumer)
                }
                "restartIce" -> {
                    val remoteIceParameters = command.data as RTCIceParameters
                    promise = this._execRestartIce(remoteIceParameters)
                }
                else -> {
                    promise = Observable.create {
                        it.onError(Throwable("unknown command method $command.method"))
                    }
                }
            }
        } catch (e: Exception) {
            promise = Observable.create {
                it.onError(Throwable(e.message))
            }
        }

        // Fill the given Promise holder.
        promiseHolder.promise = promise
    }

    private fun _execAddProducer(producer: Producer): Observable<Any>? {
        logger.debug("_execAddProducer()")

        var producerRtpParameters: RTCRtpParameters

        // Call the handler.
        return Observable.just(Unit)
            .flatMap {
                (_handler as SendHandler).addProducer(producer)
            }.flatMap {
                producerRtpParameters = it

                val data = CreateProducerRequest()
                data.id = producer.id
                data.kind = producer.kind()
                data.transportId = this._id
                data.rtpParameters = producerRtpParameters
                data.paused = producer.locallyPaused
                data.appData = producer.appData

                Observable.create(ObservableOnSubscribe<Any> {
                    //next
                    this.safeEmitAsPromise(it, "@request", "createProducer", data).subscribe()
                })
            }.flatMap {
                //producer.setRtpParameters(producerRtpParameters)
                Observable.create(ObservableOnSubscribe<Unit> {
                    it.onNext(Unit)
                })
            }
    }

    private fun _execRemoveProducer(producer: Producer): Observable<Any>? {
        logger.debug("_execRemoveProducer()")

        if (_handler is SendHandler) {
            // Call the handler.
            return (_handler as SendHandler).removeProducer(producer)
        } else {
            return Observable.create {
                it.onNext(Unit)
            }
        }
    }

    private fun _execReplaceProducerTrack(producer: Producer, track: MediaStreamTrack): Observable<Any>? {
        logger.debug("_execReplaceProducerTrack()")

        if (_handler is SendHandler) {
            // Call the handler.
            return (_handler as SendHandler).replaceProducerTrack(producer,track)
        } else {
            return Observable.create {
                it.onNext(Unit)
            }
        }
    }

    private fun _execAddConsumer(consumer: Consumer): Observable<Any>? {
        logger.debug("_execAddConsumer()")

        var consumerTrack: MediaStreamTrack? = null

        // Call the handler.
        return Observable.just(Unit)
            .flatMap {
                (_handler as RecvHandler).addConsumer(consumer)
            }.flatMap { track->
                consumerTrack = track

                val data = EnableConsumerRequest()
                data.id = consumer.id
                data.transportId = this._id
                data.paused = consumer.locallyPaused
                data.preferredProfile = consumer._preferredProfile

                Observable.create(ObservableOnSubscribe<Any> {
                    //next
                    this.safeEmitAsPromise(it, "@request", "enableConsumer", data).subscribe()
                })
            }.flatMap { response ->
                val enableConsumerResponse = response as EnableConsumerResponse

                if (enableConsumerResponse.paused){
                    consumer.remotePause()
                }

                if (enableConsumerResponse.preferredProfile.isNotEmpty()){
                    consumer.remoteSetPreferredProfile(enableConsumerResponse.preferredProfile)
                }

                if (enableConsumerResponse.effectiveProfile.isNotEmpty()){
                    consumer.remoteEffectiveProfileChanged(enableConsumerResponse.effectiveProfile)
                }

                Observable.create(ObservableOnSubscribe<Any> {
                    it.onNext(consumerTrack!!)
                })
            }
    }

    private fun _execRemoveConsumer(consumer: Consumer): Observable<Any>? {
        logger.debug("_execRemoveConsumer()")

        if (_handler is RecvHandler) {
            // Call the handler.
            return (_handler as RecvHandler).removeConsumer(consumer)
        } else {
            return Observable.create {
                it.onNext(Unit)
            }
        }
    }

    private fun _execRestartIce(remoteIceParameters: RTCIceParameters): Observable<Any>? {
        logger.debug("_execRestartIce()")

        // Call the handler.
        if (_handler is SendHandler) {
            // Call the handler.
            return (_handler as SendHandler).restartIce(remoteIceParameters)
        } else if (_handler is RecvHandler) {
            // Call the handler.
            return (_handler as RecvHandler).restartIce(remoteIceParameters)
        } else {
            return Observable.create {
                it.onNext(Unit)
            }
        }
    }

}

class UpdateTransportNotify {
    val method: String = "updateTransport"
    val target: String = "peer"
    var id: Int? = 0
    var dtlsParameters: RTCDtlsParameters? = null
}

data class ReplaceProducerTrackInfo(
    var producer: Producer,
    var track: MediaStreamTrack
)