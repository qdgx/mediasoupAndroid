package com.versatica.eventemitter

/**
 * Copyright 2018-2019 ShaoBoCheng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 * EventEmitterInterface,define the interface of EventEmitter
 *
 * @author ShaoBoCheng
 */

interface EventEmitterInterface {
    // Default maxnumber of listeners
    var defaultMaxListeners: Int

    /**
     * Synchronously calls each of the listeners registered for the event named eventName,
     * in the order they were registered, passing the supplied arguments to each.
     *
     * @param eventName The name of the event
     * @param args  Vararg arguments
     * @return true if the event had listeners, false otherwise
     */
    @Throws(Exception::class)
    fun emit(eventName: String, vararg args: Any): Boolean

    /**
     * Adds a one-time listener function for the event named eventName. The next time eventName is triggered, this listener is removed and then invoked.
     * Multiple calls passing the same combination of eventName and listener will result in
     * the listener being added, and called, multiple times.
     *
     * @param eventName The name of the event
     * @param listener The callback function
     * @return a reference to the EventEmitter, so that calls can be chained
     */
    @Throws(Exception::class)
    fun on(eventName: String, listener: (args: Array<out Any>) -> Unit): EventEmitterInterface

    /**
     * Adds a one-time listener function for the event named eventName.
     * The next time eventName is triggered, this listener is removed and then invoked.
     *
     * @param eventName The name of the event
     * @param listener The callback function
     * @return a reference to the EventEmitter, so that calls can be chained
     */
    @Throws(Exception::class)
    fun once(eventName: String, listener: (args: Array<out Any>) -> Unit): EventEmitterInterface

    /**
     * Alias for emitter.on(eventName, listener).
     *
     * @param eventName The name of the event
     * @param listener The callback function
     * @param isOnce listener status loop(default) or once
     * @return a reference to the EventEmitter, so that calls can be chained
     */
    fun addListener(
        eventName: String,
        listener: (args: Array<out Any>) -> Unit,
        isOnce: Boolean = false
    ): EventEmitterInterface

    /**
     * Removes the specified listener from the listener array for the event named eventName.
     *
     * @param eventName The name of the event
     * @param listener The callback function
     * @return a reference to the EventEmitter, so that calls can be chained
     */
    fun removeListener(eventName: String, listener: (args: Array<out Any>) -> Unit): EventEmitterInterface

    /**
     * Removes all listeners, or those of the specified eventName.
     *
     * @param eventName The name of the event
     * @return a reference to the EventEmitter, so that calls can be chained
     */
    fun removeAllListeners(eventName: String): EventEmitterInterface

    /**
     * Removes all listeners.
     *
     * @return a reference to the EventEmitter, so that calls can be chained
     */
    fun removeAllListeners(): EventEmitterInterface

    /**
     * By default EventEmitters will print a warning if more than 10 listeners
     * are added for a particular event. This is a useful default that helps
     * finding memory leaks. Obviously, not all events should be limited to
     * just 10 listeners. The emitter.setMaxListeners() method allows the limit
     * to be modified for this specific EventEmitter instance.
     * The value can be set to Infinity (or 0) to indicate an unlimited number of listeners
     *
     * @param n maxnumber of listeners
     * @return a reference to the EventEmitter, so that calls can be chained
     */
    fun setMaxListeners(n: Int): EventEmitterInterface

    /**
     * Get a copy of the array of listeners for the event named eventName.
     *
     * @param eventName The name of the event
     * @return a copy of the array of listeners for the event named eventName.
     */
    fun listeners(eventName: String): List<(args: Array<out Any>) -> Unit>

    /**
     * Get the number of listeners listening to the event named eventName
     *
     * @param eventName The name of the event
     * @return the number of listeners listening to the event named eventName.
     */
    fun listenerCount(eventName: String): Int
}