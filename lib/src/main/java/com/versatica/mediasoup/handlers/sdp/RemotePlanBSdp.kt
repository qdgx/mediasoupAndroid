package com.versatica.mediasoup.handlers.sdp

import com.dingsoft.sdptransform.*
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.Utils

/**
 * @author wolfhan
 */


object RemotePlanBSdp {

    val logger = Logger("RemotePlanBSdp")

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
            val mids = localSdpObj.media
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
            sdpObj.msidSemantic = SessionAttributes.MsidSemantic(
                semantic = "WMS",
                token = "*"
            )
            if (mids.isNotEmpty()) {
                sdpObj.groups = mutableListOf(
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

            for (localMediaObj in localSdpObj.media) {
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
                        val candidateObj = MediaAttributes.Candidate(
                            // mediasoup does not support non rtcp-mux so candidates component is
                            // always RTP (1).
                            component = 1,
                            foundation = candidate.foundation ?: "",
                            ip = candidate.ip ?: "",
                            port = candidate.port ?: 0,
                            priority = candidate.priority ?: 0,
                            transport = candidate.protocol?.name ?: "",
                            type = candidate.type?.name ?: "",
                            tcptype = candidate.tcpType?.name ?: ""
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

                // If video, be ready for simulcast.
                if (kind == "video")
                    remoteMediaObj.xGoogleFlag = "conference"

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

                remoteMediaObj.ext = arrayListOf()
                if (headerExtensions != null) {
                    for (ext in headerExtensions) {
                        // Don't add a header extension if not present in the offer.
                        val matchedLocalExt = localMediaObj.ext?.find {
                            it.uri == ext.uri
                        } ?: continue

                        remoteMediaObj.ext?.add(
                            SharedAttributes.Ext(
                                uri = ext.uri,
                                value = ext.id
                            )
                        )
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
         * @param {Array<String>} kinds - Media kinds.
         * @param {Array<Object>} consumerInfos - Consumer informations.
         * @return {String}
         */
        fun createOfferSdp(kinds: List<String>, consumerInfos: MutableList<ConsumerInfo>): String {
            logger.debug("createOfferSdp()")

            if (this.transportRemoteParameters == null)
                throw Exception("no transport remote parameters")

            val remoteIceParameters = this.transportRemoteParameters?.iceParameters
            val remoteIceCandidates = this.transportRemoteParameters?.iceCandidates
            val remoteDtlsParameters = this.transportRemoteParameters?.dtlsParameters
            val sdpObj = SessionDescription()
            val mids = kinds

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
            sdpObj.msidSemantic = SessionAttributes.MsidSemantic(
                semantic = "WMS",
                token = "*"
            )

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

            for (kind in kinds) {
                val codecs = this.rtpParametersByKind[kind]?.codecs
                val headerExtensions = this.rtpParametersByKind[kind]?.headerExtensions
                val remoteMediaObj = SessionDescription.Media()
                remoteMediaObj.type = kind
                remoteMediaObj.port = 7
                remoteMediaObj.protocol = "RTP/SAVPF"
                remoteMediaObj.connection = SharedDescriptionFields.Connection(ip = "127.0.0.1", version = 4)
                remoteMediaObj.mid = kind
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
                            transport = candidate.protocol?.name ?: "",
                            type = candidate.type?.name ?: "",
                            tcptype = candidate.tcpType?.name ?: ""
                        )
                        remoteMediaObj.candidates.add(candidateObj)
                    }
                }

                remoteMediaObj.endOfCandidates = "end-of-candidates"

                // Announce support for ICE renomination.
                // https://tools.ietf.org/html/draft-thatcher-ice-renomination
                remoteMediaObj.iceOptions = "renomination"
                remoteMediaObj.setup = "actpass"

                if (consumerInfos.any {
                        it.kind == kind
                    })
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

                remoteMediaObj.ext = arrayListOf()
                if (headerExtensions != null) {
                    for (ext in headerExtensions) {
                        // Ignore MID RTP extension for receiving media.
                        if (ext.uri == "urn:ietf:params:rtp-hdrext:sdes:mid")
                            continue

                        remoteMediaObj.ext?.add(
                            SharedAttributes.Ext(
                                uri = ext.uri,
                                value = ext.id
                            )
                        )
                    }
                }

                remoteMediaObj.rtcpMux = "rtcp-mux"
                remoteMediaObj.rtcpRsize = "rtcp-rsize"

                remoteMediaObj.ssrcs = arrayListOf()
                remoteMediaObj.ssrcGroups = arrayListOf()

                for (info in consumerInfos) {
                    if (info.kind != kind)
                        continue

                    remoteMediaObj.ssrcs?.add(
                        MediaAttributes.Ssrc(
                            id = info.ssrc,
                            attribute = "msid",
                            value = "${info.streamId} ${info.trackId}"
                        )
                    )

                    remoteMediaObj.ssrcs?.add(
                        MediaAttributes.Ssrc(
                            id = info.ssrc,
                            attribute = "mslabel",
                            value = info.streamId
                        )
                    )

                    remoteMediaObj.ssrcs?.add(
                        MediaAttributes.Ssrc(
                            id = info.ssrc,
                            attribute = "label",
                            value = info.trackId
                        )
                    )

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
                                id = info.rtxSsrc ?: 0,
                                attribute = "msid",
                                value = "${info.streamId} ${info.trackId}"
                            )
                        )

                        remoteMediaObj.ssrcs?.add(
                            MediaAttributes.Ssrc(
                                id = info.rtxSsrc ?: 0,
                                attribute = "mslabel",
                                value = info.streamId
                            )
                        )

                        remoteMediaObj.ssrcs?.add(
                            MediaAttributes.Ssrc(
                                id = info.rtxSsrc ?: 0,
                                attribute = "label",
                                value = info.trackId
                            )
                        )

                        remoteMediaObj.ssrcs?.add(
                            MediaAttributes.Ssrc(
                                id = info.rtxSsrc ?: 0,
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

                // Push it.
                sdpObj.media.add(remoteMediaObj)
            }
            return SdpTransform().write(sdpObj)
        }
    }

    fun newInstance(direction: String, rtpParametersByKind: Map<String, RTCRtpParameters>) = when (direction) {
        "send" -> SendRemoteSdp(rtpParametersByKind)
        "recv" -> RecvRemoteSdp(rtpParametersByKind)
        else -> null
    }

}