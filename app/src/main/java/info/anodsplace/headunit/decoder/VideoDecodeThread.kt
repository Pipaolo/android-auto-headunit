package info.anodsplace.headunit.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.Surface

import java.nio.ByteBuffer

import info.anodsplace.headunit.utils.AppLog

/**
 * Dedicated thread for video decoding using MediaCodec.
 * 
 * Pulls frames from VideoFrameQueue and feeds them to the hardware decoder.
 * Runs with THREAD_PRIORITY_DISPLAY for smooth playback.
 * 
 * @param queue Source of video frames
 * @param surface Target surface for decoded video
 * @param width Video width in pixels
 * @param height Video height in pixels
 */
class VideoDecodeThread(
    private val queue: VideoFrameQueue,
    private val surface: Surface,
    private val width: Int,
    private val height: Int
) : HandlerThread("VideoDecodeThread") {

    private var codec: MediaCodec? = null
    private var codecBufferInfo = MediaCodec.BufferInfo()
    private var inputBuffers: Array<ByteBuffer>? = null
    private var codecConfigured = false

    // Reusable buffer - no allocation during decode
    private val frameBuffer = ByteArray(65536)
    
    private lateinit var monitor: VideoPerformanceMonitor

    @Volatile private var running = false

    override fun onLooperPrepared() {
        // Set thread priority for smooth playback
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)

        monitor = VideoPerformanceMonitor(queue)
        initCodec()
        running = true

        // Post decode loop to our handler
        Handler(looper).post { decodeLoop() }
    }

    private fun decodeLoop() {
        while (running) {
            try {
                // 1. Always drain output first to free buffers
                drainOutput()

                // 2. Poll queue for new frame
                val length = queue.poll(frameBuffer)

                if (length > 0) {
                    // 3. Check for SPS to configure codec
                    if (!codecConfigured && isSpsFrame(frameBuffer)) {
                        codecConfigured = true
                        AppLog.i { "Codec configured (SPS received)" }
                    }

                    // 4. Feed to codec if configured
                    if (codecConfigured) {
                        feedInput(frameBuffer, length)
                        monitor.onFrameDecoded()
                    }
                } else {
                    // Queue empty - brief sleep to avoid CPU spin
                    Thread.sleep(1)
                }

            } catch (e: IllegalStateException) {
                // Codec in bad state - try to recover
                AppLog.e { "Codec error, attempting recovery: ${e.message}" }
                resetCodec()

            } catch (e: Exception) {
                AppLog.e { "Unexpected decode error: ${e.message}" }
            }
        }

        // Cleanup when loop exits
        releaseCodec()
    }

    /**
     * Consume all available output buffers from codec.
     * Non-blocking - returns immediately if no output ready.
     */
    private fun drainOutput() {
        val codec = this.codec ?: return
        
        while (true) {
            val index = codec.dequeueOutputBuffer(codecBufferInfo, 0)
            when {
                index >= 0 -> {
                    // Render frame to surface
                    codec.releaseOutputBuffer(index, true)
                }
                index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    // API < 21: output buffers changed, but we don't cache them
                    AppLog.d { "INFO_OUTPUT_BUFFERS_CHANGED" }
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    AppLog.d { "INFO_OUTPUT_FORMAT_CHANGED" }
                }
                else -> {
                    // INFO_TRY_AGAIN_LATER or error - exit loop
                    break
                }
            }
        }
    }

    /**
     * Feed a frame to the codec input.
     * Uses short timeout to avoid blocking.
     */
    private fun feedInput(data: ByteArray, length: Int) {
        val codec = this.codec ?: return
        val buffers = this.inputBuffers ?: return

        // Short timeout - 10ms instead of original 1 second
        val inputIndex = codec.dequeueInputBuffer(10_000) // microseconds

        if (inputIndex < 0) {
            // No buffer available - will retry next loop
            AppLog.d { "No input buffer available, will retry" }
            return
        }

        val buffer = buffers[inputIndex]
        buffer.clear()
        buffer.put(data, 0, length)
        buffer.flip()

        codec.queueInputBuffer(
            inputIndex,
            0,      // offset
            length, // size
            0,      // presentationTimeUs (not used for realtime)
            0       // flags
        )
    }

    private fun initCodec() {
        try {
            codec = MediaCodec.createDecoderByType("video/avc")

            val format = MediaFormat.createVideoFormat("video/avc", width, height)

            // Request low latency if available (API 19+)
            if (Build.VERSION.SDK_INT >= 19) {
                format.setInteger("low-latency", 1)
            }

            codec!!.configure(format, surface, null, 0)
            codec!!.start()
            inputBuffers = codec!!.inputBuffers

            AppLog.i { "Codec initialized: ${width}x${height}" }

        } catch (e: Exception) {
            AppLog.e { "Failed to initialize video codec: ${e.message}" }
        }
    }

    private fun resetCodec() {
        try {
            codec?.stop()
            codec?.release()
        } catch (ignored: Exception) {
        }

        codec = null
        inputBuffers = null
        codecConfigured = false
        
        // Brief delay before reinit
        Thread.sleep(100)
        
        initCodec()
        AppLog.i { "Codec reset complete" }
    }

    private fun releaseCodec() {
        try {
            codec?.stop()
            codec?.release()
        } catch (ignored: Exception) {
        }
        
        codec = null
        inputBuffers = null
        codecConfigured = false
        
        AppLog.i { "Codec released" }
    }

    /**
     * Check if frame starts with SPS NAL unit (type 7).
     */
    private fun isSpsFrame(data: ByteArray): Boolean {
        // NAL unit header: data[4] contains type in lower 5 bits
        // Type 7 = Sequence Parameter Set
        return data.size > 4 && (data[4].toInt() and 0x1f) == 7
    }

    /**
     * Signal decode loop to stop. Thread will exit after current iteration.
     */
    fun stopDecoding() {
        running = false
    }

    /**
     * Get current performance statistics.
     */
    fun getStats(): VideoStats = monitor.getStats()
}
