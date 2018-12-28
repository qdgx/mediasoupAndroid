package com.versatica.mediasoup.sdp

import com.dingsoft.sdptransform.*
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.randomNumber

/**
 * @author wolfhan
 */

val logger = Logger("RemoteUnifiedPlanSdp")

open class RemoteSdp(rtpParametersByKind: MutableMap<String, RTCRtpParameters>) {

    // Transport local parameters, including DTLS parameteres.
    var transportLocalParameters: Any? = null
        set(value) {
            logger.debug("setTransportLocalParameters() [transportLocalParameters:$value]")
            field = value
        }

    // Transport remote parameters, including ICE parameters, ICE candidates and DTLS parameteres.
    var transportRemoteParameters: Any? = null
        set(value) {
            logger.debug("setTransportRemoteParameters() [transportRemoteParameters:$value]")
            field = value
        }

    // SDP global fields.
    var sdpGlobalFields = SdpGlobalFields()

    class SdpGlobalFields {
        var id = randomNumber()
        var version = 0
    }

//    updateTransportRemoteIceParameters(remoteIceParameters)
//    {
//        logger.debug(
//            'updateTransportRemoteIceParameters() [remoteIceParameters:%o]',
//            remoteIceParameters
//        );
//
//        this.transportRemoteParameters.iceParameters = remoteIceParameters;
//    }
}

class SendRemoteSdp(var rtpParametersByKind: MutableMap<String, RTCRtpParameters>) : RemoteSdp(rtpParametersByKind) {

//    fun createAnswerSdp(localSdpObj: SessionDescription) {
//        logger.debug("createAnswerSdp()")
//
//        if (this.transportLocalParameters == null)
//            throw Exception("no transport local parameters")
//        else if (this.transportRemoteParameters == null)
//            throw Exception("no transport remote parameters")
//
//        var remoteIceParameters = this.transportRemoteParameters.iceParameters
//        var remoteIceCandidates = this.transportRemoteParameters.iceCandidates
//        var remoteDtlsParameters = this.transportRemoteParameters.dtlsParameters
//        var sdpObj = SessionDescription()
//        var bundleMids = localSdpObj.media
//            .filter {
//                it.mid != null
//            }.map {
//                it.mid as String
//            }
//
//        // Increase our SDP version.
//        this.sdpGlobalFields.version++
//
//        sdpObj.version = "0"
//        sdpObj.origin = SessionDescription.Origin(
//            address = "0.0.0.0",
//            ipVer = 4,
//            netType = "IN",
//            sessionId = this.sdpGlobalFields.id,
//            sessionVersion = this.sdpGlobalFields.version,
//            username = "mediasoup-client"
//        )
//        sdpObj.name = "-"
//        sdpObj.timing = SessionDescription.Timing(start = 0, stop = 0)
//        sdpObj.icelite = if (remoteIceParameters.iceLite != null) "ice-lite" else null
//        sdpObj.msidSemantic = MediaAttributes.MsidSemantic(
//            semantic = "WMS",
//            token = "*"
//        )
//        if (bundleMids.isNotEmpty()) {
//            sdpObj.groups = arrayListOf(
//                SessionAttributes.Group(
//                    type = "BUNDLE",
//                    mids = bundleMids.joinToString(" ")
//                )
//            )
//        }
//
//        sdpObj.media = arrayListOf()
//
//        // NOTE: We take the latest fingerprint.
//        var numFingerprints = remoteDtlsParameters.fingerprints.length
//        sdpObj.fingerprint = SharedAttributes.Fingerprint(
//            type = remoteDtlsParameters.fingerprints[numFingerprints - 1].algorithm,
//            hash = remoteDtlsParameters.fingerprints[numFingerprints - 1].value
//        )
//
//        for (localMediaObj in localSdpObj.media) {
//            val closed = localMediaObj.direction == "inactive"
//            val kind = localMediaObj.type
//            var codecs = this.rtpParametersByKind[kind]?.codecs
//            var headerExtensions = this.rtpParametersByKind[kind]?.headerExtensions
//            var remoteMediaObj = SessionDescription.Media()
//            remoteMediaObj.type = localMediaObj.type
//            remoteMediaObj.port = 7
//            remoteMediaObj.protocol = "RTP/SAVPF"
//            remoteMediaObj.connection = SharedDescriptionFields.Connection(ip = "127.0.0.1", version = 4)
//            remoteMediaObj.mid = localMediaObj.mid
//            remoteMediaObj.iceUfrag = remoteIceParameters.usernameFragment
//            remoteMediaObj.icePwd = remoteIceParameters.password
//            remoteMediaObj.candidates = arrayListOf()
//
//            for (candidate in remoteIceCandidates) {
//                // mediasoup does not support non rtcp-mux so candidates component is always RTP (1).
//                var candidateObj = MediaAttributes.Candidate(
//                    component = 1,
//                    foundation = candidate.foundation,
//                    ip = candidate.ip,
//                    port = candidate.port,
//                    priority = candidate.priority,
//                    transport = candidate.protocol,
//                    type = candidate.type,
//                    tcptype = candidate.tcpType
////                    transport="",
////                    priority=0
//                )
//                remoteMediaObj.candidates.add(candidateObj)
//            }
//            remoteMediaObj.endOfCandidates = "end-of-candidates"
//
//            // Announce support for ICE renomination.
//            // https://tools.ietf.org/html/draft-thatcher-ice-renomination
//            remoteMediaObj.iceOptions = "renomination"
//
//            when (remoteDtlsParameters.role) {
//                "client" -> remoteMediaObj.setup = "active"
//                "server" -> remoteMediaObj.setup = "passive"
//            }
//
//            when (localMediaObj.direction) {
//                "sendrecv", "sendonly" -> remoteMediaObj.direction = "recvonly"
//                "recvonly", "inactive" -> remoteMediaObj.direction = "inactive"
//            }
//
//            remoteMediaObj.rtp = arrayListOf()
//            remoteMediaObj.rtcpFb = arrayListOf()
//            remoteMediaObj.fmtp = arrayListOf()
//
//            if (codecs != null) {
//                for (codec in codecs) {
//                    val rtp = MediaAttributes.Rtp(
//                        payload = codec.payloadType.toInt(),
//                        codec = codec.name,
//                        rate = codec.clockRate.toInt()
//                    )
//
//                    if (codec.channels > 1)
//                        rtp.encoding = codec.channels
//
//                    remoteMediaObj.rtp?.add(rtp)
//
//                    if (codec.parameters) {
//                        val paramFmtp =
//                            { payload: codec.payloadType,
//                              config: ''
//                            };
//
//                        for (const key of Object.keys(codec.parameters))
//                        {
//                            if (paramFmtp.config)
//                                paramFmtp.config += ';';
//
//                            paramFmtp.config += `${key}=${codec.parameters[key]}`;
//                        }
//
//                        if (paramFmtp.config)
//                            remoteMediaObj.fmtp.push(paramFmtp);
//                    }
//
//                    if (codec.rtcpFeedback) {
//                        for (const fb of codec.rtcpFeedback)
//                        {
//                            remoteMediaObj.rtcpFb.push(
//                                {
//                                    payload : codec.payloadType,
//                                    type    : fb.type,
//                                    subtype : fb.parameter || ''
//                                });
//                        }
//                    }
//                }
//            }
//
//            remoteMediaObj.payloads = codecs
//                .map((codec) => codec . payloadType)
//            .join(' ');
//
//            // NOTE: Firefox does not like a=extmap lines if a=inactive.
//            if (!closed) {
//                remoteMediaObj.ext = [];
//
//                for (const ext of headerExtensions)
//                {
//                    // Don't add a header extension if not present in the offer.
//                    const matchedLocalExt =(localMediaObj.ext || [])
//                            .find((localExt) => localExt . uri === ext . uri);
//
//                    if (!matchedLocalExt)
//                        continue;
//
//                    remoteMediaObj.ext.push(
//                        { uri: ext.uri,
//                          value: ext.id
//                        });
//                }
//            }
//
//            // Simulcast.
//            if (localMediaObj.simulcast_03) {
//                // eslint-disable-next-line camelcase
//                remoteMediaObj.simulcast_03 =
//                        {
//                            value : localMediaObj.simulcast_03.value.replace(/send/g, 'recv')
//                        };
//
//                remoteMediaObj.rids = [];
//
//                for (const rid of localMediaObj.rids || [])
//                {
//                    if (rid.direction !== 'send')
//                        continue;
//
//                    remoteMediaObj.rids.push(
//                        { id: rid.id,
//                          direction: 'recv'
//                        });
//                }
//            }
//
//            remoteMediaObj.rtcpMux = 'rtcp-mux';
//            remoteMediaObj.rtcpRsize = 'rtcp-rsize';
//
//            // Push it.
//            sdpObj.media.push(remoteMediaObj);
//        }
//
//        const sdp = sdpTransform . write (sdpObj);
//
//        return sdp;
//    }
}

var rtpCodecName: String? = null
var RTCRtpCodecParameters.name: String?
    get() = rtpCodecName
    set(value) {
        rtpCodecName = value
    }

var rtpCodecParameters: Map<String, Any>? = null
var RTCRtpCodecParameters.parameters: Map<String, Any>?
    get() = rtpCodecParameters
    set(value) {
        rtpCodecParameters = value
    }