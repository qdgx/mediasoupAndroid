package com.versatica.mediasoup.demo

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.versatica.mediasoup.Logger
import kotlinx.android.synthetic.main.activity_main.*
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.util.*

class MainActivity : AppCompatActivity() {
    private var logger: Logger = Logger("MainActivity")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        safeEmit.setOnClickListener {
            val eventEmitterImpl: EventEmitterImpl = EventEmitterImpl()
            eventEmitterImpl.on("key"){ args ->
                var sb: String = String()
                for (arg in args){
                    sb += (" " + arg)
                }
                logger.debug("key: $sb")
            }
            eventEmitterImpl.safeEmit("key",101)
            eventEmitterImpl.safeEmit("key","hello world")
            eventEmitterImpl.safeEmit("key","hello world",101,102.3f,"end")
        }

        safeEmitAsPromiseTimmer.setOnClickListener {
            val eventEmitterImpl: EventEmitterImpl = EventEmitterImpl()
            eventEmitterImpl.on("@request"){ args ->
                var timer: Timer? = Timer()
                timer!!.schedule(object : TimerTask() {
                    override fun run() {
                        var length = args.size
                        if (length > 0){
                            var result = args.get(1) as  Int
                            //success callBack
                            var successCallBack = args.get(length - 2) as Function1<Any, Unit>
                            successCallBack.invoke("Result $result")
                        }
                    }
                }, 2000)
            }

            Observable.just(123)
                .flatMap(addFlatMapTimer())
                .flatMap(eventEmitterImpl.testPromise())
                .subscribe {
                    logger.debug(it as String)
                }
        }

        safeEmitAsPromiseThread.setOnClickListener {
            val eventEmitterImpl: EventEmitterImpl = EventEmitterImpl()
            eventEmitterImpl.on("@request"){ args ->
                Thread(object : Runnable{
                    override fun run() {
                        var length = args.size
                        if (length > 0){
                            var result = args.get(1) as  Int
                            //success callBack
                            var successCallBack = args.get(length - 2) as Function1<Any, Unit>
                            successCallBack.invoke("Result $result")
                        }
                    }
                }).start()
            }

            Observable.just(123)
                .flatMap(addFlatMapThread())
                .flatMap(eventEmitterImpl.testPromiseThread())
                .subscribe {
                    logger.debug(it as String)
                }

        }


        safeEmitAsPromiseMultiple.setOnClickListener {
            val eventEmitterImpl: EventEmitterImpl = EventEmitterImpl()
            eventEmitterImpl.on("@request"){ args ->
                var length = args.size
                if (length > 0){
                    var result = args.get(1) as  Int
                    var successCallBack = args.get(length - 2) as Function1<Any, Unit>
                    Observable.just(result)
                        .flatMap(addFlatMapTimer())
                        .subscribe {
                            //success callBack
                            successCallBack.invoke("Result $it")
                        }
                }
            }

            Observable.just(123)
                .flatMap(addFlatMapThread())
                .flatMap(eventEmitterImpl.testPromiseThread())
                .subscribe {
                    logger.debug(it as String)
                }

        }
    }

    private fun addFlatMap(): Function<Int, Observable<Int>> {
        return Function { input ->
            Observable.create(ObservableOnSubscribe<Int> {
                val result = input + input
                it.onNext(result)
            }).subscribeOn(Schedulers.io())
        }
    }

    private fun addFlatMapTimer(): Function<Int, Observable<Int>> {
        return Function { input ->
            Observable.create(ObservableOnSubscribe<Int> {
                var timer: Timer? = Timer()
                timer!!.schedule(object : TimerTask() {
                    override fun run() {
                        val result = input + input
                        it.onNext(result)
                    }
                }, 1000)
            })
        }
    }

    private fun addFlatMapThread(): Function<Int, Observable<Int>> {
        return Function { input ->
            Observable.create(ObservableOnSubscribe<Int> {
                Thread(object : Runnable{
                    override fun run() {
                        val result = input + input
                        it.onNext(result)
                    }
                }).start()
            })
        }
    }
}
