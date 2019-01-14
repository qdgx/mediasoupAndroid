package com.versatica.mediasoup

/**
 * @author wolfhan
 */
class Consumer : EnhancedEventEmitter(logger = Logger("Consumer")) {

    var id: Int = 0

    fun close() {}

    fun remoteClose() {}
}