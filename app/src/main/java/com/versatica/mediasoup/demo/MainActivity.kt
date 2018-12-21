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

        safeEmitAsPromise.setOnClickListener {
            val eventEmitterImpl: EventEmitterImpl = EventEmitterImpl()
            eventEmitterImpl.on("key"){ args ->

            }
            eventEmitterImpl.safeEmit("key",101)

            Observable.just(123)
                .flatMap(addFlatMap())
                .flatMap(eventEmitterImpl.testPromise())

        }
    }

    private fun addFlatMap(): Function<Int, Observable<Int>> {
//        return Function { input ->
//            Observable.create(ObservableOnSubscribe<Int> {
//                val result = input + input
//                log("calculating $input + $input...")
//                it.onNext(result)
//            }).subscribeOn(Schedulers.io())
//        }
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

}
