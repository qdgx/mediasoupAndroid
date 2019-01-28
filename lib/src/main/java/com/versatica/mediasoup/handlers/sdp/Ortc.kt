package com.versatica.mediasoup.handlers.sdp

/**
 * @author wolfhan
 */

object Ortc {

    /**
     * Generate extended RTP capabilities for sending and receiving.
     *
     * @param {RTCRtpCapabilities} localCaps - Local capabilities.
     * @param {RTCRtpCapabilities} remoteCaps - Remote capabilities.
     *
     * @return {RTCExtendedRtpCapabilities}
     */
    fun getExtendedRtpCapabilities(
        localCaps: RTCRtpCapabilities,
        remoteCaps: RTCRtpCapabilities
    ): RTCExtendedRtpCapabilities {
        val extendedCaps = RTCExtendedRtpCapabilities()

        // Match media codecs and keep the order preferred by remoteCaps.
        for (remoteCodec in remoteCaps.codecs) {
            // TODO: Ignore pseudo-codecs and feature codecs.
            if (remoteCodec.name == "rtx")
                continue

            val matchingLocalCodec = localCaps.codecs.find {
                matchCapCodecs(it, remoteCodec)
            }

            if (matchingLocalCodec != null) {
                val extendedCodec = RTCExtendedRtpCodecCapability(
                    name = remoteCodec.name,
                    mimeType = remoteCodec.mimeType,
                    kind = remoteCodec.kind,
                    clockRate = remoteCodec.clockRate,
                    sendPayloadType = matchingLocalCodec.preferredPayloadType,
                    sendRtxPayloadType = null,
                    recvPayloadType = remoteCodec.preferredPayloadType,
                    recvRtxPayloadType = null,
                    channels = remoteCodec.channels,
                    rtcpFeedback = reduceRtcpFeedback(matchingLocalCodec, remoteCodec),
                    parameters = remoteCodec.parameters
                )

                extendedCaps.codecs?.add(extendedCodec)
            }
        }

        // Match RTX codecs.
        val extendedCapsCodecs = extendedCaps.codecs
        if (extendedCapsCodecs != null) {
            for (extendedCodec in extendedCapsCodecs) {
                val matchingLocalRtxCodec = localCaps.codecs.find {
                    it.name.equals("rtx") && it.parameters?.get("apt") == extendedCodec.sendPayloadType
                }

                val matchingRemoteRtxCodec = remoteCaps.codecs.find {
                    it.name.equals("rtx") && it.parameters?.get("apt") == extendedCodec.recvPayloadType
                }

                if (matchingLocalRtxCodec != null && matchingRemoteRtxCodec != null) {
                    extendedCodec.sendRtxPayloadType = matchingLocalRtxCodec.preferredPayloadType
                    extendedCodec.recvRtxPayloadType = matchingRemoteRtxCodec.preferredPayloadType
                }
            }
        }

        // Match header extensions.
        for (remoteExt in remoteCaps.headerExtensions) {
            val matchingLocalExt = localCaps.headerExtensions.find {
                matchCapHeaderExtensions(it, remoteExt)
            }

            if (matchingLocalExt != null) {
                val extendedExt = RTCExtendedRtpHeaderExtensionCapability(
                    kind = remoteExt.kind,
                    uri = remoteExt.uri,
                    sendId = matchingLocalExt.preferredId,
                    recvId = remoteExt.preferredId
                )

                extendedCaps.headerExtensions?.add(extendedExt)
            }
        }

        return extendedCaps
    }

    /**
     * Generate RTP capabilities for receiving media based on the given extended
     * RTP capabilities.
     *
     * @param {RTCExtendedRtpCapabilities} extendedRtpCapabilities
     *
     * @return {RTCRtpCapabilities}
     */
    fun getRtpCapabilities(extendedRtpCapabilities: RTCExtendedRtpCapabilities): RTCRtpCapabilities {
        val capsCodecs = arrayListOf<RTCRtpCodecCapability>()
        val capsHeaderExtensions = arrayListOf<RTCRtpHeaderExtensionCapability>()
        val caps = RTCRtpCapabilities(capsCodecs, capsHeaderExtensions)

        val extendedRtpCapabilitiesCodecs = extendedRtpCapabilities.codecs
        if (extendedRtpCapabilitiesCodecs != null) {
            for (capCodec in extendedRtpCapabilitiesCodecs) {
                val codec = RTCRtpCodecCapability(
                    name = capCodec.name,
                    mimeType = capCodec.mimeType,
                    kind = capCodec.kind,
                    clockRate = capCodec.clockRate,
                    preferredPayloadType = capCodec.recvPayloadType,
                    channels = capCodec.channels,
                    rtcpFeedback = capCodec.rtcpFeedback,
                    parameters = capCodec.parameters
                )
                caps.codecs.add(codec)

                // Add RTX codec.
                if (capCodec.recvRtxPayloadType != null) {
                    val rtxCapCodec = RTCRtpCodecCapability(
                        name = "rtx",
                        mimeType = "${capCodec.kind}/rtx",
                        kind = capCodec.kind,
                        clockRate = capCodec.clockRate,
                        preferredPayloadType = capCodec.recvRtxPayloadType,
                        parameters = mapOf("apt" to (capCodec.recvPayloadType ?: 0))
                    )
                    caps.codecs.add(rtxCapCodec)
                }

                // TODO: In the future, we need to add FEC, CN, etc, codecs.
            }
        }

        val extendedRtpCapabilitiesHeaderExtensions = extendedRtpCapabilities.headerExtensions
        if (extendedRtpCapabilitiesHeaderExtensions != null) {
            for (capExt in extendedRtpCapabilitiesHeaderExtensions) {
                val ext = RTCRtpHeaderExtensionCapability(
                    kind = capExt.kind,
                    uri = capExt.uri,
                    preferredId = capExt.recvId
                )

                caps.headerExtensions.add(ext)
            }
        }

        caps.fecMechanisms = extendedRtpCapabilities.fecMechanisms

        return caps
    }

    /**
     * Get unsupported remote codecs.
     *
     * @param {RTCRtpCapabilities} remoteCaps - Remote capabilities.
     * @param {MutableList<Int>?} mandatoryCodecPayloadTypes - List of codec PT values.
     * @param {RTCExtendedRtpCapabilities} extendedRtpCapabilities
     *
     * @return {List<RTCRtpCodecCapability>}
     */
    fun getUnsupportedCodecs(
        remoteCaps: RTCRtpCapabilities,
        mandatoryCodecPayloadTypes: MutableList<Int>?,
        extendedRtpCapabilities: RTCExtendedRtpCapabilities
    ): List<RTCRtpCodecCapability> {
        // If not given just ignore.
        if (mandatoryCodecPayloadTypes == null)
            return emptyList()

        val unsupportedCodecs = arrayListOf<RTCRtpCodecCapability>()
        val remoteCodecs = remoteCaps.codecs
        val supportedCodecs = extendedRtpCapabilities.codecs

        for (pt in mandatoryCodecPayloadTypes) {
            val hasSupportedCodecs = supportedCodecs?.any {
                it.recvPayloadType == pt
            }
            if (hasSupportedCodecs != null && !hasSupportedCodecs) {
                val unsupportedCodec = remoteCodecs.find {
                    it.preferredPayloadType == pt
                } ?: throw Exception("mandatory codec PT $pt not found in remote codecs")

                unsupportedCodecs.add(unsupportedCodec)
            }
        }

        return unsupportedCodecs
    }

    /**
     * Whether media can be sent based on the given RTP capabilities.
     *
     * @param {String} kind
     * @param {RTCExtendedRtpCapabilities} extendedRtpCapabilities
     *
     * @return {Boolean}
     */
    fun canSend(kind: String, extendedRtpCapabilities: RTCExtendedRtpCapabilities): Boolean {
        return extendedRtpCapabilities.codecs?.any {
            it.kind == kind
        } ?: false
    }

    /**
     * Whether the given RTP parameters can be received with the given RTP
     * capabilities.
     *
     * @param {RTCRtpParameters} rtpParameters
     * @param {RTCExtendedRtpCapabilities} extendedRtpCapabilities
     *
     * @return {Boolean}
     */
    fun canReceive(rtpParameters: RTCRtpParameters, extendedRtpCapabilities: RTCExtendedRtpCapabilities): Boolean {
        if (rtpParameters.codecs.isEmpty())
            return false

        val firstMediaCodec = rtpParameters.codecs[0]

        return extendedRtpCapabilities.codecs?.any {
            it.recvPayloadType == firstMediaCodec.payloadType
        } ?: false
    }

    /**
     * Generate RTP parameters of the given kind for sending media.
     * Just the first media codec per kind is considered.
     * NOTE: muxId, encodings and rtcp fields are left empty.
     *
     * @param {kind} kind
     * @param {RTCExtendedRtpCapabilities} extendedRtpCapabilities
     *
     * @return {RTCRtpParameters}
     */
    fun getSendingRtpParameters(kind: String, extendedRtpCapabilities: RTCExtendedRtpCapabilities): RTCRtpParameters {
        val params = RTCRtpParameters(
            muxId = null,
            codecs = arrayListOf(),
            headerExtensions = arrayListOf(),
            encodings = arrayListOf(),
            rtcp = null
        )

        val extendedRtpCapabilitiesCodecs = extendedRtpCapabilities.codecs
        if (extendedRtpCapabilitiesCodecs != null) {
            for (capCodec in extendedRtpCapabilitiesCodecs) {
                if (capCodec.kind != kind)
                    continue

                val codec = RTCRtpCodecParameters(
                    name = capCodec.name,
                    mimeType = capCodec.mimeType,
                    clockRate = capCodec.clockRate,
                    payloadType = capCodec.sendPayloadType ?: 0,
                    channels = capCodec.channels,
                    rtcpFeedback = capCodec.rtcpFeedback,
                    parameters = capCodec.parameters
                )
                params.codecs.add(codec)

                // Add RTX codec.
                if (capCodec.sendRtxPayloadType != null) {
                    val rtxCodec = RTCRtpCodecParameters(
                        name = "rtx",
                        mimeType = "${capCodec.kind}/rtx",
                        clockRate = capCodec.clockRate,
                        payloadType = capCodec.sendRtxPayloadType ?: 0,
                        parameters = mapOf("apt" to (capCodec.sendPayloadType ?: 0))
                    )

                    params.codecs.add(rtxCodec)
                }

                // NOTE: We assume a single media codec plus an optional RTX codec for now.
                // TODO: In the future, we need to add FEC, CN, etc, codecs.
                break
            }
        }

        val extendedRtpCapabilitiesHeaderExtensions = extendedRtpCapabilities.headerExtensions
        if (extendedRtpCapabilitiesHeaderExtensions != null) {
            for (capExt in extendedRtpCapabilitiesHeaderExtensions) {
                if (capExt.kind != null && capExt.kind != kind)
                    continue

                val ext = RTCRtpHeaderExtensionParameters(
                    uri = capExt.uri ?: "",
                    id = capExt.sendId ?: 0
                )

                params.headerExtensions.add(ext)
            }
        }

        return params
    }

    /**
     * Generate RTP parameters of the given kind for receiving media.
     * All the media codecs per kind are considered. This is useful for generating
     * a SDP remote offer.
     * NOTE: muxId, encodings and rtcp fields are left empty.
     *
     * @param {String} kind
     * @param {RTCExtendedRtpCapabilities} extendedRtpCapabilities
     *
     * @return {RTCRtpParameters}
     */
    fun getReceivingFullRtpParameters(
        kind: String,
        extendedRtpCapabilities: RTCExtendedRtpCapabilities
    ): RTCRtpParameters {
        val params = RTCRtpParameters(
            muxId = null,
            codecs = arrayListOf(),
            headerExtensions = arrayListOf(),
            encodings = arrayListOf(),
            rtcp = null
        )

        val extendedRtpCapabilitiesCodecs = extendedRtpCapabilities.codecs
        if (extendedRtpCapabilitiesCodecs != null) {
            for (capCodec in extendedRtpCapabilitiesCodecs) {
                if (capCodec.kind != kind)
                    continue

                val codec =
                    RTCRtpCodecParameters(
                        name = capCodec.name,
                        mimeType = capCodec.mimeType,
                        clockRate = capCodec.clockRate,
                        payloadType = capCodec.recvPayloadType ?: 0,
                        channels = capCodec.channels,
                        rtcpFeedback = capCodec.rtcpFeedback,
                        parameters = capCodec.parameters
                    )
                params.codecs.add(codec)

                // Add RTX codec.
                if (capCodec.recvRtxPayloadType != null) {
                    val rtxCodec =
                        RTCRtpCodecParameters(
                            name = "rtx",
                            mimeType = "${capCodec.kind}/rtx",
                            clockRate = capCodec.clockRate,
                            payloadType = capCodec.recvRtxPayloadType ?: 0,
                            parameters = mapOf("apt" to (capCodec.recvPayloadType ?: 0))
                        )
                    params.codecs.add(rtxCodec)
                }

                // TODO: In the future, we need to add FEC, CN, etc, codecs.
            }

        }

        val extendedRtpCapabilitiesHeaderExtensions = extendedRtpCapabilities.headerExtensions
        if (extendedRtpCapabilitiesHeaderExtensions != null) {
            for (capExt in extendedRtpCapabilitiesHeaderExtensions) {
                if (capExt.kind != null && capExt.kind != kind)
                    continue

                val ext =
                    RTCRtpHeaderExtensionParameters(
                        uri = capExt.uri ?: "",
                        id = capExt.recvId ?: 0
                    )

                params.headerExtensions.add(ext)
            }
        }

        return params
    }

    fun matchCapCodecs(aCodec: RTCRtpCodecCapability, bCodec: RTCRtpCodecCapability): Boolean {
        val aMimeType = aCodec.mimeType.toLowerCase()
        val bMimeType = bCodec.mimeType.toLowerCase()

        if (aMimeType != bMimeType)
            return false

        if (aCodec.clockRate != bCodec.clockRate)
            return false

        if (aCodec.channels != bCodec.channels)
            return false

        // Match H264 parameters.
        if (aMimeType == "video/h264") {
            val aPacketizationMode = aCodec.parameters?.get("packetization-mode") ?: 0
            val bPacketizationMode = bCodec.parameters?.get("packetization-mode") ?: 0

            if (aPacketizationMode != bPacketizationMode)
                return false
        }

        return true
    }

    fun matchCapHeaderExtensions(
        aExt: RTCRtpHeaderExtensionCapability,
        bExt: RTCRtpHeaderExtensionCapability
    ): Boolean {
        if (aExt.kind != null && bExt.kind != null && aExt.kind != bExt.kind)
            return false

        if (aExt.uri != bExt.uri)
            return false

        return true
    }

    fun reduceRtcpFeedback(codecA: RTCRtpCodecCapability, codecB: RTCRtpCodecCapability): MutableList<RtcpFeedback>? {
        val reducedRtcpFeedback = arrayListOf<RtcpFeedback>()

        val rtcpFeedback = codecA.rtcpFeedback
        if (rtcpFeedback != null) {
            for (aFb in rtcpFeedback) {
                val matchingBFb = codecB.rtcpFeedback?.find {
                    it.type == aFb.type && it.parameter == aFb.parameter
                }

                if (matchingBFb != null)
                    reducedRtcpFeedback.add(matchingBFb)
            }
        }

        return reducedRtcpFeedback
    }
}

data class RTCExtendedRtpCapabilities(
    var codecs: MutableList<RTCExtendedRtpCodecCapability>? = mutableListOf(),
    var headerExtensions: MutableList<RTCExtendedRtpHeaderExtensionCapability>? = mutableListOf(),
    var fecMechanisms: MutableList<Any>? = mutableListOf()
)

data class RTCExtendedRtpCodecCapability(
    var name: String?,
    var mimeType: String,
    var kind: String?,
    var clockRate: Number?,
    var sendPayloadType: Int?,
    var sendRtxPayloadType: Int?,
    var recvPayloadType: Int?,
    var recvRtxPayloadType: Int?,
    var channels: Int?,
    var rtcpFeedback: MutableList<RtcpFeedback>?,
    var parameters: Map<String, Any>?
)

data class RTCExtendedRtpHeaderExtensionCapability(
    var kind: String?,
    var uri: String?,
    var sendId: Int?,
    var recvId: Int?
)