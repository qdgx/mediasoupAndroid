package com.versatica.mediasoup

/**
 * Error produced when calling a method in an invalid state.
 */
class InvalidStateError: Throwable{
    val name: String = "InvalidStateError"

    constructor(message: String?): super(message)
}


/**
 * Error produced when a Promise is rejected due to a timeout.
 */
class TimeoutError: Throwable{
    val name: String = "TimeoutError"

    constructor(message: String?): super(message)
}

/**
 * Error indicating not support for something.
 */
class UnsupportedError: Throwable{
    val name: String = "UnsupportedError"
    var data: Any? = null

    constructor(message: String?, data: Any?): super(message){
        this.data = data
    }
}