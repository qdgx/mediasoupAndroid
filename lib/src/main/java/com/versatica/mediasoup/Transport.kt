package com.versatica.mediasoup

import com.versatica.mediasoup.handlers.sdp.RTCDtlsParameters
import com.versatica.mediasoup.handlers.sdp.RTCExtendedRtpCapabilities
import com.versatica.mediasoup.handlers.sdp.RTCRtpParameters

val logger = Logger("Transport")

class Transport(
    direction: String,
    extendedRtpCapabilities: RTCExtendedRtpCapabilities,
    settings: RoomOptions,
    appData: Any? = null
): EnhancedEventEmitter(logger){

    init {

    }
}

class RestartTransportRequest {
    var id: String? = null
}

class EnableTransportStatsNotify {
    var id: String? = null
    var interval: Int? = 0
}

class DisableTransportStatsNotify {
    var id: String? = null
    var interval: Int? = 0
}

class CreateTransportRequest {
    var id: String? = null
    var direction: String? = null
    var options: TransportOptions? = null
    var appData: Any? = null
    var dtlsParameters: RTCDtlsParameters? = null
}

class UpdateTransportNotify {
    var id: String? = null
}

class updateProducerNotify {
    var id: String? = null
    var rtpParameters: RTCRtpParameters? = null
}

class PauseProducerNotify {
    var id: String? = null
    var appData: Any? = null
}

class EnableProducerStatsNotify {
    var id: String? = null
    var interval: Int? = 0
}

class ResumeProducerNotify {
    var id: String? = null
    var appData: Any? = null
}

class DisableProducerStatsNotify {
    var id: String? = null
}

class PauseConsumerNotify {
    var id: String? = null
    var appData: Any? = null
}

class ResumeConsumerNotify {
    var id: String? = null
    var appData: Any? = null
}

class SetConsumerPreferredProfileNotify {
    var id: String? = null
    var profile: String? = null
}

class EnableConsumerStatsNotify {
    var id: String? = null
    var interval: Int? = 0
}

class DisableConsumerStatsNotify {
    var id: String? = null
}

class CreateProducerRequest {
    var id: String? = null
    var kind: String? = null
    var transportId: Int? = null
    var rtpParameters: RTCRtpParameters? = null
    var paused: Boolean = false
    var appData: Any? = null
}

class EnableConsumerRequest {
    var id: String? = null
    var transportId: Int? = null
    var paused: Boolean = false
    var preferredProfile: String? = null
}