package com.versatica.mediasoup.handlers.webRtc

import android.content.Context
import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.webrtc.Callback
import org.webrtc.*
import io.reactivex.Observable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


/**
 * The implementation of `getUserMedia` extracted into a separate file in
 * order to reduce complexity and to (somewhat) separate concerns.
 */
class GetUserMediaImpl(
    webRTCModule: WebRTCModule,
    context: Context
) {
    private val logger = Logger("GetUserMediaImpl")

    private val cameraEnumerator: CameraEnumerator

    private val webRTCModule: WebRTCModule = webRTCModule

    private val context: Context = context

    /**
     * The application/library-specific private members of local
     * [MediaStreamTrack]s created by `GetUserMediaImpl` mapped by
     * track ID.
     */
    private val tracks = HashMap<String, TrackPrivate>()

    init {

        // NOTE: to support Camera2, the device should:
        //   1. Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        //   2. all camera support level should greater than LEGACY
        //   see: https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#INFO_SUPPORTED_HARDWARE_LEVEL
        if (Camera2Enumerator.isSupported(context)) {
            logger.debug("Creating video capturer using Camera2 API.")
            cameraEnumerator = Camera2Enumerator(context)
        } else {
            logger.debug("Creating video capturer using Camera1 API.")
            cameraEnumerator = Camera1Enumerator(false)
        }
    }

    private fun createAudioTrack(constraints: HashMap<*, *>): AudioTrack {
        val audioConstraints = webRTCModule.parseMediaConstraints(constraints["audio"] as HashMap<*, *>)

        logger.debug("getUserMedia(audio): $audioConstraints")

        val id = UUID.randomUUID().toString()
        val pcFactory = webRTCModule.mFactory
        val audioSource = pcFactory?.createAudioSource(audioConstraints)
        val track = pcFactory?.createAudioTrack(id, audioSource)
        tracks[id] = TrackPrivate(track!!, audioSource!!, null)

        return track
    }

    private fun createVideoTrack(constraints: HashMap<*, *>): VideoTrack? {
        val videoConstraintsMap = constraints["video"] as HashMap<*, *>

        logger.debug("getUserMedia(video): $videoConstraintsMap")

        val videoCaptureController = VideoCaptureController(cameraEnumerator, videoConstraintsMap)
        val videoCapturer = videoCaptureController.videoCapturer ?: return null

        val pcFactory = webRTCModule.mFactory

        val videoSource = pcFactory?.createVideoSource(false)

        val id = UUID.randomUUID().toString()
        val track = pcFactory?.createVideoTrack(id, videoSource)

        track?.setEnabled(true)
        videoCaptureController.startCapture()

        tracks[id] = TrackPrivate(track!!, videoSource!!, videoCaptureController)

        return track
    }

    fun enumerateDevices(): ArrayList<*> {
        val array = ArrayList<HashMap<*, *>>()
        val devices = cameraEnumerator.getDeviceNames()

        for (i in devices.indices) {
            val params = HashMap<String, String>()
            params.put("deviceId", "" + i)
            params.put("groupId", "")
            params.put("label", devices[i])
            params.put("kind", "videoinput")
            array.add(params)
        }

        val audio = HashMap<String, String>()
        audio.put("deviceId", "audio-1")
        audio.put("groupId", "")
        audio.put("label", "Audio")
        audio.put("kind", "audioinput")

        array.add(audio)

        return array
    }

    fun getTrack(id: String): MediaStreamTrack? {
        val trackPrivate = tracks[id]

        return trackPrivate?.track
    }

    /**
     * Implements `getUserMedia`. Note that at this point constraints have
     * been normalized and permissions have been granted. The constraints only
     * contain keys for which permissions have already been granted, that is,
     * if audio permission was not granted, there will be no "audio" key in
     * the constraints map.
     */
    fun getUserMedia(
        constraints: HashMap<*, *>,
        successCallback: Callback,
        errorCallback: Callback
    ) {
        // TODO: change getUserMedia constraints format to support new syntax
        //   constraint format seems changed, and there is no mandatory any more.
        //   and has a new syntax/attrs to specify resolution
        //   should change `parseConstraints()` according
        //   see: https://www.w3.org/TR/mediacapture-streams/#idl-def-MediaTrackConstraints

        var audioTrack: AudioTrack? = null
        var videoTrack: VideoTrack? = null

        if (constraints.containsKey("audio")) {
            audioTrack = createAudioTrack(constraints)
        }

        if (constraints.containsKey("video")) {
            videoTrack = createVideoTrack(constraints)
        }

        if (audioTrack == null && videoTrack == null) {
            // Fail with DOMException with name AbortError as per:
            // https://www.w3.org/TR/mediacapture-streams/#dom-mediadevices-getusermedia
            errorCallback.invoke("DOMException AbortError")
            return
        }

        val streamId = UUID.randomUUID().toString()

        val mediaStream = webRTCModule.mFactory?.createLocalMediaStream(streamId)

        mediaStream?.addTrack(audioTrack)
        mediaStream?.addTrack(videoTrack)

        logger.debug("MediaStream id: $streamId")

        successCallback.invoke(mediaStream)
    }

    fun getUserMedia(constraints: HashMap<*, *>): Observable<MediaStream> {
        return Observable.create {
            getUserMedia(constraints,
                successCallback = Callback { args ->
                    var mediaStream = args[0] as MediaStream
                    it.onNext(mediaStream)
                },
                errorCallback = Callback { args ->
                    var errorMessage = args[0] as String
                    it.onError(Throwable(errorMessage))
                })
        }
    }

    fun mediaStreamTrackSetEnabled(trackId: String, enabled: Boolean) {
        val track = tracks[trackId]
        if (track != null && track.videoCaptureController != null) {
            if (enabled) {
                track.videoCaptureController.startCapture()
            } else {
                track.videoCaptureController.stopCapture()
            }
        }
    }

    fun mediaStreamTrackStop(id: String) {
        val track = getTrack(id)
        if (track == null) {
            logger.debug("mediaStreamTrackStop() No local MediaStreamTrack with id $id")
            return
        }
        track!!.setEnabled(false)
        removeTrack(id)
    }

    private fun removeTrack(id: String) {
        val track = tracks.remove(id)
        if (track != null) {
            val videoCaptureController = track.videoCaptureController
            if (videoCaptureController != null) {
                if (videoCaptureController.stopCapture()) {
                    videoCaptureController.dispose()
                }
            }
            track.mediaSource.dispose()
        }
    }

    fun switchCamera(trackId: String) {
        val track = tracks[trackId]
        if (track != null && track.videoCaptureController != null) {
            track.videoCaptureController.switchCamera()
        }
    }

    /**
     * Application/library-specific private members of local
     * `MediaStreamTrack`s created by `GetUserMediaImpl`.
     */
    private class TrackPrivate
    /**
     * Initializes a new `TrackPrivate` instance.
     *
     * @param track
     * @param mediaSource the `MediaSource` from which the specified
     * `code` was created
     * @param  {@code VideoCapturer} from which the
     * specified `mediaSource` was created if the specified
     * `track` is a [VideoTrack]
     */
        (
        val track: MediaStreamTrack,
        /**
         * The `MediaSource` from which [.track] was created.
         */
        val mediaSource: MediaSource,
        /**
         * The `VideoCapturer` from which [.mediaSource] was created
         * if [.track] is a [VideoTrack].
         */
        val videoCaptureController: VideoCaptureController?
    )
}
