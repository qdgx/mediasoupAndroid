package com.versatica.mediasoup

import com.dingsoft.sdptransform.MediaAttributes
import com.versatica.mediasoup.handlers.sdp.RTCRtpParameters
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import org.webrtc.MediaStreamTrack

/**
 * @author wolfhan
 */

class Producer(var track: MediaStreamTrack, var options: MediaAttributes, var appData: Any) :
    EnhancedEventEmitter(logger = Logger("Producer")) {

    val DEFAULT_STATS_INTERVAL = 1000
    val SIMULCAST_DEFAULT = mapOf(
        "low" to 100000,
        "medium" to 300000,
        "high" to 1500000
    )

    // Id.
    // @type {Number}
    val id: Int by lazy {
        Utils.randomNumber()
    }

    // Closed flag.
    // @type {Boolean}
    var closed = false

    // Original track.
    // @type {MediaStreamTrack}
    var originalTrack: MediaStreamTrack = track

    // Simulcast.
    // @type {Object|false}
    private var simulcast: Any = false

    // Associated Transport.
    // @type {Transport}
    var transport: Transport? = null

    // RTP parameters.
    // @type {RTCRtpParameters}
    var rtpParameters: RTCRtpParameters? = null

    // Locally paused flag.
    // @type {Boolean}
    var locallyPaused = !this.track.enabled()

    // Remotely paused flag.
    // @type {Boolean}
    var remotelyPaused = false

    // Periodic stats flag.
    // @type {Boolean}
    var statsEnabled = false

    // Periodic stats gathering interval (milliseconds).
    // @type {Number}
    var statsInterval = DEFAULT_STATS_INTERVAL

    init {
//            if (typeof options.simulcast === 'object')
//    this._simulcast = Object.assign({}, SIMULCAST_DEFAULT, options.simulcast)
//    else if (options.simulcast === true)
//    this._simulcast = Object.assign({}, SIMULCAST_DEFAULT)

        // Handle the effective track.
        this.handleTrack()
    }

    /**
     * Media kind.
     *
     * @return {String}
     */
    fun kind() = this.track.kind()

    /**
     * Whether the Producer is paused.
     *
     * @return {Boolean}
     */
    fun paused() = this.locallyPaused || this.remotelyPaused

    /**
     * Closes the Producer.
     *
     * @param {Any} [appData] - App custom data.
     */
    fun close(appData: Any) {
        logger.debug("close()")

        if (this.closed)
            return

        this.closed = true

        if (this.statsEnabled) {
            this.statsEnabled = false

            if (this.transport != null) {
                this.transport?.disableProducerStats(this)
            }
        }

        if (this.transport != null)
            this.transport?.removeProducer(this, "local", appData)

        this.destroy()

        this.emit("@close", "local", appData)
        this.safeEmit("close", "local", appData)
    }

    /**
     * My remote Producer was closed.
     * Invoked via remote notification.
     *
     * @private
     *
     * @param {Any} [appData] - App custom data.
     */
    fun remoteClose(appData: Any) {
        logger.debug("remoteClose()")

        if (this.closed)
            return

        this.closed = true

        if (this.transport != null)
            this.transport?.removeProducer(this, "remote", appData)

        this.destroy()

        this.emit("@close", "remote", appData)
        this.safeEmit("close", "remote", appData)
    }

    fun destroy() {
//        this.transport = false
        this.rtpParameters = null

        try {
//            this.track.stop()
            this.track.dispose()
        } catch (error: Exception) {
        }
    }

    /**
     * Sends RTP.
     *
     * @param {transport} Transport instance.
     *
     * @return {Promise}
     */
    fun send(transport: Transport): Observable<Any> {
        logger.debug("send() [transport:$transport]")

        if (this.closed)
            return Observable.create {
                it.onError(InvalidStateError("Producer closed"))
            }
        else if (this.transport != null)
            return Observable.create {
                it.onError(Exception("already handled by a Transport"))
            }

        this.transport = transport

        return transport.addProducer(this)
            .flatMap {
                transport.once("@close") {
                    if (this.closed || this.transport !== transport)
                        return@once

                    this.transport?.removeProducer(this, "local")

                    this.transport = null
                    this.rtpParameters = null

                    this.safeEmit("unhandled")
                }

                this.safeEmit("handled")

                if (this.statsEnabled)
                    transport.enableProducerStats(this, this.statsInterval)

                Observable.create(ObservableOnSubscribe<Any> {
                    it.onNext(Unit)
                })
            }
    }

    /**
     * Pauses sending media.
     *
     * @param {Any} [appData] - App custom data.
     *
     * @return {Boolean} true if paused.
     */
    fun pause(appData: Any): Boolean {
        logger.debug("pause()")

        if (this.closed) {
            logger.error("pause() | Producer closed")

            return false
        } else if (this.locallyPaused) {
            return true
        }

        this.locallyPaused = true
        this.track.setEnabled(false)

        if (this.transport != null)
            this.transport?.pauseProducer(this, appData)

        this.safeEmit("pause", "local", appData)

        // Return true if really paused.
        return this.paused()
    }

    /**
     * My remote Producer was paused.
     * Invoked via remote notification.
     *
     * @private
     *
     * @param {Any} [appData] - App custom data.
     */
    fun remotePause(appData: Any) {
        logger.debug("remotePause()")

        if (this.closed || this.remotelyPaused)
            return

        this.remotelyPaused = true
        this.track.setEnabled(false)

        this.safeEmit("pause", "remote", appData)
    }

    /**
     * Resumes sending media.
     *
     * @param {Any} [appData] - App custom data.
     *
     * @return {Boolean} true if not paused.
     */
    fun resume(appData: Any): Boolean {
        logger.debug("resume()")

        if (this.closed) {
            logger.error("resume() | Producer closed")

            return false
        } else if (!this.locallyPaused) {
            return true
        }

        this.locallyPaused = false

        if (!this.remotelyPaused)
            this.track.setEnabled(true)

        if (this.transport != null)
            this.transport?.resumeProducer(this, appData)

        this.safeEmit("resume", "local", appData)

        // Return true if not paused.
        return !this.paused()
    }

    /**
     * My remote Producer was resumed.
     * Invoked via remote notification.
     *
     * @private
     *
     * @param {Any} [appData] - App custom data.
     */
    fun remoteResume(appData: Any) {
        logger.debug("remoteResume()")

        if (this.closed || !this.remotelyPaused)
            return

        this.remotelyPaused = false

        if (!this.locallyPaused)
            this.track.setEnabled(true)

        this.safeEmit("resume", "remote", appData)
    }

    /**
     * Replaces the current track with a new one.
     *
     * @param {MediaStreamTrack} track - New track.
     *
     * @return {Promise} Resolves with the new track itself.
     */
    fun replaceTrack(track: MediaStreamTrack): Observable<Any> {
        logger.debug("replaceTrack() [track:$track]")

        if (this.closed)
            return Observable.create {
                it.onError(InvalidStateError("Producer closed"))
            }
//        else if (track.readyState === "ended")
        else if (track.state() === MediaStreamTrack.State.ENDED)
            return Observable.create {
                it.onError(Exception("track.readyState is \"ended\""))
            }

        val clonedTrack = track

        return Observable.just(Unit)
            .flatMap {
                if (this.transport != null)
                    this.transport?.replaceProducerTrack(this, clonedTrack)
                else
                    Observable.create {
                        it.onNext(Unit)
                    }
            }
            .flatMap {
                // Stop the previous track.
                try {
//                    this.track.onended = null
//                    this.track.stop()
                    this.track.dispose()
                } catch (error: Exception) {
                }

                // If this Producer was locally paused/resumed and the state of the new
                // track does not match, fix it.
                if (!this.paused())
                    clonedTrack.setEnabled(true)
                else
                    clonedTrack.setEnabled(false)

                // Set the new tracks.
                this.originalTrack = track
                this.track = clonedTrack

                // Handle the effective track.
                this.handleTrack()

                // Return the new track.
                Observable.create(ObservableOnSubscribe<MediaStreamTrack> {
                    it.onNext(track)
                })
            }
    }

    /**
     * Enables periodic stats retrieval.
     */
    fun enableStats(interval: Int = DEFAULT_STATS_INTERVAL) {
        logger.debug("enableStats() [interval:$interval]")

        if (this.closed) {
            logger.error("enableStats() | Producer closed")
            return
        }

        if (this.statsEnabled)
            return

        if (interval < 1000)
            this.statsInterval = DEFAULT_STATS_INTERVAL
        else
            this.statsInterval = interval

        this.statsEnabled = true

        if (this.transport != null)
            this.transport?.enableProducerStats(this, this.statsInterval)
    }

    /**
     * Disables periodic stats retrieval.
     */
    fun disableStats() {
        logger.debug("disableStats()")

        if (this.closed) {
            logger.error("disableStats() | Producer closed")
            return
        }

        if (!this.statsEnabled)
            return

        this.statsEnabled = false

        if (this.transport != null)
            this.transport?.disableProducerStats(this)
    }

    /**
     * Receive remote stats.
     *
     * @private
     *
     * @param {Object} stats
     */
    fun remoteStats(stats: Any) {
        this.safeEmit("stats", stats)
    }

    /**
     * @private
     */
    fun handleTrack() {
        // If the cloned track is closed (for example if the desktop sharing is closed
        // via chrome UI) notify the app and let it decide wheter to close the Producer
        // or not.
//        this.track.onended = () =>
//        {
//            if (this.closed)
//                return
//
//            logger.warn("track \"ended\" event")
//
//            this.safeEmit("trackended")
//        }
    }
}
