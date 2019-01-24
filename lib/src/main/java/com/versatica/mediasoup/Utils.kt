package com.versatica.mediasoup

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import java.util.*

object Utils {

    private const val MAX: Int = 99999999
    private const val MIN: Int = 10000000

    /**
     * Generates a random positive number between 10000000 and 99999999.
     *
     * @return random number
     */
    fun randomNumber(): Int {
        return Random().nextInt(MAX - MIN + 1) + MIN
    }

    /**
     * Clones the given JSONObject
     *
     * @param obj original JSONObject
     * @return new clone JSONObject
     */
    fun clone(obj: JSONObject): JSONObject {
        val sb = JSON.toJSONString(obj)
        return JSON.parseObject(sb)
    }

    /**
     * Clones the given JSONArray
     *
     * @param obj original JSONArray
     * @return new clone JSONArray
     */
    fun clone(obj: JSONArray): JSONArray {
        val sb = JSON.toJSONString(obj)
        return JSON.parseArray(sb)
    }
}