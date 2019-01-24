package com.versatica.mediasoup.handlers.webRtc

import java.util.concurrent.Executors

object ThreadUtil {
    /**
     * Thread which will be used to call all WebRTC PeerConnection APIs. They
     * they don't run on the calling thread anyway, we are deferring the calls
     * to this thread to avoid (potentially) blocking the calling thread.
     */
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Runs the given [Runnable] on the executor.
     * @param runnable
     */
    fun runOnExecutor(runnable: Runnable) {
        executor.execute(runnable)
    }
}