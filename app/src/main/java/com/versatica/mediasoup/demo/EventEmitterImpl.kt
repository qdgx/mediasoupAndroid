package com.versatica.mediasoup.demo

import com.versatica.EnhancedEventEmitter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers


class EventEmitterImpl: EnhancedEventEmitter() {
    init {
    }

    public fun testPromise(): Function<Any, Observable<Any>> {
        return Function { input ->
            Observable.create{
                if (input is Int){
                    val result = input + input
                    safeEmitAsPromise(it,"@request","enableConsumer",result).subscribe()
                }
            }
        }
    }

    public fun testPromiseThread(): Function<Any, Observable<Any>> {
        return Function { input ->
            Observable.create{
                Thread(object : Runnable{
                    override fun run() {
                        if (input is Int){
                            val result = input + input
                            safeEmitAsPromise(it,"@request","enableConsumer",result).subscribe()
                        }
                    }
                }).start()
            }
        }
    }
}