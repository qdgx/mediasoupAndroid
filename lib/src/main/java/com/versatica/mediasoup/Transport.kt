package com.versatica.mediasoup

import com.versatica.mediasoup.handlers.Handler
import com.versatica.mediasoup.handlers.sdp.*
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import org.webrtc.MediaStreamTrack

val DEFAULT_STATS_INTERVAL = 1000
val logger = Logger("Transport")

class Transport(
    direction: String,
    extendedRtpCapabilities: RTCExtendedRtpCapabilities,
    settings: RoomOptions,
    appData: Any? = null
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
    val _handler = Handler.getHandle(direction, extendedRtpCapabilities, settings)

    // Transport state. Values can be:
    // "new"/"connecting"/"connected"/"failed"/"disconnected"/"closed"
    // @type {String}
    var _connectionState = "new"

    init {
        this._commandQueue.on("exec") { args ->
            _execCommand(args[0] as CommandQueue.Command, args[1] as CommandQueue.PromiseHolder)
        }

        this._handleHandler()
    }

    /**
     * Close the Transport.
     *
     * @param {Any} [appData] - App custom data.
     */
    fun close(appData: Any?){
        logger.debug("close()")

        if (this._closed)
            return

        this._closed = true

        if (this._statsEnabled)
        {
            this._statsEnabled = false
            this.disableStats()
        }

        var closeTransportNotify = CloseTransportNotify()
        closeTransportNotify.id = this._id
        closeTransportNotify.appData = appData

        this.safeEmit("@notify", "closeTransport",closeTransportNotify)

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
    fun remoteClose(appData: Any?, destroy: Boolean)
    {
        logger.debug("remoteClose() [destroy:$destroy]")

        if (this._closed)
            return

        if (!destroy)
        {
            this._handler?.remoteClosed()
            return
        }

        this._closed = true

        this.emit("@close")
        this.safeEmit("close", "remote", appData!!)

        this._destroy()
    }


    private fun _destroy(){
        // Close the CommandQueue.
        this._commandQueue.close()

        // Close the handler.
        this._handler?.close()
    }

    fun restartIce(): Observable<Any> {
        logger.debug("restartIce()")

        if (this._closed){
            return Observable.create{
                //next
                it.onNext(Unit)
            }
        } else if (this._connectionState === "new"){
            return  Observable.create{
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
                    this.safeEmitAsPromise(it,"@request","restartTransport",data).subscribe()
                })
            }.flatMap { response ->
                //only for test
                //val remoteIceParameters = response.iceParameters
                val remoteIceParameters = RTCIceParameters("","",true)

                this._commandQueue.push("restartIce",remoteIceParameters)
            }
    }

    fun enableStats(interval: Int = DEFAULT_STATS_INTERVAL){
        logger.debug("enableStats() [interval:$interval]")

        var statsInterval = interval
        if (statsInterval < 1000){
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

        var data = DisableTransportStatsNotify()
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
    fun addProducer(producer:Any): Observable<Any> {
        logger.debug("addProducer() [producer:$producer]")

        if (this._closed){
            return Observable.create{
                //next
                it.onError(InvalidStateError("Transport closed"))
            }
        } else if (this._connectionState === "new"){
            return  Observable.create{
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
    fun removeProducer(producer: Any,
                       originator: String,
                       appData: Any) {
        logger.debug("removeProducer() [producer:$producer]")

        // Enqueue command.
        if (!this._closed)
        {
            this._commandQueue.push("removeProducer", producer)
        }

        if (originator === "local"){
            val data = CloseProducerNotify()
            //data.id = producer.id
            data.appData = appData

            this.safeEmit("@notify", "closeProducer", data)
        }
    }

    /**
     * @private
     */
    fun pauseProducer(producer: Any,
                      appData: Any) {
        logger.debug("pauseProducer() [producer:$producer]")

        val data = PauseProducerNotify()
        //data.id =  producer.id
        data.appData = appData

        this.safeEmit("@notify", "pauseProducer", data)
    }

    /**
     * @private
     */
    fun resumeProducer(producer: Any,
                       appData: Any) {
        logger.debug("resumeProducer() [producer:$producer]")

        val data = ResumeProducerNotify()
        //data.id =  producer.id
        data.appData = appData

        this.safeEmit("@notify", "resumeProducer", data)
    }

    /**
     * @private
     *
     * @return {Promise}
     */
    fun replaceProducerTrack(producer: Any,
                             track: MediaStreamTrack):Observable<Any> {
        logger.debug("replaceProducerTrack() [producer:$producer]")

        return this._commandQueue.push(
            "replaceProducerTrack", ReplaceProducerTrackInfo(producer,track))
    }

    /**
     * @private
     */
    fun enableProducerStats(producer: Any,
                            interval: Int) {
        logger.debug("enableProducerStats() [producer:$producer]")

        val data = EnableProducerStatsNotify()
        //data.id = producer.id
        data.interval = interval

        this.safeEmit("@notify", "enableProducerStats", data)
    }

    /**
     * @private
     */
    fun disableProducerStats(producer: Any) {
        logger.debug("disableProducerStats() [producer:$producer]")

        val data = DisableProducerStatsNotify()
        //data.id = producer.id

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
    fun addConsumer(consumer: Any): Observable<Any> {
        logger.debug("addConsumer() [consumer:$consumer]")

        if (this._closed){
            return Observable.create{
                //next
                it.onError(InvalidStateError("Transport closed"))
            }
        } else if (this._connectionState === "new"){
            return  Observable.create{
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
    fun removeConsumer(consumer: Any){
        logger.debug("removeConsumer () [consumer:$consumer]")

        // Enqueue command.
        this._commandQueue.push("removeConsumer", consumer)
    }

    /**
     * @private
     */
    fun pauseConsumer(consumer: Any,
                      appData: Any){
        logger.debug("pauseConsumer () [consumer:$consumer]")

        val data = PauseConsumerNotify()
        //data.id = consumer.id
        data.appData = appData

        this.safeEmit("@notify", "pauseConsumer", data)
    }

    /**
     * @private
     */
    fun resumeConsumer(consumer: Any,
                        appData: Any){
        logger.debug("resumeConsumer () [consumer:$consumer]")

        val data = ResumeConsumerNotify()
        //data.id = consumer.id
        data.appData = appData

        this.safeEmit("@notify", "resumeConsumer", data)
    }

    /**
     * @private
     */
    fun setConsumerPreferredProfile(consumer: Any,
                                    profile: String){
        logger.debug("setConsumerPreferredProfile () [consumer:$consumer]")

        val data = SetConsumerPreferredProfileNotify()
        //data.id = consumer.id
        data.profile = profile

        this.safeEmit("@notify", "setConsumerPreferredProfile", data)
    }

    /**
     * @private
     */
    fun enableConsumerStats(consumer: Any,
                            interval: Int){
        logger.debug("enableConsumerStats () [consumer:$consumer]")

        val data = EnableConsumerStatsNotify()
        //data.id = consumer.id
        data.interval = interval

        this.safeEmit("@notify", "enableConsumerStats", data)
    }

    /**
     * @private
     */
    fun disableConsumerStats(consumer: Any) {
        logger.debug("disableConsumerStats () [consumer:$consumer]")

        val data = DisableConsumerStatsNotify()
        //data.id = consumer.id

        this.safeEmit("@notify", "disableConsumerStats", data)
    }

    /**
     * Receive remote stats.
     *
     * @private
     *
     * @param {Object} stats
     */
    fun remoteStats(stats: RTCStats) {
        this.safeEmit("stats", stats)
    }

    private fun _handleHandler() {
        _handler?.on("@connectionstatechange") {
            val state = it[0] as String
            if (this._connectionState != state){
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
            data .options = this._settings.transportOptions
            data.appData = this._appData

            if (transportLocalParameters?.dtlsParameters != null)
                data.dtlsParameters = transportLocalParameters.dtlsParameters

            this.safeEmit("@request", "createTransport", data, callback, errback)
        }

        _handler?.on("@needupdatetransport") {
            val transportLocalParameters = it[0] as TransportRemoteIceParameters

            val data = UpdateTransportNotify()

            if (transportLocalParameters?.dtlsParameters != null)
                data.dtlsParameters = transportLocalParameters.dtlsParameters

            this.safeEmit("@notify", "updateTransport", data)
        }
    }

    private fun _execCommand(command: CommandQueue.Command, promiseHolder: CommandQueue.PromiseHolder) {
        var promise: Observable<Any>? = null
        try {
            when (command.method) {
                "addProducer" -> {
                    var data = command.data
                    promise = this._execAddProducer(data)
                }
                "removeProducer" -> {

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

    private fun _execAddProducer(data: Any): Observable<Any>? {
        logger.debug("_execAddProducer()")
        // Call the handler.
        return Observable.just(data as String)
            .flatMap { str: String ->
                Observable.create(ObservableOnSubscribe<String> {
                    //next
                    it.onNext("_execAddProducer step1 $str")
                })
            }.flatMap { str: String ->
                Observable.create(ObservableOnSubscribe<Any> {
                    //next
                    this.safeEmitAsPromise(it, "@request", "createProducer", "_execAddProducer step2 $str").subscribe()
                })
            }.flatMap { data: Any ->
                Observable.create(ObservableOnSubscribe<Any> {
                    //next
                    it.onNext(data)
                })
            }
    }
}

class RestartTransportRequest {
    var id: Int? = 0
}

class EnableTransportStatsNotify {
    var id: Int? = 0
    var interval: Int? = 0
}

class DisableTransportStatsNotify {
    var id: Int? = 0
    var interval: Int? = 0
}

class CreateTransportRequest {
    var id: Int? = 0
    var direction: String? = null
    var options: TransportOptions? = null
    var appData: Any? = null
    var dtlsParameters: RTCDtlsParameters? = null
}

class UpdateTransportNotify {
    var id: Int? = 0
    var dtlsParameters: RTCDtlsParameters? = null
}

class updateProducerNotify {
    var id: Int? = 0
    var rtpParameters: RTCRtpParameters? = null
}

class PauseProducerNotify {
    var id: Int? = 0
    var appData: Any? = null
}

class EnableProducerStatsNotify {
    var id: Int? = 0
    var interval: Int? = 0
}

class ResumeProducerNotify {
    var id: Int? = 0
    var appData: Any? = null
}

class DisableProducerStatsNotify {
    var id: Int? = 0
}

class PauseConsumerNotify {
    var id: Int? = 0
    var appData: Any? = null
}

class ResumeConsumerNotify {
    var id: Int? = 0
    var appData: Any? = null
}

class SetConsumerPreferredProfileNotify {
    var id: Int? = 0
    var profile: String? = null
}

class EnableConsumerStatsNotify {
    var id: Int? = 0
    var interval: Int? = 0
}

class DisableConsumerStatsNotify {
    var id: Int? = 0
}

class CreateProducerRequest {
    var id: Int? = 0
    var kind: String? = null
    var transportId: Int? = null
    var rtpParameters: RTCRtpParameters? = null
    var paused: Boolean = false
    var appData: Any? = null
}

class EnableConsumerRequest {
    var id: Int? = 0
    var transportId: Int? = null
    var paused: Boolean = false
    var preferredProfile: String? = null
}

class CloseTransportNotify {
    var id: Int? = 0
    var appData: Any? = null
}

class CloseProducerNotify {
    var id: Int? = 0
    var appData: Any? = null
}

class ReplaceProducerTrackInfo(
    producer: Any,
    track: MediaStreamTrack
)