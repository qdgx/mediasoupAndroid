package com.versatica.mediasoup.demo

import com.versatica.EnhancedEventEmitter
import com.versatica.mediasoup.CommandQueue
import com.versatica.mediasoup.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers

val logger = Logger("EventEmitterImpl")

class EventEmitterImpl : EnhancedEventEmitter(logger) {
    private val commandQueue = CommandQueue()

    init {
        commandQueue.on("exec") { args ->
            _execCommand(args[0] as CommandQueue.Command, args[1] as CommandQueue.PromiseHolder)
        }
    }

    fun testPromise(): Function<Any, Observable<Any>> {
        return Function { input ->
            Observable.create {
                if (input is Int) {
                    val result = input + input
                    safeEmitAsPromise(it, "@request", "enableConsumer", result).subscribe()
                }
            }
        }
    }

    fun testPromiseThread(): Function<Any, Observable<Any>> {
        return Function { input ->
            Observable.create {
                Thread(object : Runnable {
                    override fun run() {
                        if (input is Int) {
                            val result = input + input
                            safeEmitAsPromise(it, "@request", "enableConsumer", result).subscribe()
                        }
                    }
                }).start()
            }
        }
    }

    fun addProducer(producer:String): Observable<Any>{
        logger.debug("addProducer() producer")
        return this.commandQueue.push("addProducer",producer)
    }

    private fun _execCommand(command: CommandQueue.Command, promiseHolder: CommandQueue.PromiseHolder) {
        var promise: Observable<Any>? = null
        try {
            when (command.method) {
                "addProducer" -> {
                    var data = command.data
                    promise = this._execAddProducer(data)
                }
                "removeProducer" -> {

                }
                else -> {
                    promise = Observable.create {
                        it.onError(Throwable("unknown command method $command.method"))
                    }
                }
            }
        } catch (e: Exception) {
            promise = Observable.create {
                it.onError(Throwable(e.message))
            }
        }

        // Fill the given Promise holder.
        promiseHolder.promise = promise
    }

    private fun _execAddProducer(data:Any):Observable<Any>?{
        logger.debug("_execAddProducer()")
        // Call the handler.
        return Observable.just(data as String)
            .flatMap { str: String->
                Observable.create(ObservableOnSubscribe<String> {
                    //next
                    it.onNext("_execAddProducer step1 $str")
                })
            }.flatMap {str: String->
                Observable.create(ObservableOnSubscribe<Any> {
                    //next
                    this.safeEmitAsPromise(it,"@request","createProducer","_execAddProducer step2 $str")
                        .subscribe()
                })
            }.flatMap {data: Any->
                Observable.create(ObservableOnSubscribe<Any> {
                    //next
                    it.onNext(data)
                })
            }
    }
}