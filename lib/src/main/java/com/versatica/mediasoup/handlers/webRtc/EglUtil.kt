package com.versatica.mediasoup.handlers.webRtc

import android.os.Build
import com.versatica.mediasoup.Logger
import org.webrtc.EglBase

object EglUtil {
    /**
     * The root [EglBase] instance shared by the entire application for
     * the sake of reducing the utilization of system resources (such as EGL
     * contexts).
     */
    private val logger = Logger("CameraEventsHandler")

    private var rootEglBase: EglBase? = null

    val rootEglBaseContext: EglBase.Context?
        get() {
            val eglBase = getRootEglBase()

            return eglBase?.eglBaseContext
        }

    /**
     * Lazily creates and returns the one and only [EglBase] which will
     * serve as the root for all contexts that are needed.
     */
    @Synchronized
    fun getRootEglBase(): EglBase? {
        if (rootEglBase == null) {
            // XXX EglBase14 will report that isEGL14Supported() but its
            // getEglConfig() will fail with a RuntimeException with message
            // "Unable to find any matching EGL config". Fall back to EglBase10
            // in the described scenario.
            var eglBase: EglBase? = null
            val configAttributes = EglBase.CONFIG_PLAIN
            var cause: RuntimeException? = null

            try {
                // WebRTC internally does this check in isEGL14Supported, but it's no longer exposed
                // in the public API
                if (Build.VERSION.SDK_INT >= 18) {
                    eglBase = EglBase.createEgl14(configAttributes)
                }
            } catch (ex: RuntimeException) {
                // Fall back to EglBase10.
                cause = ex
            }

            if (eglBase == null) {
                try {
                    eglBase = EglBase.createEgl10(configAttributes)
                } catch (ex: RuntimeException) {
                    // Neither EglBase14, nor EglBase10 succeeded to initialize.
                    cause = ex
                }

            }

            if (cause != null) {
                logger.error("Failed to create EglBase: $cause")
            } else {
                rootEglBase = eglBase
            }
        }

        return rootEglBase
    }
}