package com.versatica.mediasoup.handlers.webRtc

import com.versatica.mediasoup.Logger
import com.versatica.mediasoup.handlers.BaseApplication
import org.webrtc.*

class VideoCaptureController(
    cameraEnumerator: CameraEnumerator,
    constraints: HashMap<*, *>
) {
    companion object {
        /**
         * Default values for width, height and fps (respectively) which will be
         * used to open the camera at.
         */
        private val DEFAULT_WIDTH = 1280
        private val DEFAULT_HEIGHT = 720
        private val DEFAULT_FPS = 30
    }
    
    private val logger = Logger("VideoCaptureController")
    /**
     * Values for width, height and fps (respectively) which will be
     * used to open the camera at.
     */
    private var width = DEFAULT_WIDTH
    private var height = DEFAULT_HEIGHT
    private var fps = DEFAULT_FPS

    /**
     * The [CameraEventsHandler] used with
     * [CameraEnumerator.createCapturer]. Cached because the
     * implementation does not do anything but logging unspecific to the camera
     * device's name anyway.
     */
    private val cameraEventsHandler = CameraEventsHandler()

    /**
     * [VideoCapturer] which this controller manages.
     */
    var videoCapturer: VideoCapturer? = null
        private set


    init {
        var videoConstraintsMandatory: HashMap<*,*>? = null
        if (constraints.containsKey("mandatory") && constraints["mandatory"] is  HashMap<*, *> ) {
            videoConstraintsMandatory = constraints["mandatory"] as HashMap<*, *>
        }

        val sourceId = getSourceIdConstraint(constraints)
        val facingMode = getFacingMode(constraints)

        videoCapturer = createVideoCapturer(cameraEnumerator, sourceId, facingMode)

        if (videoConstraintsMandatory != null) {
            width = if (videoConstraintsMandatory.containsKey("minWidth"))
                (videoConstraintsMandatory["minWidth"] as String).toInt()
            else
                DEFAULT_WIDTH
            height = if (videoConstraintsMandatory.containsKey("minHeight"))
                (videoConstraintsMandatory["minHeight"] as String).toInt()
            else
                DEFAULT_HEIGHT
            fps = if (videoConstraintsMandatory.containsKey("minFrameRate"))
                (videoConstraintsMandatory["minFrameRate"] as String).toInt()
            else
                DEFAULT_FPS
        }
    }

    fun dispose() {
        if (videoCapturer != null) {
            videoCapturer!!.dispose()
            videoCapturer = null
        }
    }

    fun startCapture(videoSource: VideoSource) {
        try {
            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", EglUtil.rootEglBaseContext)
            videoCapturer!!.initialize(surfaceTextureHelper, BaseApplication.getAppContext(),videoSource.capturerObserver)
            videoCapturer!!.startCapture(width, height, fps)
        } catch (e: RuntimeException) {
            // XXX This can only fail if we initialize the capturer incorrectly,
            // which we don't. Thus, ignore any failures here since we trust
            // ourselves.
            logger.error(e.message!!)
        }

    }

    fun stopCapture(): Boolean {
        try {
            videoCapturer!!.stopCapture()
            return true
        } catch (e: InterruptedException) {
            return false
        }

    }

    fun switchCamera() {
        if (videoCapturer is CameraVideoCapturer) {
            (videoCapturer as CameraVideoCapturer).switchCamera(null)
        }
    }

    /**
     * Constructs a new `VideoCapturer` instance attempting to satisfy
     * specific constraints.
     *
     * @param enumerator a `CameraEnumerator` provided by WebRTC. It can
     * be `Camera1Enumerator` or `Camera2Enumerator`.
     * @param sourceId the ID of the requested video source. If not
     * `null` and a `VideoCapturer` can be created for it, then
     * `facingMode` is ignored.
     * @param facingMode the facing of the requested video source such as
     * `user` and `environment`. If `null`, "user" is
     * presumed.
     * @return a `VideoCapturer` satisfying the `facingMode` or
     * `sourceId` constraint
     */
    private fun createVideoCapturer(
        enumerator: CameraEnumerator,
        sourceId: String?,
        facingMode: String?
    ): VideoCapturer? {
        val deviceNames = enumerator.getDeviceNames()
        val failedDevices = ArrayList<String>()

        // If sourceId is specified, then it takes precedence over facingMode.
        if (sourceId != null) {
            for (name in deviceNames) {
                if (name == sourceId) {
                    val videoCapturer = enumerator.createCapturer(name, cameraEventsHandler)
                    val message = "Create user-specified camera $name"
                    if (videoCapturer != null) {
                        logger.debug("$message succeeded")
                        return videoCapturer
                    } else {
                        logger.debug("$message failed")
                        failedDevices.add(name)
                        break // fallback to facingMode
                    }
                }
            }
        }

        // Otherwise, use facingMode (defaulting to front/user facing).
        val isFrontFacing = facingMode == null || facingMode != "environment"
        for (name in deviceNames) {
            if (failedDevices.contains(name)) {
                continue
            }
            try {
                // This can throw an exception when using the Camera 1 API.
                if (enumerator.isFrontFacing(name) != isFrontFacing) {
                    continue
                }
            } catch (e: Exception) {
                logger.debug("Failed to check the facing mode of camera $name")
                failedDevices.add(name)
                continue
            }

            val videoCapturer = enumerator.createCapturer(name, cameraEventsHandler)
            val message = "Create camera $name"
            if (videoCapturer != null) {
                logger.debug("$message succeeded")
                return videoCapturer
            } else {
                logger.debug("$message failed")
                failedDevices.add(name)
            }
        }

        // Fallback to any available camera.
        for (name in deviceNames) {
            if (!failedDevices.contains(name)) {
                val videoCapturer = enumerator.createCapturer(name, cameraEventsHandler)
                val message = "Create fallback camera $name"
                if (videoCapturer != null) {
                    logger.debug("$message succeeded")
                    return videoCapturer
                } else {
                    logger.debug("$message failed")
                    failedDevices.add(name)
                    // fallback to the next device.
                }
            }
        }

        logger.warn("Unable to identify a suitable camera.")

        return null
    }


    /**
     * Retrieves "facingMode" constraint value.
     *
     * @param mediaConstraints a `ReadableMap` which represents "GUM"
     * constraints argument.
     * @return String value of "facingMode" constraints in "GUM" or
     * `null` if not specified.
     */
    private fun getFacingMode(mediaConstraints: HashMap<*, *>?): String? {
        if (mediaConstraints == null){
            return null
        }else if (mediaConstraints["facingMode"] == null){
            return null
        }else{
            return mediaConstraints["facingMode"] as String
        }
    }

    //csb
    /**
     * Retrieves "sourceId" constraint value.
     *
     * @param mediaConstraints a `ReadableMap` which represents "GUM"
     * constraints argument
     * @return String value of "sourceId" optional "GUM" constraint or
     * `null` if not specified.
     */
    private fun getSourceIdConstraint(mediaConstraints: HashMap<*, *>?): String? {
        if (mediaConstraints != null
            && mediaConstraints.containsKey("optional")
            && mediaConstraints["optional"] is ArrayList<*>
        ) {
            val optional = mediaConstraints["optional"] as ArrayList<*>

            var i = 0
            val size = optional.size
            while (i < size) {
                if (optional[i] is HashMap<*,*>) {
                    val option = optional[i] as HashMap<*, *>

                    if (option.containsKey("sourceId") && String::class.java.isInstance(option["sourceId"])) {
                        return option["sourceId"] as String
                    }
                }
                i++
            }
        }

        return null
    }
}