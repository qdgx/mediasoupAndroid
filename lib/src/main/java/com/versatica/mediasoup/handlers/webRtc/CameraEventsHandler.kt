package com.versatica.mediasoup.handlers.webRtc

import com.versatica.mediasoup.Logger
import org.webrtc.CameraVideoCapturer

class CameraEventsHandler : CameraVideoCapturer.CameraEventsHandler {
    private val logger = Logger("CameraEventsHandler")
    
    // Callback invoked when camera closed.
    override fun onCameraClosed() {
        logger.debug("CameraEventsHandler.onFirstFrameAvailable")
    }

    // Called when camera is disconnected.
    override fun onCameraDisconnected() {
        logger.debug("CameraEventsHandler.onCameraDisconnected")
    }

    // Camera error handler - invoked when camera can not be opened or any
    // camera exception happens on camera thread.
    override fun onCameraError(errorDescription: String) {
        logger.debug("CameraEventsHandler.onCameraError: errorDescription=$errorDescription")
    }

    // Invoked when camera stops receiving frames
    override fun onCameraFreezed(errorDescription: String) {
        logger.debug("CameraEventsHandler.onCameraFreezed: errorDescription=$errorDescription")
    }

    // Callback invoked when camera is opening.
    override fun onCameraOpening(cameraName: String) {
        logger.debug("CameraEventsHandler.onCameraOpening: cameraName=$cameraName"
        )
    }

    // Callback invoked when first camera frame is available after camera is opened.
    override fun onFirstFrameAvailable() {
        logger.debug("CameraEventsHandler.onFirstFrameAvailable")
    }
    
}