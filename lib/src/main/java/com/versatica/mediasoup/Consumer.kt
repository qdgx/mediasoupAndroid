package com.versatica.mediasoup

import com.versatica.mediasoup.handlers.TransceiversMediaTrack
import com.versatica.mediasoup.handlers.sdp.RTCRtpParameters
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import org.webrtc.MediaStreamTrack

/**
 * @author wolfhan
 */

class Consumer(
    var id: Int, var kind: String, var rtpParameters: RTCRtpParameters, var peer: Peer, var appData: Any?,
    private var logger: Logger = Logger("Consumer")
) : EnhancedEventEmitter(logger) {

    val PROFILES = setOf("default", "low", "medium", "high")
    val DEFAULT_STATS_INTERVAL = 1000

    // Closed flag.
    // @type {Boolean}
    var closed = false

    // Whether we can receive this Consumer (based on our RTP capabilities).
    // @type {Boolean}
    var supported = false

    // Associated Transport.
    // @type {Transport}
    var transport: Transport? = null

    // Remote track.
    // @type {MediaStreamTrack}
    var track: MediaStreamTrack? = null

    // Locally paused flag.
    // @type {Boolean}
    var locallyPaused = false

    // Remotely paused flag.
    // @type {Boolean}
    var remotelyPaused = false

    // Periodic stats flag.
    // @type {Boolean}
    var statsEnabled = false

    // Periodic stats gathering interval (milliseconds).
    // @type {Number}
    var statsInterval = DEFAULT_STATS_INTERVAL

    // Preferred profile.
    // @type {String}
    var _preferredProfile = "default"

    // Effective profile.
    // @type {String}
    var effectiveProfile: String? = null

    /**
     * Whether the Consumer is paused.
     *
     * @return {Boolean}
     */
    fun paused() = this.locallyPaused || this.remotelyPaused

    /**
     * Closes the Consumer.
     * This is called when the local Room is closed.
     *
     * @private
     */
    fun close() {
        logger.debug("close()")

        if (this.closed)
            return

        this.closed = true

        if (this.statsEnabled) {
            this.statsEnabled = false

            if (this.transport != null)
                this.transport?.disableConsumerStats(this)
        }

        this.emit("@close")
        this.safeEmit("close", "local")
        this.destroy()
    }

    /**
     * My remote Consumer was closed.
     * Invoked via remote notification.
     *
     * @private
     */
    fun remoteClose(appData: Any? = null) {
        logger.debug("remoteClose()")

        if (this.closed)
            return

        this.closed = true

        if (this.transport != null)
            this.transport?.removeConsumer(this)

        this.destroy()

        this.emit("@close")
        this.safeEmit("close", "remote")
    }

    fun destroy() {
        this.transport = null

        try {
//            this.track.stop()
            this.track?.dispose()
        } catch (error: Exception) {
        }

        this.track = null
    }

    /**
     * Receives RTP.
     *
     * @param {transport} Transport instance.
     *
     * @return {Promise} Resolves with a remote MediaStreamTrack.
     */
    fun receive(transport: Transport): Observable<Any> {
        logger.debug("receive() [transport:$transport]")

        if (this.closed)
            return Observable.create {
                it.onError(InvalidStateError("Consumer closed"))
            }
        else if (!this.supported)
            return Observable.create {
                it.onError(Exception("unsupported codecs"))
            }
        else if (this.transport != null)
            return Observable.create {
                it.onError(Exception("already handled by a Transport"))
            }
        this.transport = transport

        return transport.addConsumer(this)
            .flatMap { transceiversMediaTrack ->
                this.track = (transceiversMediaTrack as TransceiversMediaTrack).newTrack

                // If we were paused, disable the track.
                if (this.paused())
                    track?.setEnabled(false)

                transport.once("@close") {
                    if (this.closed || this.transport != transport)
                        return@once

                    this.transport = null

                    try {
//                        this.track.stop()
                        this.track?.dispose()
                    } catch (error: Exception) {
                    }

                    this.track = null

                    this.safeEmit("unhandled")
                }

                this.safeEmit("handled")

                if (this.statsEnabled)
                    transport.enableConsumerStats(this, this.statsInterval)

                Observable.create(ObservableOnSubscribe<TransceiversMediaTrack> {
                    it.onNext(transceiversMediaTrack)
                })
            }
    }

    /**
     * Pauses receiving media.
     *
     * @param {Any} [appData] - App custom data.
     *
     * @return {Boolean} true if paused.
     */
    fun pause(appData: Any): Boolean {
        logger.debug("pause()")

        if (this.closed) {
            logger.error("pause() | Consumer closed")

            return false
        } else if (this.locallyPaused) {
            return true
        }

        this.locallyPaused = true

        if (this.track != null)
            this.track?.setEnabled(false)

        if (this.transport != null)
            this.transport?.pauseConsumer(this, appData)

        this.safeEmit("pause", "local", appData)

        // Return true if really paused.
        return this.paused()
    }

    /**
     * My remote Consumer was paused.
     * Invoked via remote notification.
     *
     * @private
     *
     * @param {Any} [appData] - App custom data.
     */
    fun remotePause(appData: Any? = null) {
        logger.debug("remotePause()")

        if (this.closed || this.remotelyPaused)
            return

        this.remotelyPaused = true

        if (this.track != null)
            this.track?.setEnabled(false)

        this.safeEmit("pause", "remote", appData ?: "")
    }

    /**
     * Resumes receiving media.
     *
     * @param {Any} [appData] - App custom data.
     *
     * @return {Boolean} true if not paused.
     */
    fun resume(appData: Any): Boolean {
        logger.debug("resume()")

        if (this.closed) {
            logger.error("resume() | Consumer closed")

            return false
        } else if (!this.locallyPaused) {
            return true
        }

        this.locallyPaused = false

        if (this.track != null && !this.remotelyPaused)
            this.track?.setEnabled(true)

        if (this.transport != null)
            this.transport?.resumeConsumer(this, appData)

        this.safeEmit("resume", "local", appData)

        // Return true if not paused.
        return !this.paused()
    }

    /**
     * My remote Consumer was resumed.
     * Invoked via remote notification.
     *
     * @private
     *
     * @param {Any} [appData] - App custom data.
     */
    fun remoteResume(appData: Any?) {
        logger.debug("remoteResume()")

        if (this.closed || !this.remotelyPaused)
            return

        this.remotelyPaused = false

        if (this.track != null && !this.locallyPaused)
            this.track?.setEnabled(true)

        this.safeEmit("resume", "remote", appData ?: "")
    }

    /**
     * Set preferred receiving profile.
     *
     * @param {String} profile
     */
    fun setPreferredProfile(profile: String) {
        logger.debug("setPreferredProfile() [profile:$profile]")

        if (this.closed) {
            logger.error("setPreferredProfile() | Consumer closed")

            return
        } else if (profile == this._preferredProfile) {
            return
        } else if (!PROFILES.contains(profile)) {
            logger.error("setPreferredProfile() | invalid profile $profile")

            return
        }

        this._preferredProfile = profile

        if (this.transport != null)
            this.transport?.setConsumerPreferredProfile(this, this._preferredProfile)
    }

    /**
     * Preferred receiving profile was set on my remote Consumer.
     *
     * @param {String} profile
     */
    fun remoteSetPreferredProfile(profile: String) {
        logger.debug("remoteSetPreferredProfile() [profile:$profile]")

        if (this.closed || profile == this._preferredProfile)
            return

        this._preferredProfile = profile
    }

    /**
     * Effective receiving profile changed on my remote Consumer.
     *
     * @param {String} profile
     */
    fun remoteEffectiveProfileChanged(profile: String) {
        logger.debug("remoteEffectiveProfileChanged() [profile:$profile]")

        if (this.closed || profile == this.effectiveProfile)
            return

        this.effectiveProfile = profile

        this.safeEmit("effectiveprofilechange", this.effectiveProfile as String)
    }

    /**
     * Enables periodic stats retrieval.
     */
    fun enableStats(interval: Int = DEFAULT_STATS_INTERVAL) {
        logger.debug("enableStats() [interval:$interval]")

        if (this.closed) {
            logger.error("enableStats() | Consumer closed")

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
            this.transport?.enableConsumerStats(this, this.statsInterval)
    }

    /**
     * Disables periodic stats retrieval.
     */
    fun disableStats() {
        logger.debug("disableStats()")

        if (this.closed) {
            logger.error("disableStats() | Consumer closed")

            return
        }

        if (!this.statsEnabled)
            return

        this.statsEnabled = false

        if (this.transport != null)
            this.transport?.disableConsumerStats(this)
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
}