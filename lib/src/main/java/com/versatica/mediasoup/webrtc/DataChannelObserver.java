package com.versatica.mediasoup.webrtc;

import android.support.annotation.Nullable;
import android.util.Base64;
import org.webrtc.DataChannel;

import java.nio.charset.Charset;
import java.util.HashMap;

class DataChannelObserver implements DataChannel.Observer {

    private final int mId;
    private final DataChannel mDataChannel;
    private final int peerConnectionId;
    //csb
    //private final WebRTCModule webRTCModule;

    //csb
//    DataChannelObserver(
//            WebRTCModule webRTCModule,
//            int peerConnectionId,
//            int id,
//            DataChannel dataChannel) {
//        this.webRTCModule = webRTCModule;
//        this.peerConnectionId = peerConnectionId;
//        mId = id;
//        mDataChannel = dataChannel;
//    }

    DataChannelObserver(
            //WebRTCModule webRTCModule,
            int peerConnectionId,
            int id,
            DataChannel dataChannel) {
        //this.webRTCModule = webRTCModule;
        this.peerConnectionId = peerConnectionId;
        mId = id;
        mDataChannel = dataChannel;
    }

    @Nullable
    private String dataChannelStateString(DataChannel.State dataChannelState) {
        switch (dataChannelState) {
        case CONNECTING:
            return "connecting";
        case OPEN:
            return "open";
        case CLOSING:
            return "closing";
        case CLOSED:
            return "closed";
        }
        return null;
    }

    @Override
    public void onBufferedAmountChange(long amount) {
    }

    //csb
//    @Override
//    public void onMessage(DataChannel.Buffer buffer) {
//        WritableMap params = Arguments.createMap();
//        params.putInt("id", mId);
//        params.putInt("peerConnectionId", peerConnectionId);
//
//        byte[] bytes;
//        if (buffer.data.hasArray()) {
//            bytes = buffer.data.array();
//        } else {
//            bytes = new byte[buffer.data.remaining()];
//            buffer.data.get(bytes);
//        }
//
//        String type;
//        String data;
//        if (buffer.binary) {
//            type = "binary";
//            data = Base64.encodeToString(bytes, Base64.NO_WRAP);
//        } else {
//            type = "text";
//            data = new String(bytes, Charset.forName("UTF-8"));
//        }
//        params.putString("type", type);
//        params.putString("data", data);
//
//        webRTCModule.sendEvent("dataChannelReceiveMessage", params);
//    }

    @Override
    public void onMessage(DataChannel.Buffer buffer) {
        HashMap params = new HashMap();
        params.put("id", mId);
        params.put("peerConnectionId", peerConnectionId);

        byte[] bytes;
        if (buffer.data.hasArray()) {
            bytes = buffer.data.array();
        } else {
            bytes = new byte[buffer.data.remaining()];
            buffer.data.get(bytes);
        }

        String type;
        String data;
        if (buffer.binary) {
            type = "binary";
            data = Base64.encodeToString(bytes, Base64.NO_WRAP);
        } else {
            type = "text";
            data = new String(bytes, Charset.forName("UTF-8"));
        }
        params.put("type", type);
        params.put("data", data);

        //csb
        //webRTCModule.sendEvent("dataChannelReceiveMessage", params);
    }

    //csb
//    @Override
//    public void onStateChange() {
//        WritableMap params = Arguments.createMap();
//        params.putInt("id", mId);
//        params.putInt("peerConnectionId", peerConnectionId);
//        params.putString("state", dataChannelStateString(mDataChannel.state()));
//        webRTCModule.sendEvent("dataChannelStateChanged", params);
//    }

    @Override
    public void onStateChange() {
        HashMap params = new HashMap();
        params.put("id", mId);
        params.put("peerConnectionId", peerConnectionId);
        params.put("state", dataChannelStateString(mDataChannel.state()));
        //csb
        //webRTCModule.sendEvent("dataChannelStateChanged", params);
    }
}
