package com.versatica.mediasoup

/**
 * @author wolfhan
 */

class Peer(var name: String, var appData: Any?, private var logger: Logger = Logger("Peer")) :
    EnhancedEventEmitter(logger) {

    var closed = false
    private var _consumers = mutableMapOf<Int, Consumer>()

    /**
     * Closes the Peer.
     * This is called when the local Room is closed.
     *
     *  @private
     */
    fun close() {
        logger.debug("close()")

        if (this.closed)
            return

        this.closed = true

        this.emit("@close")
        this.safeEmit("close", "local")

        // Close all the Consumers.
        for (consumer in this._consumers.values) {
            consumer.close()
        }
    }

    /**
     * The remote Peer or Room was closed.
     * Invoked via remote notification.
     *
     * @private
     *
     * @param {Any} [appData] - App custom data.
     */
    fun remoteClose(appData: Any? = null) {
        logger.debug("remoteClose()")

        if (this.closed)
            return

        this.closed = true

        this.emit("@close")
        if (appData == null){
            this.safeEmit("close", "remote")
        }else{
            this.safeEmit("close", "remote", appData)
        }

        // Close all the Consumers.
        for (consumer in this._consumers.values) {
            consumer.remoteClose()
        }
    }

    /**
     * Get the Consumer with the given id.
     *
     * @param {Int} id
     *
     * @return {Consumer}
     */
    fun getConsumerById(id: Int): Consumer? = this._consumers[id]

    /**
     * Add an associated Consumer.
     *
     * @private
     *
     * @param {Consumer} consumer
     */
    fun addConsumer(consumer: Consumer) {
        if (this._consumers.containsKey(consumer.id))
            throw Exception("Consumer already exists [id:${consumer.id}]")

        // Store it.
        this._consumers[consumer.id] = consumer

        // Handle it.
        consumer.on("@close") {
            this._consumers.remove(consumer.id)
        }

        // Emit event.
        this.safeEmit("newconsumer", consumer)
    }

    /**
     * The list of Consumers.
     *
     * @return {Array<Consumer>}
     */
    fun consumers(): MutableList<Consumer>{
        return _consumers.values.toMutableList()
    }
}