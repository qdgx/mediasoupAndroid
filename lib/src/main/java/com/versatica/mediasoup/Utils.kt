package com.versatica.mediasoup

import com.alibaba.fastjson.JSON
import java.util.*

/**
 * @author wolfhan
 */

fun randomNumber() = Random().nextInt(99_999_999 + 1 - 10_000_000) + 10_000_000

fun clone(obj: Any) = JSON.parse(JSON.toJSONString(obj))