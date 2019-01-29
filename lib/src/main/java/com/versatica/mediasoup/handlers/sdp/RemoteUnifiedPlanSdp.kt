package com.versatica.mediasoup.handlers.sdp

import com.dingsoft.sdptransform.*
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.Utils

/**
 * @author wolfhan
 */

object RemoteUnifiedPlanSdp {

    val logger = Logger("RemoteUnifiedPlanSdp")

    open class RemoteSdp(rtpParametersByKind: Map<String, RTCRtpParameters>) {

        // Transport local parameters, including DTLS parameters.
        var transportLocalParameters: TransportRemoteIceParameters? = null
            set(value) {
                logger.debug("setTransportLocalParameters() [transportLocalParameters:$value]")
                field = value
            }

        // Transport remote parameters, including ICE parameters, ICE candidates and DTLS parameteres.
        var transportRemoteParameters: TransportRemoteIceParameters? = null
            set(value) {
                logger.debug("setTransportRemoteParameters() [transportRemoteParameters:$value]")
                field = value
            }

        // SDP global fields.
        var sdpGlobalFields = SdpGlobalFields()

        class SdpGlobalFields {
            var id = Utils.randomNumber()
            var version = 0
        }

        fun updateTransportRemoteIceParameters(remoteIceParameters: RTCIceParameters) {
            logger.debug("updateTransportRemoteIceParameters() [remoteIceParameters:$remoteIceParameters]")
            this.transportRemoteParameters?.iceParameters = remoteIceParameters
        }
    }

    class SendRemoteSdp(var rtpParametersByKind: Map<String, RTCRtpParameters>) : RemoteSdp(rtpParametersByKind) {

        fun createAnswerSdp(localSdpObj: SessionDescription): String {
            logger.debug("createAnswerSdp()")

            if (this.transportLocalParameters == null)
                throw Exception("no transport local parameters")
            else if (this.transportRemoteParameters == null)
                throw Exception("no transport remote parameters")

            val remoteIceParameters = this.transportRemoteParameters?.iceParameters
            val remoteIceCandidates = this.transportRemoteParameters?.iceCandidates
            val remoteDtlsParameters = this.transportRemoteParameters?.dtlsParameters
            val sdpObj = SessionDescription()
            val bundleMids = localSdpObj.media
                .filter {
                    it.mid != null
                }.map {
                    it.mid as String
                }

            // Increase our SDP version.
            this.sdpGlobalFields.version++

            sdpObj.version = "0"
            sdpObj.origin = SessionDescription.Origin(
                address = "0.0.0.0",
                ipVer = 4,
                netType = "IN",
                sessionId = this.sdpGlobalFields.id.toLong(),
                sessionVersion = this.sdpGlobalFields.version,
                username = "mediasoup-client"
            )
            sdpObj.name = "-"
            sdpObj.timing = SessionDescription.Timing(start = 0, stop = 0)
            sdpObj.icelite = if (remoteIceParameters?.iceLite != null) "ice-lite" else null
//        sdpObj.msidSemantic = SessionAttributes.MsidSemantic(
//            semantic = "WMS",
//            token = "*"
//        )
            if (bundleMids.isNotEmpty()) {
                sdpObj.groups = arrayListOf(
                    SessionAttributes.Group(
                        type = "BUNDLE",
                        mids = bundleMids.joinToString(" ")
                    )
                )
            }

            sdpObj.media = arrayListOf()

            // NOTE: We take the latest fingerprint.
            val numFingerprints = remoteDtlsParameters?.fingerprints?.size
            if (numFingerprints != null) {
                sdpObj.fingerprint = SharedAttributes.Fingerprint(
                    type = remoteDtlsParameters.fingerprints?.get(numFingerprints - 1)?.algorithm ?: "",
                    hash = remoteDtlsParameters.fingerprints?.get(numFingerprints - 1)?.value ?: ""
                )
            }

            for (localMediaObj in localSdpObj.media) {
                val closed = localMediaObj.direction == "inactive"
                val kind = localMediaObj.type
                val codecs = this.rtpParametersByKind[kind]?.codecs
                val headerExtensions = this.rtpParametersByKind[kind]?.headerExtensions
                val remoteMediaObj = SessionDescription.Media()
                remoteMediaObj.type = localMediaObj.type
                remoteMediaObj.port = 7
                remoteMediaObj.protocol = "RTP/SAVPF"
                remoteMediaObj.connection = SharedDescriptionFields.Connection(ip = "127.0.0.1", version = 4)
                remoteMediaObj.mid = localMediaObj.mid
                remoteMediaObj.iceUfrag = remoteIceParameters?.usernameFragment
                remoteMediaObj.icePwd = remoteIceParameters?.password
                remoteMediaObj.candidates = arrayListOf()

                if (remoteIceCandidates != null) {
                    for (candidate in remoteIceCandidates) {
                        // mediasoup does not support non rtcp-mux so candidates component is always RTP (1).
                        val candidateObj = MediaAttributes.Candidate(
                            component = 1,
                            foundation = candidate.foundation ?: "",
                            ip = candidate.ip ?: "",
                            port = candidate.port ?: 0,
                            priority = candidate.priority ?: 0,
                            transport = candidate.protocol?.v ?: "",
                            type = candidate.type?.v ?: "",
                            tcptype = candidate.tcpType?.v ?: ""
                        )
                        remoteMediaObj.candidates.add(candidateObj)
                    }
                }
                remoteMediaObj.endOfCandidates = "end-of-candidates"

                // Announce support for ICE renomination.
                // https://tools.ietf.org/html/draft-thatcher-ice-renomination
                remoteMediaObj.iceOptions = "renomination"

                when (remoteDtlsParameters?.role) {
                    RTCDtlsRole.client -> remoteMediaObj.setup = "active"
                    RTCDtlsRole.server -> remoteMediaObj.setup = "passive"
                }

                when (localMediaObj.direction) {
                    "sendrecv", "sendonly" -> remoteMediaObj.direction = "recvonly"
                    "recvonly", "inactive" -> remoteMediaObj.direction = "inactive"
                }

                remoteMediaObj.rtp = arrayListOf()
                remoteMediaObj.rtcpFb = arrayListOf()
                remoteMediaObj.fmtp = arrayListOf()

                if (codecs != null) {
                    for (codec in codecs) {
                        val rtp = MediaAttributes.Rtp(
                            payload = codec.payloadType,
                            codec = codec.name ?: "",
                            rate = codec.clockRate?.toInt()
                        )

                        if (codec.channels != null && codec.channels!! > 1)
                            rtp.encoding = codec.channels.toString()

                        remoteMediaObj.rtp?.add(rtp)

                        if (codec.parameters != null) {
                            val paramFmtp = MediaAttributes.Fmtp(
                                payload = codec.payloadType,
                                config = ""
                            )

                            val keys = codec.parameters?.keys
                            if (keys != null) {
                                for (key in keys) {
                                    if (paramFmtp.config.isNotEmpty())
                                        paramFmtp.config += ';'
                                    paramFmtp.config += "$key=${codec.parameters?.get(key)}"
                                }
                            }

                            if (paramFmtp.config.isNotEmpty())
                                remoteMediaObj.fmtp?.add(paramFmtp)
                        }

                        if (codec.rtcpFeedback != null && codec.rtcpFeedback!!.isNotEmpty()) {
                            for (fb in codec.rtcpFeedback!!) {
                                remoteMediaObj.rtcpFb?.add(
                                    MediaAttributes.RtcpFb(
                                        payload = codec.payloadType.toString(),
                                        type = fb.type,
                                        subtype = fb.parameter
                                    )
                                )
                            }
                        }
                    }
                }

                remoteMediaObj.payloads = codecs
                    ?.map {
                        it.payloadType
                    }
                    ?.joinToString(" ")

                // NOTE: Firefox does not like a=extmap lines if a=inactive.
                if (!closed) {
                    remoteMediaObj.ext = arrayListOf()
                    if (headerExtensions != null) {
                        for (ext in headerExtensions) {
                            // Don't add a header extension if not present in the offer.
                            val matchedLocalExt = localMediaObj.ext?.find {
                                it.uri === ext.uri
                            }

                            if (matchedLocalExt != null)
                                continue

                            remoteMediaObj.ext?.add(
                                SharedAttributes.Ext(
                                    uri = ext.uri,
                                    value = ext.id
                                )
                            )
                        }
                    }
                }

                // Simulcast.
                if (localMediaObj.simulcast_03 != null) {
                    // eslint-disable-next-line camelcase
                    val oldValue = localMediaObj.simulcast_03?.value
                    val newValue = Regex("send").replace(oldValue ?: "", "recv")
                    remoteMediaObj.simulcast_03 = MediaAttributes.Simulcast_03(value = newValue)

                    remoteMediaObj.rids = arrayListOf()

                    if (localMediaObj.rids != null && localMediaObj.rids!!.isNotEmpty()) {
                        for (rid in localMediaObj.rids!!) {
                            if (rid.direction !== "send")
                                continue
                            remoteMediaObj.rids?.add(
                                MediaAttributes.Rid(
                                    id = rid.id,
                                    direction = "recv"
                                )
                            )
                        }
                    }
                }

                remoteMediaObj.rtcpMux = "rtcp-mux"
                remoteMediaObj.rtcpRsize = "rtcp-rsize"

                // Push it.
                sdpObj.media.add(remoteMediaObj)
            }
            return SdpTransform().write(sdpObj)
        }

    }

    class RecvRemoteSdp(var rtpParametersByKind: Map<String, RTCRtpParameters>) : RemoteSdp(rtpParametersByKind) {

        /**
         * @param {Array<Object>} consumerInfo - Consumer informations.
         * @return {String}
         */
        fun createOfferSdp(consumerInfo: MutableList<ConsumerInfo>): String {
            logger.debug("createOfferSdp()")

            if (this.transportRemoteParameters == null)
                throw Exception("no transport remote parameters")

            val remoteIceParameters = this.transportRemoteParameters?.iceParameters
            val remoteIceCandidates = this.transportRemoteParameters?.iceCandidates
            val remoteDtlsParameters = this.transportRemoteParameters?.dtlsParameters
            val sdpObj = SessionDescription()
            val mids = consumerInfo.map {
                it.mid
            }

            // Increase our SDP version.
            this.sdpGlobalFields.version++

            sdpObj.version = "0"
            sdpObj.origin = SessionDescription.Origin(
                address = "0.0.0.0",
                ipVer = 4,
                netType = "IN",
                sessionId = this.sdpGlobalFields.id.toLong(),
                sessionVersion = this.sdpGlobalFields.version,
                username = "mediasoup-client"
            )
            sdpObj.name = "-"
            sdpObj.timing = SessionDescription.Timing(start = 0, stop = 0)
            sdpObj.icelite = if (remoteIceParameters?.iceLite != null) "ice-lite" else null
//        sdpObj.msidSemantic = SessionAttributes.MsidSemantic(
//            semantic = "WMS",
//            token = "*"
//        )

            if (mids.isNotEmpty()) {
                sdpObj.groups = arrayListOf(
                    SessionAttributes.Group(
                        type = "BUNDLE",
                        mids = mids.joinToString(" ")
                    )
                )
            }

            sdpObj.media = arrayListOf()

            // NOTE: We take the latest fingerprint.
            val numFingerprints = remoteDtlsParameters?.fingerprints?.size
            if (numFingerprints != null) {
                sdpObj.fingerprint = SharedAttributes.Fingerprint(
                    type = remoteDtlsParameters.fingerprints?.get(numFingerprints - 1)?.algorithm ?: "",
                    hash = remoteDtlsParameters.fingerprints?.get(numFingerprints - 1)?.value ?: ""
                )
            }

            for (info in consumerInfo) {
                val closed = info.closed
                val kind = info.kind
                var codecs: MutableCollection<RTCRtpCodecParameters>? = null
                var headerExtensions: MutableCollection<RTCRtpHeaderExtensionParameters>? = null

                if (info.kind !== "application") {
                    codecs = this.rtpParametersByKind[kind]?.codecs
                    headerExtensions = this.rtpParametersByKind[kind]?.headerExtensions
                }

                val remoteMediaObj = SessionDescription.Media()
                if (info.kind !== "application") {
                    remoteMediaObj.type = kind
                    remoteMediaObj.port = 7
                    remoteMediaObj.protocol = "RTP/SAVPF"
                    remoteMediaObj.connection = SharedDescriptionFields.Connection(ip = "127.0.0.1", version = 4)
                    remoteMediaObj.mid = info.mid
                    remoteMediaObj.msid = "${info.streamId} ${info.trackId}"
                } else {
                    remoteMediaObj.type = kind
                    remoteMediaObj.port = 9
                    remoteMediaObj.protocol = "DTLS/SCTP"
                    remoteMediaObj.connection = SharedDescriptionFields.Connection(ip = "127.0.0.1", version = 4)
                    remoteMediaObj.mid = info.mid
                }

                remoteMediaObj.iceUfrag = remoteIceParameters?.usernameFragment
                remoteMediaObj.icePwd = remoteIceParameters?.password
                remoteMediaObj.candidates = arrayListOf()

                if (remoteIceCandidates != null) {
                    for (candidate in remoteIceCandidates) {
                        // mediasoup does not support non rtcp-mux so candidates component is always RTP (1).
                        val candidateObj = MediaAttributes.Candidate(
                            component = 1,
                            foundation = candidate.foundation ?: "",
                            ip = candidate.ip ?: "",
                            port = candidate.port ?: 0,
                            priority = candidate.priority ?: 0,
                            transport = candidate.protocol?.v ?: "",
                            type = candidate.type?.v ?: "",
                            tcptype = candidate.tcpType?.v ?: ""
                        )
                        remoteMediaObj.candidates.add(candidateObj)
                    }
                }

                remoteMediaObj.endOfCandidates = "end-of-candidates"

                // Announce support for ICE renomination.
                // https://tools.ietf.org/html/draft-thatcher-ice-renomination
                remoteMediaObj.iceOptions = "renomination"
                remoteMediaObj.setup = "actpass"

                if (info.kind !== "application") {
                    if (!closed)
                        remoteMediaObj.direction = "sendonly"
                    else
                        remoteMediaObj.direction = "inactive"

                    remoteMediaObj.rtp = arrayListOf()
                    remoteMediaObj.rtcpFb = arrayListOf()
                    remoteMediaObj.fmtp = arrayListOf()

                    if (codecs != null) {
                        for (codec in codecs) {
                            val rtp = MediaAttributes.Rtp(
                                payload = codec.payloadType,
                                codec = codec.name ?: "",
                                rate = codec.clockRate?.toInt()
                            )

                            if (codec.channels != null && codec.channels!! > 1)
                                rtp.encoding = codec.channels.toString()

                            remoteMediaObj.rtp?.add(rtp)

                            if (codec.parameters != null) {
                                val paramFmtp = MediaAttributes.Fmtp(
                                    payload = codec.payloadType,
                                    config = ""
                                )

                                val keys = codec.parameters?.keys
                                if (keys != null) {
                                    for (key in keys) {
                                        if (paramFmtp.config.isNotEmpty())
                                            paramFmtp.config += ";"
                                        paramFmtp.config += "$key=${codec.parameters?.get(key)}"
                                    }
                                }

                                if (paramFmtp.config.isNotEmpty())
                                    remoteMediaObj.fmtp?.add(paramFmtp)
                            }

                            if (codec.rtcpFeedback != null && codec.rtcpFeedback!!.isNotEmpty()) {
                                for (fb in codec.rtcpFeedback!!) {
                                    remoteMediaObj.rtcpFb?.add(
                                        MediaAttributes.RtcpFb(
                                            payload = codec.payloadType.toString(),
                                            type = fb.type,
                                            subtype = fb.parameter
                                        )
                                    )
                                }
                            }
                        }
                    }

                    remoteMediaObj.payloads = codecs
                        ?.map {
                            it.payloadType
                        }
                        ?.joinToString(" ")

                    // NOTE: Firefox does not like a=extmap lines if a=inactive.
                    if (!closed) {
                        remoteMediaObj.ext = arrayListOf()
                        if (headerExtensions != null) {
                            for (ext in headerExtensions) {
                                // Ignore MID RTP extension for receiving media.
                                if (ext.uri === "urn:ietf:params:rtp-hdrext:sdes:mid")
                                    continue

                                remoteMediaObj.ext?.add(
                                    SharedAttributes.Ext(
                                        uri = ext.uri,
                                        value = ext.id
                                    )
                                )
                            }
                        }
                    }

                    remoteMediaObj.rtcpMux = "rtcp-mux"
                    remoteMediaObj.rtcpRsize = "rtcp-rsize"

                    if (!closed) {
                        remoteMediaObj.ssrcs = arrayListOf()
                        remoteMediaObj.ssrcGroups = arrayListOf()

                        remoteMediaObj.ssrcs?.add(
                            MediaAttributes.Ssrc(
                                id = info.ssrc,
                                attribute = "cname",
                                value = info.cname
                            )
                        )

                        if (info.rtxSsrc != null) {
                            remoteMediaObj.ssrcs?.add(
                                MediaAttributes.Ssrc(
                                    id = info.rtxSsrc!!,
                                    attribute = "cname",
                                    value = info.cname
                                )
                            )

                            // Associate original and retransmission SSRC.
                            remoteMediaObj.ssrcGroups?.add(
                                MediaAttributes.SsrcGroup(
                                    semantics = "FID",
                                    ssrcs = "${info.ssrc} ${info.rtxSsrc}"
                                )
                            )
                        }
                    }
                } else {
                    remoteMediaObj.payloads = "5000"
                    remoteMediaObj.sctpmap =
                            MediaAttributes.Sctpmap(
                                app = "webrtc-datachannel",
                                maxMessageSize = 256,
                                sctpmapNumber = 5000
                            )
                }

                // Push it.
                sdpObj.media.add(remoteMediaObj)
            }

            return SdpTransform().write(sdpObj)
        }
    }

    fun newInstance(direction: String, rtpParametersByKind: Map<String, RTCRtpParameters>) =
        when (direction) {
            "send" -> SendRemoteSdp(rtpParametersByKind)
            "recv" -> RecvRemoteSdp(rtpParametersByKind)
            else -> null
        }

}

data class TransportRemoteIceParameters(
    var iceParameters: RTCIceParameters = RTCIceParameters(),
    var iceCandidates: MutableList<RTCIceCandidateDictionary> = mutableListOf(),
    var dtlsParameters: RTCDtlsParameters = RTCDtlsParameters()
)

data class ConsumerInfo(
    var kind: String,
    var streamId: String,
    var trackId: String,
    var ssrc: Long,
    var cname: String,
    var mid: String = "",
    var closed: Boolean = false,
    var rtxSsrc: Long? = 0
)
