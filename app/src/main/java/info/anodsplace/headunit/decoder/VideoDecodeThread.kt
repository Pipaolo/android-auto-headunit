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
    private val frameBuffer = ByteArray(VideoFrameQueue.MAX_FRAME_SIZE)
    
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
        var emptyPollCount = 0

        while (running) {
            try {
                // 1. Always drain output first to free buffers and render frames
                drainOutput()

                // 2. Check if we're falling behind - if queue is backing up, skip non-keyframes
                val queueSize = queue.size()
                val shouldSkipNonKeyframes = queueSize > 4

                // 3. Poll queue for new frame
                val length = queue.poll(frameBuffer)

                if (length > 0) {
                    emptyPollCount = 0

                    // 4. Check for SPS to configure codec
                    if (!codecConfigured && isSpsFrame(frameBuffer)) {
                        codecConfigured = true
                        AppLog.i { "Codec configured (SPS received)" }
                    }

                    // 5. When falling behind, only decode keyframes (I-frames) to catch up
                    val isKeyframe = isKeyFrame(frameBuffer)
                    if (shouldSkipNonKeyframes && !isKeyframe) {
                        // Skip this P/B-frame to catch up
                        continue
                    }

                    // 6. Feed to codec if configured
                    if (codecConfigured) {
                        feedInput(frameBuffer, length)
                        monitor.onFrameDecoded()
                    }
                } else {
                    // Queue empty - use adaptive sleep to balance CPU usage and latency
                    emptyPollCount++
                    if (emptyPollCount > 10) {
                        // After many empty polls, sleep longer to save CPU
                        Thread.sleep(2)
                    }
                    // Otherwise, tight loop for low latency
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
     * Uses vsync-aligned rendering on API 21+ for smoother playback.
     */
    private fun drainOutput() {
        val codec = this.codec ?: return

        while (true) {
            val index = codec.dequeueOutputBuffer(codecBufferInfo, 0)
            when {
                index >= 0 -> {
                    // Render frame immediately for lowest latency in real-time streaming
                    // Vsync alignment adds latency that hurts A/V sync
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
     * Uses minimal timeout for low latency.
     */
    private fun feedInput(data: ByteArray, length: Int) {
        val codec = this.codec ?: return
        val buffers = this.inputBuffers ?: return

        // Very short timeout - 1ms for minimal latency
        val inputIndex = codec.dequeueInputBuffer(1_000) // microseconds

        if (inputIndex < 0) {
            // No buffer available - will retry next loop
            return
        }

        val buffer = buffers[inputIndex]
        buffer.clear()
        buffer.put(data, 0, length)

        codec.queueInputBuffer(
            inputIndex,
            0,      // offset
            length, // size
            System.nanoTime() / 1000, // presentationTimeUs for proper frame timing
            0       // flags
        )
    }

    private fun initCodec() {
        try {
            codec = MediaCodec.createDecoderByType("video/avc")

            val format = MediaFormat.createVideoFormat("video/avc", width, height)

            // Low latency optimizations for real-time video
            if (Build.VERSION.SDK_INT >= 19) {
                format.setInteger("low-latency", 1)
            }
            
            if (Build.VERSION.SDK_INT >= 23) {
                // Request realtime priority (API 23+)
                format.setInteger(MediaFormat.KEY_PRIORITY, 0) // 0 = realtime
                // Request low latency mode
                format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
            }
            
            if (Build.VERSION.SDK_INT >= 30) {
                // Request low latency mode explicitly (API 30+)
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            
            // Set max input size hint to help codec allocate appropriate buffers
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, VideoFrameQueue.MAX_FRAME_SIZE)

            codec!!.configure(format, surface, null, 0)
            codec!!.start()
            inputBuffers = codec!!.inputBuffers

            AppLog.i { "Codec initialized: ${width}x${height}, SDK=${Build.VERSION.SDK_INT}" }

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
     * Check if frame is a keyframe (IDR or SPS/PPS).
     * NAL types: 5 = IDR (keyframe), 7 = SPS, 8 = PPS
     */
    private fun isKeyFrame(data: ByteArray): Boolean {
        if (data.size <= 4) return false
        val nalType = data[4].toInt() and 0x1f
        return nalType == 5 || nalType == 7 || nalType == 8
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
