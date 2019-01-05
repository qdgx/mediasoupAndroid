package com.versatica.mediasoup.webrtc;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;
import org.webrtc.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class WebRTCModule {
    static final String TAG = WebRTCModule.class.getCanonicalName();

    PeerConnectionFactory mFactory;
    private final SparseArray<PeerConnectionObserver> mPeerConnectionObservers;
    final Map<String, MediaStream> localStreams;

    /**
     * The implementation of {@code getUserMedia} extracted into a separate file
     * in order to reduce complexity and to (somewhat) separate concerns.
     */
    private GetUserMediaImpl getUserMediaImpl;

    //csb single instance
    private static WebRTCModule instance;

//    public WebRTCModule(ReactApplicationContext reactContext) {
//        super(reactContext);
//
//        mPeerConnectionObservers = new SparseArray<>();
//        localStreams = new HashMap<>();
//
//        ThreadUtils.runOnExecutor(() -> initAsync());
//    }

    public static WebRTCModule getInstance(Context context){
        if (instance == null){
            instance = new WebRTCModule(context);
        }
        return instance;
    }

    //csb single instance
    private WebRTCModule(Context reactContext) {
        mPeerConnectionObservers = new SparseArray<>();
        localStreams = new HashMap<>();

        ThreadUtils.runOnExecutor(() -> initAsync(reactContext));
    }


    /**
     * Invoked asynchronously to initialize this {@code WebRTCModule} instance.
     */
    private void initAsync(Context reactContext) {
        //csb
        //ReactApplicationContext reactContext = getReactApplicationContext();

        // Initialize EGL contexts required for HW acceleration.
        EglBase.Context eglContext = EglUtils.getRootEglBaseContext();

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(reactContext)
                    //csb
                //.setEnableVideoHwAcceleration(eglContext != null)
                .createInitializationOptions());

        VideoEncoderFactory encoderFactory;
        VideoDecoderFactory decoderFactory;

        if (eglContext != null) {
            encoderFactory
                = new DefaultVideoEncoderFactory(
                    eglContext,
                    /* enableIntelVp8Encoder */ true,
                    /* enableH264HighProfile */ false);
            decoderFactory = new DefaultVideoDecoderFactory(eglContext);
        } else {
            encoderFactory = new SoftwareVideoEncoderFactory();
            decoderFactory = new SoftwareVideoDecoderFactory();
        }

        mFactory
            = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();

        if (eglContext != null) {
            //csb
            //mFactory.setVideoHwAccelerationOptions(eglContext, eglContext);
        }

        getUserMediaImpl = new GetUserMediaImpl(this, reactContext);
    }


    private PeerConnection getPeerConnection(int id) {
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        return (pco == null) ? null : pco.getPeerConnection();
    }

    void sendEvent(String eventName, @Nullable HashMap params) {
        //csb
//        getReactApplicationContext()
//                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
//                .emit(eventName, params);
    }

    private PeerConnection.IceServer createIceServer(String url) {
        return PeerConnection.IceServer.builder(url).createIceServer();
    }

    private PeerConnection.IceServer createIceServer(String url, String username, String credential) {
        return PeerConnection.IceServer.builder(url)
            .setUsername(username)
            .setPassword(credential)
            .createIceServer();
    }

    //csb
//    private List<PeerConnection.IceServer> createIceServers(ReadableArray iceServersArray) {
//        final int size = (iceServersArray == null) ? 0 : iceServersArray.size();
//        List<PeerConnection.IceServer> iceServers = new ArrayList<>(size);
//        for (int i = 0; i < size; i++) {
//            ReadableMap iceServerMap = iceServersArray.getMap(i);
//            boolean hasUsernameAndCredential = iceServerMap.hasKey("username") && iceServerMap.hasKey("credential");
//            if (iceServerMap.hasKey("url")) {
//                if (hasUsernameAndCredential) {
//                    iceServers.add(createIceServer(iceServerMap.getString("url"), iceServerMap.getString("username"), iceServerMap.getString("credential")));
//                } else {
//                    iceServers.add(createIceServer(iceServerMap.getString("url")));
//                }
//            } else if (iceServerMap.hasKey("urls")) {
//                switch (iceServerMap.getType("urls")) {
//                    case String:
//                        if (hasUsernameAndCredential) {
//                            iceServers.add(createIceServer(iceServerMap.getString("urls"), iceServerMap.getString("username"), iceServerMap.getString("credential")));
//                        } else {
//                            iceServers.add(createIceServer(iceServerMap.getString("urls")));
//                        }
//                        break;
//                    case Array:
//                        ReadableArray urls = iceServerMap.getArray("urls");
//                        for (int j = 0; j < urls.size(); j++) {
//                            String url = urls.getString(j);
//                            if (hasUsernameAndCredential) {
//                                iceServers.add(createIceServer(url,iceServerMap.getString("username"), iceServerMap.getString("credential")));
//                            } else {
//                                iceServers.add(createIceServer(url));
//                            }
//                        }
//                        break;
//                }
//            }
//        }
//        return iceServers;
//    }

    private List<PeerConnection.IceServer> createIceServers(ArrayList iceServersArray) {
        final int size = (iceServersArray == null) ? 0 : iceServersArray.size();
        List<PeerConnection.IceServer> iceServers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            HashMap iceServerMap = (HashMap) iceServersArray.get(i);
            boolean hasUsernameAndCredential = iceServerMap.containsKey("username") && iceServerMap.containsKey("credential");
            if (iceServerMap.containsKey("url")) {
                if (hasUsernameAndCredential) {
                    iceServers.add(createIceServer((String) iceServerMap.get("url"), (String)iceServerMap.get("username"), (String)iceServerMap.get("credential")));
                } else {
                    iceServers.add(createIceServer((String)iceServerMap.get("url")));
                }
            } else if (iceServerMap.containsKey("urls")) {
                if(String.class.isInstance(iceServerMap.get("urls"))){
                    if (hasUsernameAndCredential) {
                        iceServers.add(createIceServer((String)iceServerMap.get("urls"), (String)iceServerMap.get("username"), (String)iceServerMap.get("credential")));
                    } else {
                        iceServers.add(createIceServer((String)iceServerMap.get("urls")));
                    }
                }else if(ArrayList.class.isInstance(iceServerMap.get("urls"))){
                    ArrayList urls = (ArrayList) iceServerMap.get("urls");
                    for (int j = 0; j < urls.size(); j++) {
                        String url = (String) urls.get(j);
                        if (hasUsernameAndCredential) {
                            iceServers.add(createIceServer(url,(String)iceServerMap.get("username"), (String)iceServerMap.get("credential")));
                        } else {
                            iceServers.add(createIceServer(url));
                        }
                    }
                }
            }
        }
        return iceServers;
    }

    //csb
//    private PeerConnection.RTCConfiguration parseRTCConfiguration(ReadableMap map) {
//        ReadableArray iceServersArray = null;
//        if (map != null) {
//            iceServersArray = map.getArray("iceServers");
//        }
//        List<PeerConnection.IceServer> iceServers = createIceServers(iceServersArray);
//        PeerConnection.RTCConfiguration conf = new PeerConnection.RTCConfiguration(iceServers);
//        if (map == null) {
//            return conf;
//        }
//
//        // iceTransportPolicy (public api)
//        if (map.hasKey("iceTransportPolicy")
//                && map.getType("iceTransportPolicy") == ReadableType.String) {
//            final String v = map.getString("iceTransportPolicy");
//            if (v != null) {
//                switch (v) {
//                case "all": // public
//                    conf.iceTransportsType = PeerConnection.IceTransportsType.ALL;
//                    break;
//                case "relay": // public
//                    conf.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
//                    break;
//                case "nohost":
//                    conf.iceTransportsType = PeerConnection.IceTransportsType.NOHOST;
//                    break;
//                case "none":
//                    conf.iceTransportsType = PeerConnection.IceTransportsType.NONE;
//                    break;
//                }
//            }
//        }
//
//        // bundlePolicy (public api)
//        if (map.hasKey("bundlePolicy")
//                && map.getType("bundlePolicy") == ReadableType.String) {
//            final String v = map.getString("bundlePolicy");
//            if (v != null) {
//                switch (v) {
//                case "balanced": // public
//                    conf.bundlePolicy = PeerConnection.BundlePolicy.BALANCED;
//                    break;
//                case "max-compat": // public
//                    conf.bundlePolicy = PeerConnection.BundlePolicy.MAXCOMPAT;
//                    break;
//                case "max-bundle": // public
//                    conf.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
//                    break;
//                }
//            }
//        }
//
//        // rtcpMuxPolicy (public api)
//        if (map.hasKey("rtcpMuxPolicy")
//                && map.getType("rtcpMuxPolicy") == ReadableType.String) {
//            final String v = map.getString("rtcpMuxPolicy");
//            if (v != null) {
//                switch (v) {
//                case "negotiate": // public
//                    conf.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE;
//                    break;
//                case "require": // public
//                    conf.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
//                    break;
//                }
//            }
//        }
//
//        // FIXME: peerIdentity of type DOMString (public api)
//        // FIXME: certificates of type sequence<RTCCertificate> (public api)
//
//        // iceCandidatePoolSize of type unsigned short, defaulting to 0
//        if (map.hasKey("iceCandidatePoolSize")
//                && map.getType("iceCandidatePoolSize") == ReadableType.Number) {
//            final int v = map.getInt("iceCandidatePoolSize");
//            if (v > 0) {
//                conf.iceCandidatePoolSize = v;
//            }
//        }
//
//        // === below is private api in webrtc ===
//
//        // tcpCandidatePolicy (private api)
//        if (map.hasKey("tcpCandidatePolicy")
//                && map.getType("tcpCandidatePolicy") == ReadableType.String) {
//            final String v = map.getString("tcpCandidatePolicy");
//            if (v != null) {
//                switch (v) {
//                case "enabled":
//                    conf.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
//                    break;
//                case "disabled":
//                    conf.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
//                    break;
//                }
//            }
//        }
//
//        // candidateNetworkPolicy (private api)
//        if (map.hasKey("candidateNetworkPolicy")
//                && map.getType("candidateNetworkPolicy") == ReadableType.String) {
//            final String v = map.getString("candidateNetworkPolicy");
//            if (v != null) {
//                switch (v) {
//                case "all":
//                    conf.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL;
//                    break;
//                case "low_cost":
//                    conf.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.LOW_COST;
//                    break;
//                }
//            }
//        }
//
//        // KeyType (private api)
//        if (map.hasKey("keyType")
//                && map.getType("keyType") == ReadableType.String) {
//            final String v = map.getString("keyType");
//            if (v != null) {
//                switch (v) {
//                case "RSA":
//                    conf.keyType = PeerConnection.KeyType.RSA;
//                    break;
//                case "ECDSA":
//                    conf.keyType = PeerConnection.KeyType.ECDSA;
//                    break;
//                }
//            }
//        }
//
//        // continualGatheringPolicy (private api)
//        if (map.hasKey("continualGatheringPolicy")
//                && map.getType("continualGatheringPolicy") == ReadableType.String) {
//            final String v = map.getString("continualGatheringPolicy");
//            if (v != null) {
//                switch (v) {
//                case "gather_once":
//                    conf.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
//                    break;
//                case "gather_continually":
//                    conf.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
//                    break;
//                }
//            }
//        }
//
//        // audioJitterBufferMaxPackets (private api)
//        if (map.hasKey("audioJitterBufferMaxPackets")
//                && map.getType("audioJitterBufferMaxPackets") == ReadableType.Number) {
//            final int v = map.getInt("audioJitterBufferMaxPackets");
//            if (v > 0) {
//                conf.audioJitterBufferMaxPackets = v;
//            }
//        }
//
//        // iceConnectionReceivingTimeout (private api)
//        if (map.hasKey("iceConnectionReceivingTimeout")
//                && map.getType("iceConnectionReceivingTimeout") == ReadableType.Number) {
//            final int v = map.getInt("iceConnectionReceivingTimeout");
//            conf.iceConnectionReceivingTimeout = v;
//        }
//
//        // iceBackupCandidatePairPingInterval (private api)
//        if (map.hasKey("iceBackupCandidatePairPingInterval")
//                && map.getType("iceBackupCandidatePairPingInterval") == ReadableType.Number) {
//            final int v = map.getInt("iceBackupCandidatePairPingInterval");
//            conf.iceBackupCandidatePairPingInterval = v;
//        }
//
//        // audioJitterBufferFastAccelerate (private api)
//        if (map.hasKey("audioJitterBufferFastAccelerate")
//                && map.getType("audioJitterBufferFastAccelerate") == ReadableType.Boolean) {
//            final boolean v = map.getBoolean("audioJitterBufferFastAccelerate");
//            conf.audioJitterBufferFastAccelerate = v;
//        }
//
//        // pruneTurnPorts (private api)
//        if (map.hasKey("pruneTurnPorts")
//                && map.getType("pruneTurnPorts") == ReadableType.Boolean) {
//            final boolean v = map.getBoolean("pruneTurnPorts");
//            conf.pruneTurnPorts = v;
//        }
//
//        // presumeWritableWhenFullyRelayed (private api)
//        if (map.hasKey("presumeWritableWhenFullyRelayed")
//                && map.getType("presumeWritableWhenFullyRelayed") == ReadableType.Boolean) {
//            final boolean v = map.getBoolean("presumeWritableWhenFullyRelayed");
//            conf.presumeWritableWhenFullyRelayed = v;
//        }
//
//        return conf;
//    }
    private PeerConnection.RTCConfiguration parseRTCConfiguration(HashMap map) {
        ArrayList iceServersArray = null;
        if (map != null) {
            iceServersArray = (ArrayList) map.get("iceServers");
        }
        List<PeerConnection.IceServer> iceServers = createIceServers(iceServersArray);
        PeerConnection.RTCConfiguration conf = new PeerConnection.RTCConfiguration(iceServers);
        if (map == null) {
            return conf;
        }

        // iceTransportPolicy (public api)
        if (map.containsKey("iceTransportPolicy")
                //csb
                //&& map.getType("iceTransportPolicy") == ReadableType.String
                && String.class.isInstance(map.get("iceTransportPolicy"))) {
            final String v = (String) map.get("iceTransportPolicy");
            if (v != null) {
                switch (v) {
                    case "all": // public
                        conf.iceTransportsType = PeerConnection.IceTransportsType.ALL;
                        break;
                    case "relay": // public
                        conf.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
                        break;
                    case "nohost":
                        conf.iceTransportsType = PeerConnection.IceTransportsType.NOHOST;
                        break;
                    case "none":
                        conf.iceTransportsType = PeerConnection.IceTransportsType.NONE;
                        break;
                }
            }
        }

        // bundlePolicy (public api)
        if (map.containsKey("bundlePolicy")
                //csb
                //&& map.getType("bundlePolicy") == ReadableType.String
                && String.class.isInstance(map.get("bundlePolicy"))) {
            final String v = (String) map.get("bundlePolicy");
            if (v != null) {
                switch (v) {
                    case "balanced": // public
                        conf.bundlePolicy = PeerConnection.BundlePolicy.BALANCED;
                        break;
                    case "max-compat": // public
                        conf.bundlePolicy = PeerConnection.BundlePolicy.MAXCOMPAT;
                        break;
                    case "max-bundle": // public
                        conf.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
                        break;
                }
            }
        }

        // rtcpMuxPolicy (public api)
        if (map.containsKey("rtcpMuxPolicy")
                //csb
                //&& map.getType("rtcpMuxPolicy") == ReadableType.String
                && String.class.isInstance(map.get("rtcpMuxPolicy"))) {
            final String v = (String) map.get("rtcpMuxPolicy");
            if (v != null) {
                switch (v) {
                    case "negotiate": // public
                        conf.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE;
                        break;
                    case "require": // public
                        conf.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
                        break;
                }
            }
        }

        // FIXME: peerIdentity of type DOMString (public api)
        // FIXME: certificates of type sequence<RTCCertificate> (public api)

        // iceCandidatePoolSize of type unsigned short, defaulting to 0
        if (map.containsKey("iceCandidatePoolSize")
                //&& map.getType("iceCandidatePoolSize") == ReadableType.Number
                && Number.class.isInstance(map.get("iceCandidatePoolSize"))) {
            final int v = (int) map.get("iceCandidatePoolSize");
            if (v > 0) {
                conf.iceCandidatePoolSize = v;
            }
        }

        // === below is private api in webrtc ===

        // tcpCandidatePolicy (private api)
        if (map.containsKey("tcpCandidatePolicy")
                //csb
                //&& map.getType("tcpCandidatePolicy") == ReadableType.String
                && String.class.isInstance(map.get("tcpCandidatePolicy"))) {
            final String v = (String) map.get("tcpCandidatePolicy");
            if (v != null) {
                switch (v) {
                    case "enabled":
                        conf.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
                        break;
                    case "disabled":
                        conf.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
                        break;
                }
            }
        }

        // candidateNetworkPolicy (private api)
        if (map.containsKey("candidateNetworkPolicy")
                //&& map.getType("candidateNetworkPolicy") == ReadableType.String
                && String.class.isInstance(map.get("candidateNetworkPolicy"))){
            final String v = (String) map.get("candidateNetworkPolicy");
            if (v != null) {
                switch (v) {
                    case "all":
                        conf.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL;
                        break;
                    case "low_cost":
                        conf.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.LOW_COST;
                        break;
                }
            }
        }

        // KeyType (private api)
        if (map.containsKey("keyType")
                //&& map.getType("keyType") == ReadableType.String
                && String.class.isInstance(map.get("keyType"))){
            final String v = (String) map.get("keyType");
            if (v != null) {
                switch (v) {
                    case "RSA":
                        conf.keyType = PeerConnection.KeyType.RSA;
                        break;
                    case "ECDSA":
                        conf.keyType = PeerConnection.KeyType.ECDSA;
                        break;
                }
            }
        }

        // continualGatheringPolicy (private api)
        if (map.containsKey("continualGatheringPolicy")
                //&& map.getType("continualGatheringPolicy") == ReadableType.String
                && String.class.isInstance(map.get("continualGatheringPolicy"))){
            final String v = (String) map.get("continualGatheringPolicy");
            if (v != null) {
                switch (v) {
                    case "gather_once":
                        conf.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
                        break;
                    case "gather_continually":
                        conf.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
                        break;
                }
            }
        }

        // audioJitterBufferMaxPackets (private api)
        if (map.containsKey("audioJitterBufferMaxPackets")
                //&& map.getType("audioJitterBufferMaxPackets") == ReadableType.Number
                && Number.class.isInstance(map.get("audioJitterBufferMaxPackets") )) {
            final int v = (int) map.get("audioJitterBufferMaxPackets");
            if (v > 0) {
                conf.audioJitterBufferMaxPackets = v;
            }
        }

        // iceConnectionReceivingTimeout (private api)
        if (map.containsKey("iceConnectionReceivingTimeout")
                //&& map.getType("iceConnectionReceivingTimeout") == ReadableType.Number
                && Number.class.isInstance(map.get("iceConnectionReceivingTimeout") )) {
            final int v = (int) map.get("iceConnectionReceivingTimeout");
            conf.iceConnectionReceivingTimeout = v;
        }

        // iceBackupCandidatePairPingInterval (private api)
        if (map.containsKey("iceBackupCandidatePairPingInterval")
                //&& map.getType("iceBackupCandidatePairPingInterval") == ReadableType.Number
                && Number.class.isInstance(map.get("iceBackupCandidatePairPingInterval") )) {
            final int v = (int) map.get("iceBackupCandidatePairPingInterval");
            conf.iceBackupCandidatePairPingInterval = v;
        }

        // audioJitterBufferFastAccelerate (private api)
        if (map.containsKey("audioJitterBufferFastAccelerate")
                //&& map.getType("audioJitterBufferFastAccelerate") == ReadableType.Boolean
                && Boolean.class.isInstance(map.get("iceBackupCandidatePairPingInterval") )) {
            final boolean v = (boolean) map.get("audioJitterBufferFastAccelerate");
            conf.audioJitterBufferFastAccelerate = v;
        }

        // pruneTurnPorts (private api)
        if (map.containsKey("pruneTurnPorts")
                //&& map.getType("pruneTurnPorts") == ReadableType.Boolean
                && Boolean.class.isInstance(map.get("pruneTurnPorts") )) {
            final boolean v = (boolean) map.get("pruneTurnPorts");
            conf.pruneTurnPorts = v;
        }

        // presumeWritableWhenFullyRelayed (private api)
        if (map.containsKey("presumeWritableWhenFullyRelayed")
                //&& map.getType("presumeWritableWhenFullyRelayed") == ReadableType.Boolean
                && Boolean.class.isInstance(map.get("presumeWritableWhenFullyRelayed") )) {
            final boolean v = (boolean) map.get("presumeWritableWhenFullyRelayed");
            conf.presumeWritableWhenFullyRelayed = v;
        }

        return conf;
    }

    //csb
    //@ReactMethod
//    public void peerConnectionInit(ReadableMap configuration, int id) {
//        PeerConnection.RTCConfiguration rtcConfiguration
//            = parseRTCConfiguration(configuration);
//
//        ThreadUtils.runOnExecutor(() ->
//            peerConnectionInitAsync(rtcConfiguration, id));
//    }
    public void peerConnectionInit(HashMap configuration, int id) {
        PeerConnection.RTCConfiguration rtcConfiguration
                = parseRTCConfiguration(configuration);

        ThreadUtils.runOnExecutor(() ->
                peerConnectionInitAsync(rtcConfiguration, id));
    }

    private void peerConnectionInitAsync(
            PeerConnection.RTCConfiguration configuration,
            int id) {
        PeerConnectionObserver observer = new PeerConnectionObserver(this, id);
        PeerConnection peerConnection
            = mFactory.createPeerConnection(configuration, observer);

        observer.setPeerConnection(peerConnection);
        mPeerConnectionObservers.put(id, observer);
    }

    MediaStream getStreamForReactTag(String streamReactTag) {
        MediaStream stream = localStreams.get(streamReactTag);

        if (stream == null) {
            for (int i = 0, size = mPeerConnectionObservers.size();
                    i < size;
                    i++) {
                PeerConnectionObserver pco
                    = mPeerConnectionObservers.valueAt(i);
                stream = pco.remoteStreams.get(streamReactTag);
                if (stream != null) {
                    break;
                }
            }
        }

        return stream;
    }

    MediaStreamTrack getLocalTrack(String trackId) {
        return getUserMediaImpl.getTrack(trackId);
    }

    private static MediaStreamTrack getLocalTrack(
            MediaStream localStream,
            String trackId) {
        for (AudioTrack track : localStream.audioTracks) {
            if (track.id().equals(trackId)) {
                return track;
            }
        }
        for (VideoTrack track : localStream.videoTracks) {
            if (track.id().equals(trackId)) {
                return track;
            }
        }
        return null;
    }

    /**
     * Parses a constraint set specified in the form of a JavaScript object into
     * a specific <tt>List</tt> of <tt>MediaConstraints.KeyValuePair</tt>s.
     *
     * @param src The constraint set in the form of a JavaScript object to
     * parse.
     * @param dst The <tt>List</tt> of <tt>MediaConstraints.KeyValuePair</tt>s
     * into which the specified <tt>src</tt> is to be parsed.
     */
    //csb
//    private void parseConstraints(
//            ReadableMap src,
//            List<MediaConstraints.KeyValuePair> dst) {
//        ReadableMapKeySetIterator keyIterator = src.keySetIterator();
//
//        while (keyIterator.hasNextKey()) {
//            String key = keyIterator.nextKey();
//            String value = ReactBridgeUtil.getMapStrValue(src, key);
//
//            dst.add(new MediaConstraints.KeyValuePair(key, value));
//        }
//    }
    private void parseConstraints(
            HashMap<String,String> src,
            List<MediaConstraints.KeyValuePair> dst) {
        for (String key : src.keySet()) {
            String value = src.get(key);
            dst.add(new MediaConstraints.KeyValuePair(key, value));
        }
    }

    /**
     * Parses mandatory and optional "GUM" constraints described by a specific
     * <tt>ReadableMap</tt>.
     *
     * @param constraints A <tt>ReadableMap</tt> which represents a JavaScript
     * object specifying the constraints to be parsed into a
     * <tt>MediaConstraints</tt> instance.
     * @return A new <tt>MediaConstraints</tt> instance initialized with the
     * mandatory and optional constraint keys and values specified by
     * <tt>constraints</tt>.
     */
    //csb
//    MediaConstraints parseMediaConstraints(ReadableMap constraints) {
//        MediaConstraints mediaConstraints = new MediaConstraints();
//
//        if (constraints.hasKey("mandatory")
//                && constraints.getType("mandatory") == ReadableType.Map) {
//            parseConstraints(
//                    constraints.getMap("mandatory"),
//                    mediaConstraints.mandatory);
//        } else {
//            Log.d(TAG, "mandatory constraints are not a map");
//        }
//
//        if (constraints.hasKey("optional")
//                && constraints.getType("optional") == ReadableType.Array) {
//            ReadableArray optional = constraints.getArray("optional");
//
//            for (int i = 0, size = optional.size(); i < size; i++) {
//                if (optional.getType(i) == ReadableType.Map) {
//                    parseConstraints(
//                            optional.getMap(i),
//                            mediaConstraints.optional);
//                }
//            }
//        } else {
//            Log.d(TAG, "optional constraints are not an array");
//        }
//
//        return mediaConstraints;
//    }
    MediaConstraints parseMediaConstraints(HashMap constraints) {
        MediaConstraints mediaConstraints = new MediaConstraints();

        if (constraints.containsKey("mandatory")
                //&& constraints.getType("mandatory") == ReadableType.Map
                && Map.class.isInstance(constraints.get("mandatory"))) {
            parseConstraints(
                    (HashMap<String, String>) constraints.get("mandatory"),
                    mediaConstraints.mandatory);
        } else {
            Log.d(TAG, "mandatory constraints are not a map");
        }

        if (constraints.containsKey("optional")
                //&& constraints.getType("optional") == ReadableType.Array
                && ArrayList.class.isInstance(constraints.get("optional"))) {
            ArrayList optional = (ArrayList) constraints.get("optional");

            for (int i = 0, size = optional.size(); i < size; i++) {
                //csb
                //if (optional.getType(i) == ReadableType.Map) {
                if (HashMap.class.isInstance(optional.get(i))) {
                    parseConstraints(
                            (HashMap<String, String>) optional.get(i),
                            mediaConstraints.optional);
                }
            }
        } else {
            Log.d(TAG, "optional constraints are not an array");
        }

        return mediaConstraints;
    }

    //csb
    //@ReactMethod
//    public void getUserMedia(ReadableMap constraints,
//                             Callback    successCallback,
//                             Callback    errorCallback) {
//        ThreadUtils.runOnExecutor(() ->
//            getUserMediaImpl.getUserMedia(constraints, successCallback, errorCallback));
//    }
    public void getUserMedia(HashMap constraints,
                             Callback    successCallback,
                             Callback    errorCallback) {
        ThreadUtils.runOnExecutor(() ->
            getUserMediaImpl.getUserMedia(constraints, successCallback, errorCallback));
    }

    //@ReactMethod
    public void mediaStreamRelease(String id) {
        ThreadUtils.runOnExecutor(() -> mediaStreamReleaseAsync(id));
    }

    private void mediaStreamReleaseAsync(String id) {
        MediaStream stream = localStreams.get(id);
        if (stream == null) {
            Log.d(TAG, "mediaStreamRelease() stream is null");
        } else {
            // XXX Copy the lists of audio and video tracks because we'll be
            // incrementally modifying them. Though a while loop with isEmpty()
            // is generally a clearer approach (employed by MediaStream), we'll
            // be searching through our own lists and these may (or may not) get
            // out of sync with MediaStream's lists which raises the risk of
            // entering infinite loops.
            List<MediaStreamTrack> tracks
                = new ArrayList<>(
                    stream.audioTracks.size() + stream.videoTracks.size());

            tracks.addAll(stream.audioTracks);
            tracks.addAll(stream.videoTracks);
            for (MediaStreamTrack track : tracks) {
                 mediaStreamTrackRelease(id, track.id());
            }

            localStreams.remove(id);

            // MediaStream.dispose() may be called without an exception only if
            // it's no longer added to any PeerConnection.
            for (int i = 0, size = mPeerConnectionObservers.size();
                    i < size;
                    i++) {
                mPeerConnectionObservers.valueAt(i).removeStream(stream);
            }

            stream.dispose();
        }
    }

    //@ReactMethod
    public void enumerateDevices(Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            callback.invoke(getUserMediaImpl.enumerateDevices()));
    }

    //@ReactMethod
    public void mediaStreamTrackRelease(String streamId, String trackId) {
        ThreadUtils.runOnExecutor(() ->
            mediaStreamTrackReleaseAsync(streamId, trackId));
    }

    private void mediaStreamTrackReleaseAsync(String streamId, String trackId) {
        MediaStream stream = localStreams.get(streamId);
        if (stream == null) {
            Log.d(TAG, "mediaStreamTrackRelease() stream is null");
            return;
        }
        MediaStreamTrack track = getLocalTrack(trackId);
        if (track == null) {
            // XXX The specified trackId may have already been stopped by
            // mediaStreamTrackStop().
            track = getLocalTrack(stream, trackId);
            if (track == null) {
                Log.d(
                    TAG,
                    "mediaStreamTrackRelease() No local MediaStreamTrack with id "
                        + trackId);
                return;
            }
        } else {
            mediaStreamTrackStop(trackId);
        }

        String kind = track.kind();
        if ("audio".equals(kind)) {
            stream.removeTrack((AudioTrack)track);
        } else if ("video".equals(kind)) {
            stream.removeTrack((VideoTrack)track);
        }
        track.dispose();
    }

    //@ReactMethod
    public void mediaStreamTrackSetEnabled(String id, boolean enabled) {
        ThreadUtils.runOnExecutor(() ->
            mediaStreamTrackSetEnabledAsync(id, enabled));
    }

    private void mediaStreamTrackSetEnabledAsync(String id, boolean enabled) {
        MediaStreamTrack track = getLocalTrack(id);
        if (track == null) {
            Log.d(TAG, "mediaStreamTrackSetEnabled() track is null");
            return;
        } else if (track.enabled() == enabled) {
            return;
        }
        track.setEnabled(enabled);
        getUserMediaImpl.mediaStreamTrackSetEnabled(id, enabled);
    }

    //@ReactMethod
    public void mediaStreamTrackStop(String trackId) {
        getUserMediaImpl.mediaStreamTrackStop(trackId);
    }

    //@ReactMethod
    public void mediaStreamTrackSwitchCamera(String id) {
        MediaStreamTrack track = getLocalTrack(id);
        if (track != null) {
            getUserMediaImpl.switchCamera(id);
        }
    }

    //csb
//    //@ReactMethod
//    public void peerConnectionSetConfiguration(ReadableMap configuration,
//                                               int id) {
//        ThreadUtils.runOnExecutor(() ->
//            peerConnectionSetConfigurationAsync(configuration, id));
//    }
    public void peerConnectionSetConfiguration(HashMap configuration,
                                               int id) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionSetConfigurationAsync(configuration, id));
    }

    private void peerConnectionSetConfigurationAsync(HashMap configuration,
                                                     int id) {
        PeerConnection peerConnection = getPeerConnection(id);
        if (peerConnection == null) {
            Log.d(TAG, "peerConnectionSetConfiguration() peerConnection is null");
            return;
        }
        peerConnection.setConfiguration(parseRTCConfiguration(configuration));
    }

    //csb
    //@ReactMethod
    public void peerConnectionAddStream(String streamId, int id) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionAddStreamAsync(streamId, id));
    }

    private void peerConnectionAddStreamAsync(String streamId, int id) {
        MediaStream mediaStream = localStreams.get(streamId);
        if (mediaStream == null) {
            Log.d(TAG, "peerConnectionAddStream() mediaStream is null");
            return;
        }
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if (pco == null || !pco.addStream(mediaStream)) {
            Log.e(TAG, "peerConnectionAddStream() failed");
        }
    }

    //csb
    //@ReactMethod
    public void peerConnectionRemoveStream(String streamId, int id) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionRemoveStreamAsync(streamId, id));
    }

    private void peerConnectionRemoveStreamAsync(String streamId, int id) {
        MediaStream mediaStream = localStreams.get(streamId);
        if (mediaStream == null) {
            Log.d(TAG, "peerConnectionRemoveStream() mediaStream is null");
            return;
        }
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if (pco == null || !pco.removeStream(mediaStream)) {
            Log.e(TAG, "peerConnectionRemoveStream() failed");
        }
    }

    //@ReactMethod
    public void peerConnectionCreateOffer(int id,
                                          HashMap constraints,
                                          Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionCreateOfferAsync(id, constraints, callback));
    }

    private void peerConnectionCreateOfferAsync(int id,
                                                HashMap constraints,
                                                final Callback callback) {
        PeerConnection peerConnection = getPeerConnection(id);

        if (peerConnection != null) {
            peerConnection.createOffer(new SdpObserver() {
                @Override
                public void onCreateFailure(String s) {
                    callback.invoke(false, s);
                }

                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    //csb
//                    WritableMap params = Arguments.createMap();
//                    params.putString("sdp", sdp.description);
//                    params.putString("type", sdp.type.canonicalForm());
//                    callback.invoke(true, params);
                    HashMap params = new HashMap();
                    params.put("sdp", sdp.description);
                    params.put("type", sdp.type.canonicalForm());
                    callback.invoke(true, params);
                }

                @Override
                public void onSetFailure(String s) {}

                @Override
                public void onSetSuccess() {}
            }, parseMediaConstraints(constraints));
        } else {
            Log.d(TAG, "peerConnectionCreateOffer() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
    }

    //csb
    //ReactMethod
    public void peerConnectionCreateAnswer(int id,
                                           HashMap constraints,
                                           Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionCreateAnswerAsync(id, constraints, callback));
    }

    private void peerConnectionCreateAnswerAsync(int id,
                                                 HashMap constraints,
                                                 final Callback callback) {
        PeerConnection peerConnection = getPeerConnection(id);

        if (peerConnection != null) {
            peerConnection.createAnswer(new SdpObserver() {
                @Override
                public void onCreateFailure(String s) {
                    callback.invoke(false, s);
                }

                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    //csb
//                    WritableMap params = Arguments.createMap();
//                    params.putString("sdp", sdp.description);
//                    params.putString("type", sdp.type.canonicalForm());
//                    callback.invoke(true, params);
                    HashMap params = new HashMap();
                    params.put("sdp", sdp.description);
                    params.put("type", sdp.type.canonicalForm());
                    callback.invoke(true, params);
                }

                @Override
                public void onSetFailure(String s) {}

                @Override
                public void onSetSuccess() {}
            }, parseMediaConstraints(constraints));
        } else {
            Log.d(TAG, "peerConnectionCreateAnswer() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
    }

    //csb
    //@ReactMethod
    public void peerConnectionSetLocalDescription(HashMap sdpMap,
                                                  int id,
                                                  Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionSetLocalDescriptionAsync(sdpMap, id, callback));
    }

    private void peerConnectionSetLocalDescriptionAsync(HashMap sdpMap,
                                                        int id,
                                                        final Callback callback) {
        PeerConnection peerConnection = getPeerConnection(id);

        Log.d(TAG, "peerConnectionSetLocalDescription() start");
        if (peerConnection != null) {
            SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm((String) sdpMap.get("type")),
                    (String)sdpMap.get("sdp")
            );

            peerConnection.setLocalDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                }

                @Override
                public void onSetSuccess() {
                    callback.invoke(true);
                }

                @Override
                public void onCreateFailure(String s) {
                }

                @Override
                public void onSetFailure(String s) {
                    callback.invoke(false, s);
                }
            }, sdp);
        } else {
            Log.d(TAG, "peerConnectionSetLocalDescription() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
        Log.d(TAG, "peerConnectionSetLocalDescription() end");
    }

    //@ReactMethod
    public void peerConnectionSetRemoteDescription(HashMap sdpMap,
                                                   int id,
                                                   Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionSetRemoteDescriptionAsync(sdpMap, id, callback));
    }

    private void peerConnectionSetRemoteDescriptionAsync(HashMap sdpMap,
                                                         int id,
                                                         final Callback callback) {
        PeerConnection peerConnection = getPeerConnection(id);

        Log.d(TAG, "peerConnectionSetRemoteDescription() start");
        if (peerConnection != null) {
            SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm((String) sdpMap.get("type")),
                    (String)sdpMap.get("sdp")
            );

            peerConnection.setRemoteDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(final SessionDescription sdp) {
                }

                @Override
                public void onSetSuccess() {
                    callback.invoke(true);
                }

                @Override
                public void onCreateFailure(String s) {
                }

                @Override
                public void onSetFailure(String s) {
                    callback.invoke(false, s);
                }
            }, sdp);
        } else {
            Log.d(TAG, "peerConnectionSetRemoteDescription() peerConnection is null");
            callback.invoke(false, "peerConnection is null");
        }
        Log.d(TAG, "peerConnectionSetRemoteDescription() end");
    }

    //@ReactMethod
    public void peerConnectionAddICECandidate(HashMap candidateMap,
                                              int id,
                                              Callback callback) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionAddICECandidateAsync(candidateMap, id, callback));
    }

    private void peerConnectionAddICECandidateAsync(HashMap candidateMap,
                                                    int id,
                                                    Callback callback) {
        boolean result = false;
        PeerConnection peerConnection = getPeerConnection(id);
        Log.d(TAG, "peerConnectionAddICECandidate() start");
        if (peerConnection != null) {
            IceCandidate candidate = new IceCandidate(
                    (String) candidateMap.get("sdpMid"),
                    (int)candidateMap.get("sdpMLineIndex"),
                    (String) candidateMap.get("candidate")
            );
            result = peerConnection.addIceCandidate(candidate);
        } else {
            Log.d(TAG, "peerConnectionAddICECandidate() peerConnection is null");
        }
        callback.invoke(result);
        Log.d(TAG, "peerConnectionAddICECandidate() end");
    }

    //@ReactMethod
    public void peerConnectionGetStats(String trackId, int id, Callback cb) {
        ThreadUtils.runOnExecutor(() ->
            peerConnectionGetStatsAsync(trackId, id, cb));
    }

    private void peerConnectionGetStatsAsync(String trackId,
                                             int id,
                                             Callback cb) {
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if (pco == null || pco.getPeerConnection() == null) {
            Log.d(TAG, "peerConnectionGetStats() peerConnection is null");
            cb.invoke(false, "PeerConnection ID not found");
        } else {
            pco.getStats(trackId, cb);
        }
    }

    //@ReactMethod
    public void peerConnectionClose(int id) {
        ThreadUtils.runOnExecutor(() -> peerConnectionCloseAsync(id));
    }

    private void peerConnectionCloseAsync(int id) {
        PeerConnectionObserver pco = mPeerConnectionObservers.get(id);
        if (pco == null || pco.getPeerConnection() == null) {
            Log.d(TAG, "peerConnectionClose() peerConnection is null");
        } else {
            pco.close();
            mPeerConnectionObservers.remove(id);
        }
    }

    //@ReactMethod
    public void createDataChannel(int peerConnectionId,
                                  String label,
                                  HashMap config) {
        ThreadUtils.runOnExecutor(() ->
            createDataChannelAsync(peerConnectionId, label, config));
    }

    private void createDataChannelAsync(int peerConnectionId,
                                        String label,
                                        HashMap config) {
        // Forward to PeerConnectionObserver which deals with DataChannels
        // because DataChannel is owned by PeerConnection.
        PeerConnectionObserver pco
            = mPeerConnectionObservers.get(peerConnectionId);
        if (pco == null || pco.getPeerConnection() == null) {
            Log.d(TAG, "createDataChannel() peerConnection is null");
        } else {
            pco.createDataChannel(label, config);
        }
    }

    //@ReactMethod
    public void dataChannelClose(int peerConnectionId, int dataChannelId) {
        ThreadUtils.runOnExecutor(() ->
            dataChannelCloseAsync(peerConnectionId, dataChannelId));
    }

    private void dataChannelCloseAsync(int peerConnectionId,
                                       int dataChannelId) {
        // Forward to PeerConnectionObserver which deals with DataChannels
        // because DataChannel is owned by PeerConnection.
        PeerConnectionObserver pco
            = mPeerConnectionObservers.get(peerConnectionId);
        if (pco == null || pco.getPeerConnection() == null) {
            Log.d(TAG, "dataChannelClose() peerConnection is null");
        } else {
            pco.dataChannelClose(dataChannelId);
        }
    }

   //@ReactMethod
    public void dataChannelSend(int peerConnectionId,
                                int dataChannelId,
                                String data,
                                String type) {
        ThreadUtils.runOnExecutor(() ->
            dataChannelSendAsync(peerConnectionId, dataChannelId, data, type));
    }

    private void dataChannelSendAsync(int peerConnectionId,
                                      int dataChannelId,
                                      String data,
                                      String type) {
        // Forward to PeerConnectionObserver which deals with DataChannels
        // because DataChannel is owned by PeerConnection.
        PeerConnectionObserver pco
            = mPeerConnectionObservers.get(peerConnectionId);
        if (pco == null || pco.getPeerConnection() == null) {
            Log.d(TAG, "dataChannelSend() peerConnection is null");
        } else {
            pco.dataChannelSend(dataChannelId, data, type);
        }
    }
}
