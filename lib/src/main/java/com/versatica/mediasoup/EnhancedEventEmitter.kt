package com.versatica

import com.versatica.eventemitter.EventEmitter
import com.versatica.mediasoup.Logger
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import io.reactivex.functions.Function
import java.lang.Exception

open class EnhancedEventEmitter : EventEmitter() {
    private var logger: Logger = Logger("EnhancedEventEmitter")

    init {
        this.setMaxListeners(Int.MAX_VALUE)
    }

    public fun safeEmit(event: String, vararg args: Any) {
        try {
            this.emit(event, *args)
        } catch (error: Exception) {
            logger.error(error.toString())
        }
    }

    public fun safeEmitAsPromise(observableEmitter: ObservableEmitter<Any>, event: String, vararg args: Any): Observable<Any> {
        return Observable.just("").flatMap(Function {
            Observable.create(ObservableOnSubscribe<Any> {
                //success callback
                var callback = { result: Any ->
                    observableEmitter.onNext(result)
                }
                //error callback
                var errback = { error: String ->
                    logger.error(error)
                    observableEmitter.onError(Throwable(error))
                }
                safeEmit(event, *args, callback, errback)
            })
        })
    }
}