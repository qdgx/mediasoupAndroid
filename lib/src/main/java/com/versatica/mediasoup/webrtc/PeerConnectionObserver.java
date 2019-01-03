package com.versatica.mediasoup.webrtc;

import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import org.webrtc.*;

import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.*;

class PeerConnectionObserver implements PeerConnection.Observer {
    //csb
    private final static String TAG = ""; //WebRTCModule.TAG;

    private final SparseArray<DataChannel> dataChannels
        = new SparseArray<DataChannel>();
    private final int id;
    private PeerConnection peerConnection;
    final List<MediaStream> localStreams;
    final Map<String, MediaStream> remoteStreams;
    final Map<String, MediaStreamTrack> remoteTracks;
    private final VideoTrackAdapter videoTrackAdapters;
    //csb
    //private final WebRTCModule webRTCModule;

    /**
     * The <tt>StringBuilder</tt> cache utilized by {@link #statsToJSON} in
     * order to minimize the number of allocations of <tt>StringBuilder</tt>
     * instances and, more importantly, the allocations of its <tt>char</tt>
     * buffer in an attempt to improve performance.
     */
    private SoftReference<StringBuilder> statsToJSONStringBuilder
        = new SoftReference<>(null);

    //csb
//    PeerConnectionObserver(WebRTCModule webRTCModule,
//                           int id) {
//        this.webRTCModule = webRTCModule;
//        this.id = id;
//        this.localStreams = new ArrayList<MediaStream>();
//        this.remoteStreams = new HashMap<String, MediaStream>();
//        this.remoteTracks = new HashMap<String, MediaStreamTrack>();
//        this.videoTrackAdapters = new VideoTrackAdapter(webRTCModule, id);
//    }

    PeerConnectionObserver(//WebRTCModule webRTCModule,
                           int id) {
        //this.webRTCModule = webRTCModule;
        this.id = id;
        this.localStreams = new ArrayList<MediaStream>();
        this.remoteStreams = new HashMap<String, MediaStream>();
        this.remoteTracks = new HashMap<String, MediaStreamTrack>();
        //csb
        //this.videoTrackAdapters = new VideoTrackAdapter(webRTCModule, id);
        this.videoTrackAdapters = new VideoTrackAdapter(id);
    }
    /**
     * Adds a specific local <tt>MediaStream</tt> to the associated
     * <tt>PeerConnection</tt>.
     *
     * @param localStream the local <tt>MediaStream</tt> to add to the
     * associated <tt>PeerConnection</tt>
     * @return <tt>true</tt> if the specified <tt>localStream</tt> was added to
     * the associated <tt>PeerConnection</tt>; otherwise, <tt>false</tt>
     */
    boolean addStream(MediaStream localStream) {
        if (peerConnection != null && peerConnection.addStream(localStream)) {
            localStreams.add(localStream);

            return true;
        }

        return false;
    }

    /**
     * Removes a specific local <tt>MediaStream</tt> from the associated
     * <tt>PeerConnection</tt>.
     *
     * @param localStream the local <tt>MediaStream</tt> from the associated
     * <tt>PeerConnection</tt>
     * @return <tt>true</tt> if removing the specified <tt>mediaStream</tt> from
     * this instance resulted in a modification of its internal list of local
     * <tt>MediaStream</tt>s; otherwise, <tt>false</tt>
     */
    boolean removeStream(MediaStream localStream) {
        if (peerConnection != null) {
            peerConnection.removeStream(localStream);
        }

        return localStreams.remove(localStream);
    }

    PeerConnection getPeerConnection() {
        return peerConnection;
    }

    void setPeerConnection(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }

    void close() {
        // Close the PeerConnection first to stop any events.
        peerConnection.close();

        // PeerConnection.dispose() calls MediaStream.dispose() on all local
        // MediaStreams added to it and the app may crash if a local MediaStream
        // is added to multiple PeerConnections. In order to reduce the risks of
        // an app crash, remove all local MediaStreams from the associated
        // PeerConnection so that it doesn't attempt to dispose of them.
        for (MediaStream localStream : new ArrayList<>(localStreams)) {
            removeStream(localStream);
        }

        // Remove video track adapters
        for (MediaStreamTrack track : remoteTracks.values()) {
            if (track.kind().equals("video")) {
                videoTrackAdapters.removeAdapter((VideoTrack) track);
            }
        }

        // At this point there should be no local MediaStreams in the associated
        // PeerConnection. Call dispose() to free all remaining resources held
        // by the PeerConnection instance (RtpReceivers, RtpSenders, etc.)
        peerConnection.dispose();

        remoteStreams.clear();
        remoteTracks.clear();

        // Unlike on iOS, we cannot unregister the DataChannel.Observer
        // instance on Android. At least do whatever else we do on iOS.
        dataChannels.clear();
    }

    //csb
//    void createDataChannel(String label, ReadableMap config) {
//        DataChannel.Init init = new DataChannel.Init();
//        if (config != null) {
//            if (config.hasKey("id")) {
//                init.id = config.getInt("id");
//            }
//            if (config.hasKey("ordered")) {
//                init.ordered = config.getBoolean("ordered");
//            }
//            if (config.hasKey("maxRetransmitTime")) {
//                init.maxRetransmitTimeMs = config.getInt("maxRetransmitTime");
//            }
//            if (config.hasKey("maxRetransmits")) {
//                init.maxRetransmits = config.getInt("maxRetransmits");
//            }
//            if (config.hasKey("protocol")) {
//                init.protocol = config.getString("protocol");
//            }
//            if (config.hasKey("negotiated")) {
//                init.negotiated = config.getBoolean("negotiated");
//            }
//        }
//        DataChannel dataChannel = peerConnection.createDataChannel(label, init);
//        int dataChannelId = init.id;
//        if (-1 != dataChannelId) {
//            dataChannels.put(dataChannelId, dataChannel);
//            registerDataChannelObserver(dataChannelId, dataChannel);
//        }
//    }

    void createDataChannel(String label, HashMap config) {
        DataChannel.Init init = new DataChannel.Init();
        if (config != null) {
            if (config.containsKey("id")) {
                init.id = (int) config.get("id");
            }
            if (config.containsKey("ordered")) {
                init.ordered = (boolean) config.get("ordered");
            }
            if (config.containsKey("maxRetransmitTime")) {
                init.maxRetransmitTimeMs = (int) config.get("maxRetransmitTime");
            }
            if (config.containsKey("maxRetransmits")) {
                init.maxRetransmits = (int) config.get("maxRetransmits");
            }
            if (config.containsKey("protocol")) {
                init.protocol = (String) config.get("protocol");
            }
            if (config.containsKey("negotiated")) {
                init.negotiated = (boolean) config.get("negotiated");
            }
        }
        DataChannel dataChannel = peerConnection.createDataChannel(label, init);
        int dataChannelId = init.id;
        if (-1 != dataChannelId) {
            dataChannels.put(dataChannelId, dataChannel);
            registerDataChannelObserver(dataChannelId, dataChannel);
        }
    }

    void dataChannelClose(int dataChannelId) {
        DataChannel dataChannel = dataChannels.get(dataChannelId);
        if (dataChannel != null) {
            dataChannel.close();
            dataChannels.remove(dataChannelId);
        } else {
            Log.d(TAG, "dataChannelClose() dataChannel is null");
        }
    }

    void dataChannelSend(int dataChannelId, String data, String type) {
        DataChannel dataChannel = dataChannels.get(dataChannelId);
        if (dataChannel != null) {
            byte[] byteArray;
            if (type.equals("text")) {
                try {
                    byteArray = data.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.d(TAG, "Could not encode text string as UTF-8.");
                    return;
                }
            } else if (type.equals("binary")) {
                byteArray = Base64.decode(data, Base64.NO_WRAP);
            } else {
                Log.e(TAG, "Unsupported data type: " + type);
                return;
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
            DataChannel.Buffer buffer = new DataChannel.Buffer(byteBuffer, type.equals("binary"));
            dataChannel.send(buffer);
        } else {
            Log.d(TAG, "dataChannelSend() dataChannel is null");
        }
    }

    //csb
//    void getStats(String trackId, final Callback cb) {
//        MediaStreamTrack track = null;
//        if (trackId == null
//                || trackId.isEmpty()
//                || (track = webRTCModule.getLocalTrack(trackId)) != null
//                || (track = remoteTracks.get(trackId)) != null) {
//            peerConnection.getStats(
//                    new StatsObserver() {
//                        @Override
//                        public void onComplete(StatsReport[] reports) {
//                            cb.invoke(true, statsToJSON(reports));
//                        }
//                    },
//                    track);
//        } else {
//            Log.e(TAG, "peerConnectionGetStats() MediaStreamTrack not found for id: " + trackId);
//            cb.invoke(false, "Track not found");
//        }
//    }

    /**
     * Constructs a JSON <tt>String</tt> representation of a specific array of
     * <tt>StatsReport</tt>s (produced by {@link PeerConnection#getStats}).
     * <p>
     * On Android it is faster to (1) construct a single JSON <tt>String</tt>
     * representation of an array of <tt>StatsReport</tt>s and (2) have it pass
     * through the React Native bridge rather than the array of
     * <tt>StatsReport</tt>s.
     *
     * @param reports the array of <tt>StatsReport</tt>s to represent in JSON
     * format
     * @return a <tt>String</tt> which represents the specified <tt>reports</tt>
     * in JSON format
     */
    private String statsToJSON(StatsReport[] reports) {
        // If possible, reuse a single StringBuilder instance across multiple
        // getStats method calls in order to reduce the total number of
        // allocations.
        StringBuilder s = statsToJSONStringBuilder.get();
        if (s == null) {
            s = new StringBuilder();
            statsToJSONStringBuilder = new SoftReference(s);
        }

        s.append('[');
        final int reportCount = reports.length;
        for (int i = 0; i < reportCount; ++i) {
            StatsReport report = reports[i];
            if (i != 0) {
                s.append(',');
            }
            s.append("{\"id\":\"").append(report.id)
                .append("\",\"type\":\"").append(report.type)
                .append("\",\"timestamp\":").append(report.timestamp)
                .append(",\"values\":[");
            StatsReport.Value[] values = report.values;
            final int valueCount = values.length;
            for (int j = 0; j < valueCount; ++j) {
                StatsReport.Value v = values[j];
                if (j != 0) {
                    s.append(',');
                }
                s.append("{\"").append(v.name).append("\":\"").append(v.value)
                    .append("\"}");
            }
            s.append("]}");
        }
        s.append("]");

        String r = s.toString();
        // Prepare the StringBuilder instance for reuse (in order to reduce the
        // total number of allocations performed during multiple getStats method
        // calls).
        s.setLength(0);

        return r;
    }

    //csb
//    @Override
//    public void onIceCandidate(final IceCandidate candidate) {
//        Log.d(TAG, "onIceCandidate");
//        WritableMap params = Arguments.createMap();
//        params.putInt("id", id);
//        WritableMap candidateParams = Arguments.createMap();
//        candidateParams.putInt("sdpMLineIndex", candidate.sdpMLineIndex);
//        candidateParams.putString("sdpMid", candidate.sdpMid);
//        candidateParams.putString("candidate", candidate.sdp);
//        params.putMap("candidate", candidateParams);
//
//        webRTCModule.sendEvent("peerConnectionGotICECandidate", params);
//    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        Log.d(TAG, "onIceCandidate");
        HashMap params = new HashMap();
        params.put("id", id);
        HashMap candidateParams = new HashMap();
        candidateParams.put("sdpMLineIndex", candidate.sdpMLineIndex);
        candidateParams.put("sdpMid", candidate.sdpMid);
        candidateParams.put("candidate", candidate.sdp);
        params.put("candidate", candidateParams);

        //csb
        //webRTCModule.sendEvent("peerConnectionGotICECandidate", params);
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
        Log.d(TAG, "onIceCandidatesRemoved");
    }

//    @Override
//    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
//        WritableMap params = Arguments.createMap();
//        params.putInt("id", id);
//        params.putString("iceConnectionState", iceConnectionStateString(iceConnectionState));
//
//        webRTCModule.sendEvent("peerConnectionIceConnectionChanged", params);
//    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        HashMap params = new HashMap();
        params.put("id", id);
        params.put("iceConnectionState", iceConnectionStateString(iceConnectionState));

        //csb
        //webRTCModule.sendEvent("peerConnectionIceConnectionChanged", params);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean var1) {
    }

    //csb
//    @Override
//    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
//        Log.d(TAG, "onIceGatheringChange" + iceGatheringState.name());
//        WritableMap params = Arguments.createMap();
//        params.putInt("id", id);
//        params.putString("iceGatheringState", iceGatheringStateString(iceGatheringState));
//        webRTCModule.sendEvent("peerConnectionIceGatheringChanged", params);
//    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.d(TAG, "onIceGatheringChange" + iceGatheringState.name());
        HashMap params = new HashMap();
        params.put("id", id);
        params.put("iceGatheringState", iceGatheringStateString(iceGatheringState));
        //csb
        //webRTCModule.sendEvent("peerConnectionIceGatheringChanged", params);
    }

    private String getReactTagForStream(MediaStream mediaStream) {
        for (Iterator<Map.Entry<String, MediaStream>> i
             = remoteStreams.entrySet().iterator();
             i.hasNext();) {
            Map.Entry<String, MediaStream> e = i.next();
            if (e.getValue().equals(mediaStream)) {
                return e.getKey();
            }
        }
        return null;
    }

    //csb
//    @Override
//    public void onAddStream(MediaStream mediaStream) {
//        String streamReactTag = null;
//        String streamId = mediaStream.getId();
//        // The native WebRTC implementation has a special concept of a default
//        // MediaStream instance with the label default that the implementation
//        // reuses.
//        if ("default".equals(streamId)) {
//            for (Map.Entry<String, MediaStream> e
//                    : remoteStreams.entrySet()) {
//                if (e.getValue().equals(mediaStream)) {
//                    streamReactTag = e.getKey();
//                    break;
//                }
//            }
//        }
//
//        if (streamReactTag == null) {
//            streamReactTag = UUID.randomUUID().toString();
//            remoteStreams.put(streamReactTag, mediaStream);
//        }
//
//        WritableMap params = Arguments.createMap();
//        params.putInt("id", id);
//        params.putString("streamId", streamId);
//        params.putString("streamReactTag", streamReactTag);
//
//        WritableArray tracks = Arguments.createArray();
//
//        for (int i = 0; i < mediaStream.videoTracks.size(); i++) {
//            VideoTrack track = mediaStream.videoTracks.get(i);
//            String trackId = track.id();
//
//            remoteTracks.put(trackId, track);
//
//            WritableMap trackInfo = Arguments.createMap();
//            trackInfo.putString("id", trackId);
//            trackInfo.putString("label", "Video");
//            trackInfo.putString("kind", track.kind());
//            trackInfo.putBoolean("enabled", track.enabled());
//            trackInfo.putString("readyState", track.state().toString());
//            trackInfo.putBoolean("remote", true);
//            tracks.pushMap(trackInfo);
//
//            videoTrackAdapters.addAdapter(streamReactTag, track);
//        }
//        for (int i = 0; i < mediaStream.audioTracks.size(); i++) {
//            AudioTrack track = mediaStream.audioTracks.get(i);
//            String trackId = track.id();
//
//            remoteTracks.put(trackId, track);
//
//            WritableMap trackInfo = Arguments.createMap();
//            trackInfo.putString("id", trackId);
//            trackInfo.putString("label", "Audio");
//            trackInfo.putString("kind", track.kind());
//            trackInfo.putBoolean("enabled", track.enabled());
//            trackInfo.putString("readyState", track.state().toString());
//            trackInfo.putBoolean("remote", true);
//            tracks.pushMap(trackInfo);
//        }
//        params.putArray("tracks", tracks);
//
//        webRTCModule.sendEvent("peerConnectionAddedStream", params);
//    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        String streamReactTag = null;
        String streamId = mediaStream.getId();
        // The native WebRTC implementation has a special concept of a default
        // MediaStream instance with the label default that the implementation
        // reuses.
        if ("default".equals(streamId)) {
            for (Map.Entry<String, MediaStream> e
                    : remoteStreams.entrySet()) {
                if (e.getValue().equals(mediaStream)) {
                    streamReactTag = e.getKey();
                    break;
                }
            }
        }

        if (streamReactTag == null) {
            streamReactTag = UUID.randomUUID().toString();
            remoteStreams.put(streamReactTag, mediaStream);
        }

        HashMap params = new HashMap();
        params.put("id", id);
        params.put("streamId", streamId);
        params.put("streamReactTag", streamReactTag);

        ArrayList tracks = new ArrayList();

        for (int i = 0; i < mediaStream.videoTracks.size(); i++) {
            VideoTrack track = mediaStream.videoTracks.get(i);
            String trackId = track.id();

            remoteTracks.put(trackId, track);

            HashMap trackInfo = new HashMap();
            trackInfo.put("id", trackId);
            trackInfo.put("label", "Video");
            trackInfo.put("kind", track.kind());
            trackInfo.put("enabled", track.enabled());
            trackInfo.put("readyState", track.state().toString());
            trackInfo.put("remote", true);
            tracks.add(trackInfo);

            videoTrackAdapters.addAdapter(streamReactTag, track);
        }
        for (int i = 0; i < mediaStream.audioTracks.size(); i++) {
            AudioTrack track = mediaStream.audioTracks.get(i);
            String trackId = track.id();

            remoteTracks.put(trackId, track);

            HashMap  trackInfo =  new HashMap();
            trackInfo.put("id", trackId);
            trackInfo.put("label", "Audio");
            trackInfo.put("kind", track.kind());
            trackInfo.put("enabled", track.enabled());
            trackInfo.put("readyState", track.state().toString());
            trackInfo.put("remote", true);
            tracks.add(trackInfo);
        }
        params.put("tracks", tracks);

        //csb
        //webRTCModule.sendEvent("peerConnectionAddedStream", params);
    }

//    @Override
//    public void onRemoveStream(MediaStream mediaStream) {
//        String streamReactTag = getReactTagForStream(mediaStream);
//        if (streamReactTag == null) {
//            Log.w(TAG,
//                "onRemoveStream - no remote stream for id: "
//                    + mediaStream.getId());
//            return;
//        }
//
//        for (VideoTrack track : mediaStream.videoTracks) {
//            this.videoTrackAdapters.removeAdapter(track);
//            this.remoteTracks.remove(track.id());
//        }
//        for (AudioTrack track : mediaStream.audioTracks) {
//            this.remoteTracks.remove(track.id());
//        }
//
//        this.remoteStreams.remove(streamReactTag);
//
//        WritableMap params = Arguments.createMap();
//        params.putInt("id", id);
//        params.putString("streamId", streamReactTag);
//        webRTCModule.sendEvent("peerConnectionRemovedStream", params);
//    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        String streamReactTag = getReactTagForStream(mediaStream);
        if (streamReactTag == null) {
            Log.w(TAG,
                    "onRemoveStream - no remote stream for id: "
                            + mediaStream.getId());
            return;
        }

        for (VideoTrack track : mediaStream.videoTracks) {
            this.videoTrackAdapters.removeAdapter(track);
            this.remoteTracks.remove(track.id());
        }
        for (AudioTrack track : mediaStream.audioTracks) {
            this.remoteTracks.remove(track.id());
        }

        this.remoteStreams.remove(streamReactTag);

        HashMap params = new HashMap();
        params.put("id", id);
        params.put("streamId", streamReactTag);
        //csb
        //webRTCModule.sendEvent("peerConnectionRemovedStream", params);
    }

    //csb
//    @Override
//    public void onDataChannel(DataChannel dataChannel) {
//        final int dataChannelId = dataChannel.id();
//        if (-1 == dataChannelId) {
//          return;
//        }
//
//        WritableMap dataChannelParams = Arguments.createMap();
//        dataChannelParams.putInt("id", dataChannelId);
//        dataChannelParams.putString("label", dataChannel.label());
//        WritableMap params = Arguments.createMap();
//        params.putInt("id", id);
//        params.putMap("dataChannel", dataChannelParams);
//
//        dataChannels.put(dataChannelId, dataChannel);
//        registerDataChannelObserver(dataChannelId, dataChannel);
//
//        webRTCModule.sendEvent("peerConnectionDidOpenDataChannel", params);
//    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        final int dataChannelId = dataChannel.id();
        if (-1 == dataChannelId) {
            return;
        }

        HashMap dataChannelParams = new HashMap();
        dataChannelParams.put("id", dataChannelId);
        dataChannelParams.put("label", dataChannel.label());
        HashMap params = new HashMap();
        params.put("id", id);
        params.put("dataChannel", dataChannelParams);

        dataChannels.put(dataChannelId, dataChannel);
        registerDataChannelObserver(dataChannelId, dataChannel);

        //csb
        //webRTCModule.sendEvent("peerConnectionDidOpenDataChannel", params);
    }

    private void registerDataChannelObserver(int dcId, DataChannel dataChannel) {
        // DataChannel.registerObserver implementation does not allow to
        // unregister, so the observer is registered here and is never
        // unregistered
        //csb
//        dataChannel.registerObserver(
//            new DataChannelObserver(webRTCModule, id, dcId, dataChannel));
        dataChannel.registerObserver(
                new DataChannelObserver(//webRTCModule,
                        id, dcId, dataChannel));
    }

    //csb
//    @Override
//    public void onRenegotiationNeeded() {
//        WritableMap params = Arguments.createMap();
//        params.putInt("id", id);
//        webRTCModule.sendEvent("peerConnectionOnRenegotiationNeeded", params);
//    }

    @Override
    public void onRenegotiationNeeded() {
        HashMap params = new HashMap();
        params.put("id", id);
        //csb
        //webRTCModule.sendEvent("peerConnectionOnRenegotiationNeeded", params);
    }

    //csb
//    @Override
//    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
//        WritableMap params = Arguments.createMap();
//        params.putInt("id", id);
//        params.putString("signalingState", signalingStateString(signalingState));
//        webRTCModule.sendEvent("peerConnectionSignalingStateChanged", params);
//    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        HashMap params = new HashMap();
        params.put("id", id);
        params.put("signalingState", signalingStateString(signalingState));
        //csb
        //webRTCModule.sendEvent("peerConnectionSignalingStateChanged", params);
    }

    @Override
    public void onAddTrack(final RtpReceiver receiver, final MediaStream[] mediaStreams) {
        Log.d(TAG, "onAddTrack");
    }

    @Nullable
    private String iceConnectionStateString(PeerConnection.IceConnectionState iceConnectionState) {
        switch (iceConnectionState) {
            case NEW:
                return "new";
            case CHECKING:
                return "checking";
            case CONNECTED:
                return "connected";
            case COMPLETED:
                return "completed";
            case FAILED:
                return "failed";
            case DISCONNECTED:
                return "disconnected";
            case CLOSED:
                return "closed";
        }
        return null;
    }

    @Nullable
    private String iceGatheringStateString(PeerConnection.IceGatheringState iceGatheringState) {
        switch (iceGatheringState) {
            case NEW:
                return "new";
            case GATHERING:
                return "gathering";
            case COMPLETE:
                return "complete";
        }
        return null;
    }

    @Nullable
    private String signalingStateString(PeerConnection.SignalingState signalingState) {
        switch (signalingState) {
            case STABLE:
                return "stable";
            case HAVE_LOCAL_OFFER:
                return "have-local-offer";
            case HAVE_LOCAL_PRANSWER:
                return "have-local-pranswer";
            case HAVE_REMOTE_OFFER:
                return "have-remote-offer";
            case HAVE_REMOTE_PRANSWER:
                return "have-remote-pranswer";
            case CLOSED:
                return "closed";
        }
        return null;
    }
}
