package com.versatica.mediasoup.sdp

import org.webrtc.*

/**
 * @author wolfhan
 */
class RTCAnswerOptions : RTCOfferAnswerOptions()

class RTCCertificateExpiration {
    var expires: Number? = null
}

class RTCConfiguration {
    var bundlePolicy: RTCBundlePolicy? = null
    var certificates: MutableCollection<RTCCertificate>? = null
    var iceCandidatePoolSize: Int? = null
    var iceServers: MutableCollection<RTCIceServer>? = null
    var iceTransportPolicy: RTCIceTransportPolicy? = null
    var peerIdentity: String? = null
    var rtcpMuxPolicy: RTCRtcpMuxPolicy? = null
}

interface RTCCertificate {
    val expires: Number
    fun getFingerprints(): MutableCollection<RTCDtlsFingerprint>
}


class RTCDTMFToneChangeEventInit : EventInit() {
    lateinit var tone: String
}

class RTCDataChannelEventInit : EventInit() {
    //    lateinit var channel: RTCDataChannel
    lateinit var channel: DataChannel
}

class RTCDataChannelInit {
    var id: Number? = null
    var maxPacketLifeTime: Number? = null
    var maxRetransmits: Number? = null
    var negotiated: Boolean? = null
    var ordered: Boolean? = null
    var priority: RTCPriorityType? = null
    var protocol: String? = null
}

data class RTCDtlsFingerprint(
    var algorithm: String? = null,
    var value: String? = null
)

data class RTCDtlsParameters(
    var fingerprints: MutableList<RTCDtlsFingerprint>? = null,
    var role: RTCDtlsRole? = null
)

class RTCErrorEventInit : EventInit() {
    //    var error: RTCError? = null
    var error: RTCError? = null
}

class RTCError : Error() {
    var errorDetail: String? = null
    var httpRequestStatusCode: Int? = null
    //    var message: String? = null
    var name: String? = null
    var receivedAlert: Number? = null
    var sctpCauseCode: Int? = null
    var sdpLineNumber: Int? = null
    var sentAlert: Number? = null
}

class RTCIceCandidateAttributes : RTCStats() {
    var addressSourceUrl: String? = null
    var candidateType: RTCStatsIceCandidateType? = null
    var ipAddress: String? = null
    var portNumber: Int? = null
    var priority: Int? = null
    var transport: String? = null
}

class RTCIceCandidateComplete {
}

class RTCIceCandidateDictionary {
    var foundation: String? = null
    var ip: String? = null
    var msMTurnSessionId: String? = null
    var port: Int? = null
    var priority: Int? = null
    var protocol: RTCIceProtocol? = null
    var relatedAddress: String? = null
    var relatedPort: Int? = null
    var tcpType: RTCIceTcpCandidateType? = null
    var type: RTCIceCandidateType? = null
}

class RTCIceCandidateInit {
    var candidate: String? = null
    var sdpMLineIndex: Int? = null
    var sdpMid: String? = null
    var usernameFragment: String? = null
}

class RTCIceCandidatePair {
    //    var local: RTCIceCandidate? = null
    var local: IceCandidate? = null
    //    var remote: RTCIceCandidate? = null
    var remote: IceCandidate? = null
}

class RTCIceCandidatePairStats : RTCStats() {
    var availableIncomingBitrate: Number? = null
    var availableOutgoingBitrate: Number? = null
    var bytesReceived: Int? = null
    var bytesSent: Int? = null
    var localCandidateId: String? = null
    var nominated: Boolean? = null
    var priority: Int? = null
    var readable: Boolean? = null
    var remoteCandidateId: String? = null
    var roundTripTime: Int? = null
    var state: RTCStatsIceCandidatePairState? = null
    var transportId: String? = null
    var writable: Boolean? = null
}

class RTCIceGatherOptions {
    var gatherPolicy: RTCIceGatherPolicy? = null
    var iceservers: MutableCollection<RTCIceServer>? = null
}

class RTCIceParameters {
    var password: String? = null
    var usernameFragment: String? = null
}

class RTCIceServer {
    //    var credential: String | RTCOAuthCredential?=null
    var credential: String? = null
    var credentialType: RTCIceCredentialType? = null
    //    var urls: String | MutableCollection<String>
    lateinit var urls: MutableCollection<String>
    var username: String? = null
}

class RTCIdentityProviderOptions {
    var peerIdentity: String? = null
    var protocol: String? = null
    var usernameHint: String? = null
}

class RTCInboundRTPStreamStats : RTCRTPStreamStats() {
    var bytesReceived: Int? = null
    var fractionLost: Number? = null
    var jitter: Number? = null
    var packetsLost: Int? = null
    var packetsReceived: Int? = null
}

class RTCMediaStreamTrackStats : RTCStats() {
    var audioLevel: Number? = null
    var echoReturnLoss: Number? = null
    var echoReturnLossEnhancement: Number? = null
    var frameHeight: Number? = null
    var frameWidth: Number? = null
    var framesCorrupted: Number? = null
    var framesDecoded: Number? = null
    var framesDropped: Number? = null
    var framesPerSecond: Number? = null
    var framesReceived: Number? = null
    var framesSent: Number? = null
    var remoteSource: Boolean? = null
    var ssrcIds: MutableCollection<String>? = null
    var trackIdentifier: String? = null
}

class RTCOAuthCredential {
    lateinit var accessToken: String
    lateinit var macKey: String
}

open class RTCOfferAnswerOptions(
    var voiceActivityDetection: Boolean? = false
)

class RTCOfferOptions : RTCOfferAnswerOptions() {
    var iceRestart: Boolean? = null
    var offerToReceiveAudio: Boolean? = null
    var offerToReceiveVideo: Boolean? = null
}

class RTCOutboundRTPStreamStats : RTCRTPStreamStats() {
    var bytesSent: Int? = null
    var packetsSent: Int? = null
    var roundTripTime: Int? = null
    var targetBitrate: Number? = null
}

class RTCPeerConnectionIceErrorEventInit : EventInit() {
    lateinit var errorCode: Number
    var hostCandidate: String? = null
    var statusText: String? = null
    var url: String? = null
}

class RTCPeerConnectionIceEventInit : EventInit() {
    //    var candidate: RTCIceCandidate? = null
    var candidate: IceCandidate? = null
    var url: String? = null
}

open class RTCRTPStreamStats : RTCStats() {
    var associateStatsId: String? = null
    var codecId: String? = null
    var firCount: Int? = null
    var isRemote: Boolean? = null
    var mediaTrackId: String? = null
    var mediaType: String? = null
    var nackCount: Int? = null
    var pliCount: Int? = null
    var sliCount: Int? = null
    var ssrc: String? = null
    var transportId: String? = null
}

class RTCRtcpFeedback {
    var parameter: String? = null
    var type: String? = null
}

data class RTCRtcpParameters(
    var cname: String? = null,
    var reducedSize: Boolean? = null
)

data class RTCRtpCapabilities(
    var codecs: MutableCollection<RTCRtpCodecCapability>,
    var headerExtensions: MutableCollection<RTCRtpHeaderExtensionCapability>,
    var fecMechanisms: MutableList<Any>? = null
)

open class RTCRtpCodecCapability(
    var channels: Int? = null,
    var clockRate: Number?,
    var mimeType: String,
    var sdpFmtpLine: String? = null,
    var name: String? = null,
    var kind: String? = null,
    var preferredPayloadType: Int? = null,
    var parameters: Map<String, Any>? = null,
    var rtcpFeedback: MutableCollection<RtcpFeedback>? = null
)

class RTCRtpCodecParameters(
    var channels: Int? = null,
    var clockRate: Number? = null,
    var mimeType: String,
    var payloadType: Int = 0,
    var sdpFmtpLine: String? = null,
    var name: String? = null,
    var parameters: Map<String, Any>? = null,
    var rtcpFeedback: List<RtcpFeedback>? = null
)

open class RTCRtpCodingParameters {
    var rid: String? = null
}

open class RTCRtpContributingSource {
    var audioLevel: Number? = null
    lateinit var source: Number
    lateinit var timestamp: Number
}

class RTCRtpDecodingParameters : RTCRtpCodingParameters()

class RTCRtpEncodingParameters : RTCRtpCodingParameters() {
    var active: Boolean? = null
    var codecPayloadType: Number? = null
    var dtx: RTCDtxStatus? = null
    var maxBitrate: Number? = null
    var maxFramerate: Number? = null
    var priority: RTCPriorityType? = null
    var ptime: Number? = null
    var scaleResolutionDownBy: Number? = null
}

class RTCRtpFecParameters {
    var mechanism: String? = null
    var ssrc: Number? = null
}

class RTCRtpHeaderExtension {
    var kind: String? = null
    var preferredEncrypt: Boolean? = null
    var preferredId: Number? = null
    var uri: String? = null
}

data class RTCRtpHeaderExtensionCapability(
    var uri: String? = null
) {
    var kind: String? = null
    var preferredId: Int? = null

    constructor(uri: String?, kind: String?, preferredId: Int?) : this(uri) {
        this.kind = kind
        this.preferredId = preferredId
    }
}

class RTCRtpHeaderExtensionParameters(
    var encrypted: Boolean? = null,
    var id: Int = 0,
    var uri: String = ""
)

open class RTCRtpParameters(
    var codecs: MutableList<RTCRtpCodecParameters> = arrayListOf(),
    var headerExtensions: MutableList<RTCRtpHeaderExtensionParameters> = arrayListOf(),
    var rtcp: RTCRtcpParameters? = null,
    var muxId: String? = null,
    var encodings: MutableList<RtpEncoding>? = null
)

//class RTCRtpReceiveParameters : RTCRtpParameters() {
//     override lateinit var encodings: MutableList<RTCRtpDecodingParameters>
//}

class RTCRtpRtxParameters {
    var ssrc: Number? = null
}

//class RTCRtpSendParameters : RTCRtpParameters() {
//    var degradationPreference: RTCDegradationPreference? = null
//    lateinit var encodings: MutableCollection<RTCRtpEncodingParameters>
//    lateinit var transactionId: String
//}

class RTCRtpSynchronizationSource : RTCRtpContributingSource() {
    var voiceActivityFlag: Boolean? = null
}

class RTCRtpTransceiverInit {
    var direction: RTCRtpTransceiverDirection? = null
    var sendEncodings: MutableCollection<RTCRtpEncodingParameters>? = null
    var streams: MutableCollection<MediaStream>? = null
}

class RTCRtpUnhandled {
    var muxId: String? = null
    var payloadType: Number? = null
    var ssrc: Number? = null
}

class RTCSessionDescriptionInit {
    var sdp: String? = null
    lateinit var type: RTCSdpType
}

class RTCSrtpKeyParam {
    var keyMethod: String? = null
    var keySalt: String? = null
    var lifetime: String? = null
    var mkiLength: Number? = null
    var mkiValue: Number? = null
}

class RTCSrtpSdesParameters {
    var cryptoSuite: String? = null
    var keyParams: MutableCollection<RTCSrtpKeyParam>? = null
    var sessionParams: MutableCollection<String>? = null
    var tag: Number? = null
}

class RTCSsrcRange {
    var max: Number? = null
    var min: Number? = null
}

open class RTCStats {
    lateinit var id: String
    lateinit var timestamp: Number
    lateinit var type: RTCStatsType
}

class RTCStatsEventInit : EventInit() {
    lateinit var report: RTCStatsReport
}

class RTCStatsReport {
}

class RTCTrackEventInit : EventInit() {
    //    lateinit var receiver: RTCRtpReceiver
    lateinit var receiver: RtpReceiver
    var streams: MutableCollection<MediaStream>? = null
    lateinit var track: MediaStreamTrack
    //    lateinit var transceiver: RTCRtpTransceiver
    lateinit var transceiver: RtpTransceiver
}

class RTCTransportStats : RTCStats() {
    var activeConnection: Boolean? = null
    var bytesReceived: Int? = null
    var bytesSent: Int? = null
    var localCertificateId: String? = null
    var remoteCertificateId: String? = null
    var rtcpTransportStatsId: String? = null
    var selectedCandidatePairId: String? = null
}

class RegistrationOptions {
    var scope: String? = null
    var type: WorkerType? = null
    var updateViaCache: ServiceWorkerUpdateViaCache? = null
}

open class EventInit {
    var bubbles: Boolean? = false
    var cancelable: Boolean? = false
    var composed: Boolean? = false
}

enum class RTCBundlePolicy(val v: String) {
    BALANCED("balanced"),
    MAX_COMPAT("max-compat"),
    MAX_BUNDLE("max-bundle")
}

enum class RTCDataChannelState(val v: String) {
    CONNECTING("connecting"),
    OPEN("open"),
    CLOSING("closing"),
    CLOSED("closed")
}

enum class RTCDegradationPreference(val v: String) {
    MAINTAIN_FRAMERATE("maintain-framerate"),
    MAINTAIN_RESOLUTION("maintain-resolution"),
    BALANCED("balanced")
}

enum class RTCDtlsRole(val v: String) {
    AUTO("auto"),
    CLIENT("client"),
    SERVER("server")
}

enum class RTCDtlsTransportState(val v: String) {
    NEW("new"),
    CONNECTING("connecting"),
    CONNECTED("connected"),
    CLOSED("closed"),
    FAILED("failed")
}

enum class RTCDtxStatus(val v: String) {
    DISABLED("disabled"),
    ENABLED("enabled")
}

enum class RTCErrorDetailType(val v: String) {
    DATA_CHANNEL_FAILURE("data-channel-failure"),
    DTLS_FAILURE("dtls-failure"),
    FINGERPRINT_FAILURE("fingerprint-failure"),
    IDP_BAD_SCRIPT_FAILURE("idp-bad-script-failure"),
    IDP_EXECUTION_FAILURE("idp-execution-failure"),
    IDP_LOAD_FAILURE("idp-load-failure"),
    IDP_NEED_LOGIN("idp-need-login"),
    IDP_TIMEOUT("idp-timeout"),
    IDP_TLS_FAILURE("idp-tls-failure"),
    IDP_TOKEN_EXPIRED("idp-token-expired"),
    IDP_TOKEN_INVALID("idp-token-invalid"),
    SCTP_FAILURE("sctp-failure"),
    SDP_SYNTAX_ERROR("sdp-syntax-error"),
    HARDWARE_ENCODER_NOT_AVAILABLE("hardware-encoder-not-available"),
    HARDWARE_ENCODER_ERROR("hardware-encoder-error")
}

enum class RTCIceCandidateType(val v: String) {
    HOST("host"),
    SRFLX("srflx"),
    PRFLX("prflx"),
    RELAY("relay")
}

enum class RTCIceComponent(val v: String) {
    RTP("rtp"),
    RTCP("rtcp")
}

enum class RTCIceConnectionState(val v: String) {
    NEW("new"),
    CHECKING("checking"),
    CONNECTED("connected"),
    COMPLETED("completed"),
    DISCONNECTED("disconnected"),
    FAILED("failed"),
    CLOSED("closed")
}

enum class RTCIceCredentialType(val v: String) {
    PASSWORD("password"),
    OAUTH("oauth")
}

enum class RTCIceGatherPolicy(val v: String) {
    ALL("all"),
    NOHOST("nohost"),
    RELAY("relay")
}

enum class RTCIceGathererState(val v: String) {
    NEW("new"),
    GATHERING("gathering"),
    COMPLETE("complete")
}

enum class RTCIceGatheringState(val v: String) {
    NEW("new"),
    GATHERING("gathering"),
    COMPLETE("complete")
}

enum class RTCIceProtocol(val v: String) {
    UDP("udp"),
    TCP("tcp")
}

enum class RTCIceRole(val v: String) {
    CONTROLLING("controlling"),
    CONTROLLED("controlled")
}

enum class RTCIceTcpCandidateType(val v: String) {
    ACTIVE("active"),
    PASSIVE("passive"),
    SO("so")
}

enum class RTCIceTransportPolicy(val v: String) {
    RELAY("relay"),
    ALL("all")
}

enum class RTCIceTransportState(val v: String) {
    NEW("new"),
    CHECKING("checking"),
    CONNECTED("connected"),
    COMPLETED("completed"),
    DISCONNECTED("disconnected"),
    FAILED("failed"),
    CLOSED("closed")
}

enum class RTCPeerConnectionState(val v: String) {
    NEW("new"),
    CONNECTING("connecting"),
    CONNECTED("connected"),
    DISCONNECTED("disconnected"),
    FAILED("failed"),
    CLOSED("closed")
}

enum class RTCPriorityType(val v: String) {
    VERY_LOW("very-low"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high")
}

enum class RTCRtcpMuxPolicy(val v: String) {
    NEGOTIATE("negotiate"),
    REQUIRE("require")
}

enum class RTCRtpTransceiverDirection(val v: String) {
    SENDRECV("sendrecv"),
    SENDONLY("sendonly"),
    RECVONLY("recvonly"),
    INACTIVE("inactive")
}

enum class RTCSctpTransportState(val v: String) {
    CONNECTING("connecting"),
    CONNECTED("connected"),
    CLOSED("closed")
}

enum class RTCSdpType(val v: String) {
    OFFER("offer"),
    PRANSWER("pranswer"),
    ANSWER("answer"),
    ROLLBACK("rollback")
}

enum class RTCSignalingState(val v: String) {
    STABLE("stable"),
    HAVE_LOCAL_OFFER("have-local-offer"),
    HAVE_REMOTE_OFFER("have-remote-offer"),
    HAVE_LOCAL_PRANSWER("have-local-pranswer"),
    HAVE_REMOTE_PRANSWER("have-remote-pranswer"),
    CLOSED("closed")
}

enum class RTCStatsIceCandidatePairState(val v: String) {
    FROZEN("frozen"),
    WAITING("waiting"),
    INPROGRESS("inprogress"),
    FAILED("failed"),
    SUCCEEDED("succeeded"),
    CANCELLED("cancelled")
}

enum class RTCStatsIceCandidateType(val v: String) {
    HOST("host"),
    SERVERREFLEXIVE("serverreflexive"),
    PEERREFLEXIVE("peerreflexive"),
    RELAYED("relayed")
}

enum class RTCStatsType(val v: String) {
    INBOUNDRTP("inboundrtp"),
    OUTBOUNDRTP("outboundrtp"),
    SESSION("session"),
    DATACHANNEL("datachannel"),
    TRACK("track"),
    TRANSPORT("transport"),
    CANDIDATEPAIR("candidatepair"),
    LOCALCANDIDATE("localcandidate"),
    REMOTECANDIDATE("remotecandidate")
}

enum class WorkerType(val v: String) {
    CLASSIC("classic"),
    MODULE("module")
}

enum class ServiceWorkerUpdateViaCache(val v: String) {
    IMPORTS("imports"),
    ALL("all"),
    NONE("none")
}