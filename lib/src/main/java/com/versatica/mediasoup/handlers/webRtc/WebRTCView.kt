package com.versatica.mediasoup.handlers.webRtc

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.support.v4.view.ViewCompat
import android.view.ViewGroup
import com.versatica.mediasoup.Logger
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class WebRTCView(context: Context, private val webRTCModule: WebRTCModule) : ViewGroup(context) {
    private val logger = Logger("WebRTCView")
    
    /**
     * The height of the last video frame rendered by
     * [.surfaceViewRenderer].
     */
    private var frameHeight: Int = 0

    /**
     * The rotation (degree) of the last video frame rendered by
     * [.surfaceViewRenderer].
     */
    private var frameRotation: Int = 0

    /**
     * The width of the last video frame rendered by
     * [.surfaceViewRenderer].
     */
    private var frameWidth: Int = 0

    /**
     * The `Object` which synchronizes the access to the layout-related
     * state of this instance such as [.frameHeight],
     * [.frameRotation], [.frameWidth], and [.scalingType].
     */
    private val layoutSyncRoot = Any()

    /**
     * The indicator which determines whether this `WebRTCView` is to
     * mirror the video represented by [.videoTrack] during its rendering.
     */
    private var mirror: Boolean = false

    /**
     * Indicates if the [SurfaceViewRenderer] is attached to the video
     * track.
     */
    private var rendererAttached: Boolean = false

    /**
     * The `RendererEvents` which listens to rendering events reported by
     * [.surfaceViewRenderer].
     */
    private val rendererEvents = object : RendererCommon.RendererEvents {
        override fun onFirstFrameRendered() {
            this@WebRTCView.onFirstFrameRendered()
        }

        override fun onFrameResolutionChanged(
            videoWidth: Int, videoHeight: Int,
            rotation: Int
        ) {
            this@WebRTCView.onFrameResolutionChanged(
                videoWidth, videoHeight,
                rotation
            )
        }
    }

    /**
     * The `Runnable` representation of
     * [.requestSurfaceViewRendererLayout]. Explicitly defined in order
     * to allow the use of the latter with [.post] without
     * initializing new instances on every (method) call.
     */
    private val requestSurfaceViewRendererLayoutRunnable = Runnable { requestSurfaceViewRendererLayout() }

    /**
     * The scaling type this `WebRTCView` is to apply to the video
     * represented by [.videoTrack] during its rendering. An expression of
     * the CSS property `object-fit` in the terms of WebRTC.
     */
    private var scalingType: RendererCommon.ScalingType? = null
    
    /**
     * The [View] and [VideoSink] implementation which
     * actually renders [.videoTrack] on behalf of this instance.
     */
    private var surfaceViewRenderer: SurfaceViewRenderer

    /**
     * The `VideoTrack`, if any, rendered by this `WebRTCView`.
     */
    private var videoTrack: VideoTrack? = null

    init {
        surfaceViewRenderer = SurfaceViewRenderer(context)
        addView(surfaceViewRenderer)

        setMirror(false)
        setScalingType(DEFAULT_SCALING_TYPE)
    }

    /**
     * "Cleans" the `SurfaceViewRenderer` by setting the view part to
     * opaque black and the surface part to transparent.
     */
    private fun cleanSurfaceViewRenderer() {
        surfaceViewRenderer.setBackgroundColor(Color.BLACK)
        surfaceViewRenderer.clearImage()
    }
    
    
    /**
     * If this <tt>View</tt> has [View.isInLayout], invokes it and
     * returns its return value; otherwise, returns <tt>false</tt> like
     * [ViewCompat.isInLayout].
     *
     * @return If this <tt>View</tt> has <tt>View#isInLayout()</tt>, invokes it
     * and returns its return value; otherwise, returns <tt>false</tt>.
     */
    private fun invokeIsInLayout(): Boolean {
        val m = IS_IN_LAYOUT
        var b = false

        if (m != null) {
            try {
                b = m!!.invoke(this) as Boolean
            } catch (e: IllegalAccessException) {
                // Fall back to the behavior of ViewCompat#isInLayout(View).
            } catch (e: InvocationTargetException) {
            }

        }
        return b
    }

    @Override
    protected override fun onAttachedToWindow() {
        try {
            // Generally, OpenGL is only necessary while this View is attached
            // to a window so there is no point in having the whole rendering
            // infrastructure hooked up while this View is not attached to a
            // window. Additionally, a memory leak was solved in a similar way
            // on iOS.
            tryAddRendererToVideoTrack()
        } finally {
            super.onAttachedToWindow()
        }
    }
    
    @Override
    protected override fun onDetachedFromWindow() {
        try {
            // Generally, OpenGL is only necessary while this View is attached
            // to a window so there is no point in having the whole rendering
            // infrastructure hooked up while this View is not attached to a
            // window. Additionally, a memory leak was solved in a similar way
            // on iOS.
            removeRendererFromVideoTrack()
        } finally {
            super.onDetachedFromWindow()
        }
    }

    /**
     * Callback fired by [.surfaceViewRenderer] when the first frame is
     * rendered. Here we will set the background of the view part of the
     * SurfaceView to transparent, so the surface (where video is actually
     * rendered) shines through.
     */
    private fun onFirstFrameRendered() {
        post {
            logger.debug("First frame rendered.")
            surfaceViewRenderer.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    /**
     * Callback fired by [.surfaceViewRenderer] when the resolution or
     * rotation of the frame it renders has changed.
     *
     * @param videoWidth The new width of the rendered video frame.
     * @param videoHeight The new height of the rendered video frame.
     * @param rotation The new rotation of the rendered video frame.
     */
    private fun onFrameResolutionChanged(
        videoWidth: Int, videoHeight: Int,
        rotation: Int
    ) {
        var changed = false

        synchronized(layoutSyncRoot) {
            if (this.frameHeight != videoHeight) {
                this.frameHeight = videoHeight
                changed = true
            }
            if (this.frameRotation != rotation) {
                this.frameRotation = rotation
                changed = true
            }
            if (this.frameWidth != videoWidth) {
                this.frameWidth = videoWidth
                changed = true
            }
        }
        if (changed) {
            // The onFrameResolutionChanged method call executes on the
            // surfaceViewRenderer's render Thread.
            post(requestSurfaceViewRendererLayoutRunnable)
        }
    }
    
    @Override
    protected override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var l = l
        var t = t
        var r = r
        var b = b
        val height = b - t
        val width = r - l

        if (height == 0 || width == 0) {
            b = 0
            r = b
            t = r
            l = t
        } else {
            val frameHeight: Int
            val frameRotation: Int
            val frameWidth: Int
            val scalingType: RendererCommon.ScalingType?

            synchronized(layoutSyncRoot) {
                frameHeight = this.frameHeight
                frameRotation = this.frameRotation
                frameWidth = this.frameWidth
                scalingType = this.scalingType
            }

            when (scalingType) {
                RendererCommon.ScalingType.SCALE_ASPECT_FILL -> {
                    // Fill this ViewGroup with surfaceViewRenderer and the latter
                    // will take care of filling itself with the video similarly to
                    // the cover value the CSS property object-fit.
                    r = width
                    l = 0
                    b = height
                    t = 0
                }
                RendererCommon.ScalingType.SCALE_ASPECT_FIT ->
                    // Lay surfaceViewRenderer out inside this ViewGroup in accord
                    // with the contain value of the CSS property object-fit.
                    // SurfaceViewRenderer will fill itself with the video similarly
                    // to the cover or contain value of the CSS property object-fit
                    // (which will not matter, eventually).
                    if (frameHeight == 0 || frameWidth == 0) {
                        b = 0
                        r = b
                        t = r
                        l = t
                    } else {
                        val frameAspectRatio = if (frameRotation % 180 == 0)
                            frameWidth / frameHeight.toFloat()
                        else
                            frameHeight / frameWidth.toFloat()
                        val frameDisplaySize = RendererCommon.getDisplaySize(
                            scalingType!!,
                            frameAspectRatio,
                            width, height
                        )

                        l = (width - frameDisplaySize.x) / 2
                        t = (height - frameDisplaySize.y) / 2
                        r = l + frameDisplaySize.x
                        b = t + frameDisplaySize.y
                    }
                else -> if (frameHeight == 0 || frameWidth == 0) {
                    b = 0
                    r = b
                    t = r
                    l = t
                } else {
                    val frameAspectRatio = if (frameRotation % 180 == 0)
                        frameWidth / frameHeight.toFloat()
                    else
                        frameHeight / frameWidth.toFloat()
                    val frameDisplaySize = RendererCommon.getDisplaySize(scalingType!!, frameAspectRatio, width, height)
                    l = (width - frameDisplaySize.x) / 2
                    t = (height - frameDisplaySize.y) / 2
                    r = l + frameDisplaySize.x
                    b = t + frameDisplaySize.y
                }
            }
        }
        surfaceViewRenderer.layout(l, t, r, b)
    }

    /**
     * Stops rendering [.videoTrack] and releases the associated acquired
     * resources (if rendering is in progress).
     */
    private fun removeRendererFromVideoTrack() {
        if (rendererAttached) {
            // XXX If WebRTCModule#mediaStreamTrackRelease has already been
            // invoked on videoTrack, then it is no longer safe to call methods
            // (e.g. addSink, removeSink) on videoTrack. It is OK to
            // skip the removeSink invocation in such a case because
            // VideoTrack#dispose() has performed it already.

            if (videoTrack != null) {
                videoTrack!!.removeSink(surfaceViewRenderer)
            }

            surfaceViewRenderer.release()
            rendererAttached = false

            // Since this WebRTCView is no longer rendering anything, make sure
            // surfaceViewRenderer displays nothing as well.
            synchronized(layoutSyncRoot) {
                frameHeight = 0
                frameRotation = 0
                frameWidth = 0
            }
            requestSurfaceViewRendererLayout()
        }
    }

    /**
     * Request that [.surfaceViewRenderer] be laid out (as soon as
     * possible) because layout-related state either of this instance or of
     * `surfaceViewRenderer` has changed.
     */
    @SuppressLint("WrongCall")
    private fun requestSurfaceViewRendererLayout() {
        // Google/WebRTC just call requestLayout() on surfaceViewRenderer when
        // they change the value of its mirror or surfaceType property.
        surfaceViewRenderer.requestLayout()
        // The above is not enough though when the video frame's dimensions or
        // rotation change. The following will suffice.
        if (!invokeIsInLayout()) {
            onLayout(
                /* changed */ false,
                getLeft(), getTop(), getRight(), getBottom()
            )
        }
    }

    /**
     * Sets the indicator which determines whether this `WebRTCView` is to
     * mirror the video represented by [.videoTrack] during its rendering.
     *
     * @param mirror If this `WebRTCView` is to mirror the video
     * represented by `videoTrack` during its rendering, `true`;
     * otherwise, `false`.
     */
    fun setMirror(mirror: Boolean) {
        if (this.mirror != mirror) {
            this.mirror = mirror
            surfaceViewRenderer.setMirror(mirror)
            // SurfaceViewRenderer takes the value of its mirror property into
            // account upon its layout.
            requestSurfaceViewRendererLayout()
        }
    }

    /**
     * In the fashion of
     * https://www.w3.org/TR/html5/embedded-content-0.html#dom-video-videowidth
     * and https://www.w3.org/TR/html5/rendering.html#video-object-fit,
     * resembles the CSS style `object-fit`.
     *
     * @param objectFit For details, refer to the documentation of the
     * `objectFit` property of the JavaScript counterpart of
     * `WebRTCView` i.e. `RTCView`.
     */
    fun setObjectFit(objectFit: String) {
        val scalingType = if ("cover" == objectFit)
            RendererCommon.ScalingType.SCALE_ASPECT_FILL
        else
            RendererCommon.ScalingType.SCALE_ASPECT_FIT

        setScalingType(scalingType)
    }

    private fun setScalingType(scalingType: RendererCommon.ScalingType) {
        synchronized(layoutSyncRoot) {
            if (this.scalingType == scalingType) {
                return
            }
            this.scalingType = scalingType
            surfaceViewRenderer.setScalingType(scalingType)
        }
        // Both this instance ant its SurfaceViewRenderer take the value of
        // their scalingType properties into account upon their layouts.
        requestSurfaceViewRendererLayout()
    }

    /**
     * Sets the `VideoTrack` to be rendered by this `WebRTCView`.
     *
     * @param videoTrack The `VideoTrack` to be rendered by this
     * `WebRTCView` or `null`.
     */
    fun setVideoTrack(videoTrack: VideoTrack?) {
        val oldVideoTrack = this.videoTrack

        if (oldVideoTrack !== videoTrack) {
            if (oldVideoTrack != null) {
                if (videoTrack == null) {
                    // If we are not going to render any stream, clean the
                    // surface.
                    cleanSurfaceViewRenderer()
                }
                removeRendererFromVideoTrack()
            }

            this.videoTrack = videoTrack

            if (videoTrack != null) {
                tryAddRendererToVideoTrack()
                if (oldVideoTrack == null) {
                    // If there was no old track, clean the surface so we start
                    // with black.
                    cleanSurfaceViewRenderer()
                }
            }
        }
    }

    /**
     * Sets the z-order of this [WebRTCView] in the stacking space of all
     * `WebRTCView`s. For more details, refer to the documentation of the
     * `zOrder` property of the JavaScript counterpart of
     * `WebRTCView` i.e. `RTCView`.
     *
     * @param zOrder The z-order to set on this `WebRTCView`.
     */
    fun setZOrder(zOrder: Int) {
        when (zOrder) {
            0 -> surfaceViewRenderer.setZOrderMediaOverlay(false)
            1 -> surfaceViewRenderer.setZOrderMediaOverlay(true)
            2 -> surfaceViewRenderer.setZOrderOnTop(true)
        }
    }

    private fun getVideoTrack(): VideoTrack? {
        return videoTrack
    }
    
    /**
     * Starts rendering [.videoTrack] if rendering is not in progress and
     * all preconditions for the start of rendering are met.
     */
    private fun tryAddRendererToVideoTrack() {
        if (!rendererAttached
            // XXX If WebRTCModule#mediaStreamTrackRelease has already been
            // invoked on videoTrack, then it is no longer safe to call
            // methods (e.g. addRenderer, removeRenderer) on videoTrack.
            && videoTrack != null
            && ViewCompat.isAttachedToWindow(this)
        ) {
            val sharedContext = EglUtil.rootEglBaseContext
            if (sharedContext == null) {
                // If SurfaceViewRenderer#init() is invoked, it will throw a
                // RuntimeException which will very likely kill the application.
                logger.error( "Failed to render a VideoTrack!")
                return
            }

            surfaceViewRenderer.init(sharedContext, rendererEvents)
            videoTrack!!.addSink(surfaceViewRenderer)

            rendererAttached = true
        }
    }

    companion object {
        /**
         * The scaling type to be utilized by default.
         *
         * The default value is in accord with
         * https://www.w3.org/TR/html5/embedded-content-0.html#the-video-element:
         *
         * In the absence of style rules to the contrary, video content should be
         * rendered inside the element's playback area such that the video content
         * is shown centered in the playback area at the largest possible size that
         * fits completely within it, with the video content's aspect ratio being
         * preserved. Thus, if the aspect ratio of the playback area does not match
         * the aspect ratio of the video, the video will be shown letterboxed or
         * pillarboxed. Areas of the element's playback area that do not contain the
         * video represent nothing.
         */
        private val DEFAULT_SCALING_TYPE = RendererCommon.ScalingType.SCALE_ASPECT_FIT

        /**
         * [View.isInLayout] as a <tt>Method</tt> to be invoked via
         * reflection in order to accommodate its lack of availability before API
         * level 18. [ViewCompat.isInLayout] is the best solution but I
         * could not make it available along with
         * [ViewCompat.isAttachedToWindow] at the time of this writing.
         */
        private val IS_IN_LAYOUT: Method?

        init {
            // IS_IN_LAYOUT
            var isInLayout: Method? = null

            try {
                val m = WebRTCView::class.java.getMethod("isInLayout")

                if (Boolean::class.javaPrimitiveType!!.isAssignableFrom(m.returnType)) {
                    isInLayout = m
                }
            } catch (e: NoSuchMethodException) {
                // Fall back to the behavior of ViewCompat#isInLayout(View).
            }

            IS_IN_LAYOUT = isInLayout
        }
    }
}