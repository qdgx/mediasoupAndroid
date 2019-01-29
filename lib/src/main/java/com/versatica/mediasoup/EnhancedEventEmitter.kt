package com.versatica.mediasoup

import com.versatica.eventemitter.EventEmitter
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import java.lang.Exception

open class EnhancedEventEmitter(logger: Logger) : EventEmitter() {
    private var _logger: Logger = Logger("EnhancedEventEmitter")

    init {
        this.setMaxListeners(Int.MAX_VALUE)
        this._logger = logger
    }

    public fun safeEmit(event: String, vararg args: Any) {
        try {
            this.emit(event, *args)
        } catch (error: Exception) {
            _logger.error(error.toString())
        }
    }

    public fun safeEmitAsPromise(
        observableEmitter: ObservableEmitter<Any>,
        event: String,
        vararg args: Any
    ): Observable<Any> {
        return Observable.create {
            //success callback
            val callback = { result: Any ->
                observableEmitter.onNext(result)
            }
            //error callback
            val errback = { error: Throwable ->
                _logger.error(error.message!!)
                observableEmitter.onError(error)
            }
            safeEmit(event, *args, callback, errback)
        }
    }
}