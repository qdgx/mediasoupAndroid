package com.versatica.mediasoup

import com.versatica.mediasoup.sdp.RTCIceServer
import com.versatica.mediasoup.sdp.RTCIceTransportPolicy

class TransportOptions {
    //Offer UDP ICE candidates.
    var udp: Boolean = true
    //Offer TCP ICE candidates.
    var tcp: Boolean  = true
    //Prefer IPv4 over IPv6 ICE candidates.
    var preferIPv4: Boolean  = false
    //Prefer IPv6 over IPv4 ICE candidates.
    var preferIPv6: Boolean  = false
    //Prefer UDP over TCP ICE candidates.
    var preferUdp: Boolean  = false
    //Prefer TCP over UDP ICE candidates.
    var preferTcp: Boolean  = false
}

class RoomOptions {
    //Timeout for mediasoup protocol sent requests (in milliseconds)
    var requestTimeout: Int = 10000
    //Options for created transports.
    var transportOptions: TransportOptions  = TransportOptions()
    //Array of TURN servers.
    var turnServers: List<RTCIceServer> = ArrayList()
    //The ICE transport policy.
    var iceTransportPolicy: String = RTCIceTransportPolicy.ALL.v
    var spy: Boolean  = false
}
