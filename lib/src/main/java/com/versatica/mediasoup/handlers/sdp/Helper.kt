package com.versatica.mediasoup.handlers.sdp

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

class RTCIceParameters(
    var password: String? = null,
    var usernameFragment: String? = null,
    var iceLite: Boolean? = null
)

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
    var reducedSize: Boolean? = null,
    var mux: Boolean? = null
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
    var uri: String? = null,
    var kind: String? = null,
    var preferredId: Int? = null,
    var preferredEncrypt: Boolean? = null
) {

    constructor(uri: String?) : this(uri, null, null, null)

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

enum class RTCBundlePolicy {
    balanced,
    `max-compat`,
    `max-bundle`
}

enum class RTCDataChannelState {
    connecting,
    open,
    closing,
    closed
}

enum class RTCDegradationPreference {
    `maintain-framerate`,
    `maintain-resolution`,
    balanced
}

enum class RTCDtlsRole {
    auto,
    client,
    server
}

enum class RTCDtlsTransportState {
    new,
    connecting,
    connected,
    closed,
    failed
}

enum class RTCDtxStatus {
    disabled,
    enabled
}

enum class RTCErrorDetailType {
    `data-channel-failure`,
    `dtls-failure`,
    `fingerprint-failure`,
    `idp-bad-script-failure`,
    `idp-execution-failure`,
    `idp-load-failure`,
    `idp-need-login`,
    `idp-timeout`,
    `idp-tls-failure`,
    `idp-token-expired`,
    `idp-token-invalid`,
    `sctp-failure`,
    `sdp-syntax-error`,
    `hardware-encoder-not-available`,
    `hardware-encoder-error`
}

enum class RTCIceCandidateType {
    host,
    srflx,
    prflx,
    relay
}

enum class RTCIceComponent {
    rtp,
    rtcp
}

enum class RTCIceConnectionState {
    new,
    checking,
    connected,
    completed,
    disconnected,
    failed,
    closed
}

enum class RTCIceCredentialType {
    password,
    oauth
}

enum class RTCIceGatherPolicy {
    all,
    nohost,
    relay
}

enum class RTCIceGathererState {
    new,
    gathering,
    complete
}

enum class RTCIceGatheringState {
    new,
    gathering,
    complete
}

enum class RTCIceProtocol {
    udp,
    tcp
}

enum class RTCIceRole {
    controlling,
    controlled
}

enum class RTCIceTcpCandidateType {
    active,
    passive,
    so
}

enum class RTCIceTransportPolicy {
    relay,
    all
}

enum class RTCIceTransportState {
    new,
    checking,
    connected,
    completed,
    disconnected,
    failed,
    closed
}

enum class RTCPeerConnectionState {
    new,
    connecting,
    connected,
    disconnected,
    failed,
    closed
}

enum class RTCPriorityType {
    `very-low`,
    low,
    medium,
    high
}

enum class RTCRtcpMuxPolicy {
    negotiate,
    require
}

enum class RTCRtpTransceiverDirection {
    sendrecv,
    sendonly,
    recvonly,
    inactive
}

enum class RTCSctpTransportState {
    connecting,
    connected,
    closed
}

enum class RTCSdpType {
    offer,
    pranswer,
    answer,
    rollback
}

enum class RTCSignalingState {
    stable,
    `have-local-offer`,
    `have-remote-offer`,
    `have-local-pranswer`,
    `have-remote-pranswer`,
    closed
}

enum class RTCStatsIceCandidatePairState {
    frozen,
    waiting,
    inprogress,
    failed,
    succeeded,
    cancelled
}

enum class RTCStatsIceCandidateType {
    host,
    serverreflexive,
    peerreflexive,
    relayed
}

enum class RTCStatsType {
    inboundrtp,
    outboundrtp,
    session,
    datachannel,
    track,
    transport,
    candidatepair,
    localcandidate,
    remotecandidate
}

enum class WorkerType {
    classic,
    module
}

enum class ServiceWorkerUpdateViaCache {
    imports,
    all,
    none
}