package com.versatica.mediasoup

import com.versatica.eventemitter.EventEmitter

val logger = Logger("Handle")

class Handle : EventEmitter(){
    companion object {
        fun getNativeRtpCapabilities(){
            logger.debug("getNativeRtpCapabilities()")

        }
    }

}