package com.versatica.mediasoup

/**
 * Error produced when calling a method in an invalid state.
 */
class InvalidStateError(message: String?) : Throwable(message) {
    val name: String = "InvalidStateError"
}


/**
 * Error produced when a Promise is rejected due to a timeout.
 */
class TimeoutError(message: String?) : Throwable(message) {
    val name: String = "TimeoutError"
}

/**
 * Error indicating not support for something.
 */
class UnsupportedError(message: String?, var data: Any?) : Throwable(message) {
    val name: String = "UnsupportedError"
}