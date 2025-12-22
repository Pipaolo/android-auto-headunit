package info.anodsplace.headunit.decoder

import android.view.SurfaceHolder

import info.anodsplace.headunit.utils.AppLog

/**
 * Manages the video decode pipeline lifecycle.
 * 
 * Coordinates VideoFrameQueue and VideoDecodeThread creation/destruction
 * in response to Surface lifecycle events.
 */
class VideoDecoderController {

    private var frameQueue: VideoFrameQueue? = null
    private var decodeThread: VideoDecodeThread? = null

    @Volatile private var surfaceReady = false
    
    // Store last surface info for restart capability
    private var lastHolder: SurfaceHolder? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    /**
     * Called when surface becomes available for rendering.
     * Starts the decode pipeline.
     * 
     * @param holder SurfaceHolder from the activity
     * @param width Surface width in pixels
     * @param height Surface height in pixels
     */
    fun onSurfaceAvailable(holder: SurfaceHolder, width: Int, height: Int) {
        synchronized(this) {
            // Cap height at 1080p (matches original behavior)
            val cappedHeight = minOf(height, 1080)

            // Store for potential restart
            lastHolder = holder
            lastWidth = width
            lastHeight = cappedHeight

            if (decodeThread != null) {
                AppLog.i { "Decoder already running" }
                return
            }

            startDecoder(holder, width, cappedHeight)
        }
    }
    
    /**
     * Restart the decoder using the last known surface.
     * Called when a new connection starts but surface is already available.
     */
    fun restartIfSurfaceAvailable() {
        synchronized(this) {
            val holder = lastHolder
            if (holder == null) {
                AppLog.d { "Cannot restart decoder - no surface available" }
                return
            }
            
            if (decodeThread != null) {
                AppLog.i { "Decoder already running, no restart needed" }
                return
            }
            
            // Check if surface is still valid
            try {
                val surface = holder.surface
                if (surface == null || !surface.isValid) {
                    AppLog.w { "Cannot restart decoder - surface is invalid" }
                    lastHolder = null
                    return
                }
            } catch (e: Exception) {
                AppLog.w { "Cannot restart decoder - surface check failed: ${e.message}" }
                lastHolder = null
                return
            }
            
            AppLog.i { "Restarting video decoder for new connection" }
            startDecoder(holder, lastWidth, lastHeight)
        }
    }
    
    private fun startDecoder(holder: SurfaceHolder, width: Int, height: Int) {
        // Create components - capacity of 8 frames handles ~130ms at 60fps burst traffic
        frameQueue = VideoFrameQueue(capacity = 8)
        decodeThread = VideoDecodeThread(
            queue = frameQueue!!,
            surface = holder.surface,
            width = width,
            height = height
        )

        // Start decode thread
        decodeThread!!.start()
        surfaceReady = true

        AppLog.i { "Video pipeline started: ${width}x${height}" }
    }

    /**
     * Called when surface is destroyed.
     * Stops the decode pipeline and releases resources.
     * 
     * @param reason Description of why stopping (for logging)
     */
    fun stop(reason: String) {
        synchronized(this) {
            surfaceReady = false

            decodeThread?.let { thread ->
                thread.stopDecoding()
                thread.quitSafely()
                try {
                    // Wait up to 1 second for clean shutdown
                    thread.join(1000)
                } catch (e: InterruptedException) {
                    AppLog.w { "Interrupted while waiting for decode thread" }
                }
            }

            decodeThread = null
            frameQueue = null
            
            // Note: we keep lastHolder so we can restart with same surface

            AppLog.i { "Video pipeline stopped: $reason" }
        }
    }
    
    /**
     * Called when surface is destroyed - clears the stored surface reference.
     */
    fun onSurfaceDestroyed() {
        synchronized(this) {
            lastHolder = null
            lastWidth = 0
            lastHeight = 0
        }
    }

    /**
     * Get the frame queue for writing video data.
     * Returns null if pipeline is not running.
     */
    fun getFrameQueue(): VideoFrameQueue? = frameQueue

    /**
     * Check if the decode pipeline is running.
     */
    fun isRunning(): Boolean = surfaceReady && decodeThread != null

    /**
     * Get current performance statistics.
     * Returns null if pipeline is not running.
     */
    fun getStats(): VideoStats? = decodeThread?.getStats()
}
