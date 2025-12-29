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
 * IMPORTANT: Codec is NOT initialized until SPS NAL unit is received.
 * This ensures proper CSD (Codec Specific Data) configuration.
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
    private var codecStarted = false

    // Reusable buffer - no allocation during decode
    private val frameBuffer = ByteArray(VideoFrameQueue.MAX_FRAME_SIZE)

    // Buffered SPS/PPS for codec configuration
    private var bufferedSps: ByteArray? = null
    private var bufferedPps: ByteArray? = null

    private lateinit var monitor: VideoPerformanceMonitor

    @Volatile private var running = false

    // When true, drop all frames until next keyframe to recover from decode errors
    private var waitingForKeyframe = false

    override fun onLooperPrepared() {
        // Set high thread priority for smooth video decoding
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)

        monitor = VideoPerformanceMonitor(queue)
        // NOTE: Codec is NOT initialized here - we wait for SPS NAL unit
        running = true

        AppLog.i { "VideoDecodeThread started, waiting for SPS to configure codec" }

        // Post decode loop to our handler
        Handler(looper).post { decodeLoop() }
    }

    private fun decodeLoop() {
        var emptyPollCount = 0

        while (running) {
            try {
                // 1. Always drain output first to free buffers and render frames (if codec started)
                if (codecStarted) {
                    drainOutput()
                }

                // 2. Check if we're falling behind and need to skip to next keyframe
                // Only check AFTER codec is started to avoid triggering during startup
                // Use threshold of 12 (80% of queue capacity of 15) to avoid thrashing
                val queueSize = queue.size()
                if (codecStarted && queueSize > 12 && !waitingForKeyframe) {
                    // Queue backing up severely - skip to next keyframe to catch up
                    waitingForKeyframe = true
                    AppLog.w { "Queue backing up ($queueSize frames), waiting for keyframe" }
                }

                // 3. Poll queue for new frame
                val length = queue.poll(frameBuffer)

                if (length > 0) {
                    emptyPollCount = 0

                    // Validate NAL start code first
                    if (!hasValidStartCode(frameBuffer)) {
                        val hexDump = frameBuffer.take(8).joinToString(" ") { String.format("%02X", it) }
                        AppLog.w { "Invalid NAL start code, skipping (first 8 bytes: $hexDump)" }
                        continue
                    }

                    val nalType = getNalType(frameBuffer)

                    // 4. Buffer SPS/PPS for codec configuration
                    // We need BOTH SPS and PPS before initializing codec
                    when (nalType) {
                        NAL_TYPE_SPS -> {
                            bufferedSps = frameBuffer.copyOf(length)
                            AppLog.i { "Buffered SPS NAL unit ($length bytes)" }

                            // Try to initialize codec if we have BOTH SPS and PPS
                            if (!codecStarted && bufferedPps != null) {
                                tryInitCodecWithCsd()
                            }
                            continue // Don't feed SPS to codec as regular frame
                        }
                        NAL_TYPE_PPS -> {
                            bufferedPps = frameBuffer.copyOf(length)
                            AppLog.i { "Buffered PPS NAL unit ($length bytes)" }

                            // Try to initialize codec if we now have BOTH SPS and PPS
                            if (!codecStarted && bufferedSps != null) {
                                tryInitCodecWithCsd()
                            }
                            continue // Don't feed PPS to codec as regular frame
                        }
                    }

                    // 5. If codec not started, we can only buffer config NALs - skip other frames
                    if (!codecStarted) {
                        AppLog.d { "Dropping frame (NAL type $nalType) - codec not configured yet" }
                        continue
                    }

                    val isKeyframe = isKeyFrame(frameBuffer)

                    // 6. If waiting for keyframe, drop frames until we see one
                    if (waitingForKeyframe) {
                        if (isKeyframe) {
                            waitingForKeyframe = false
                            AppLog.i { "Keyframe received, resuming decode" }
                        } else {
                            // Drop this P/B frame - can't decode without reference frames
                            continue
                        }
                    }

                    // 7. Feed to codec
                    if (feedInput(frameBuffer, length)) {
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
     * Try to initialize codec with buffered SPS/PPS as CSD buffers.
     * Called when we have both SPS and PPS buffered.
     */
    private fun tryInitCodecWithCsd() {
        val sps = bufferedSps
        val pps = bufferedPps

        if (sps == null) {
            AppLog.d { "Cannot init codec - no SPS buffered yet" }
            return
        }

        if (pps == null) {
            AppLog.d { "Cannot init codec - no PPS buffered yet" }
            return
        }

        AppLog.i { "Initializing codec with SPS (${sps.size} bytes) + PPS (${pps.size} bytes)" }

        try {
            codec = MediaCodec.createDecoderByType("video/avc")

            val format = MediaFormat.createVideoFormat("video/avc", width, height)

            // Set CSD-0 (SPS) - required for decoder configuration
            // Using string literal for API 16+ compatibility (MediaFormat.KEY_CSD_0 not in all SDKs)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))

            // Set CSD-1 (PPS) - required for proper decoding
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))

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
            codecStarted = true

            AppLog.i { "Codec initialized with CSD: ${width}x${height}, SDK=${Build.VERSION.SDK_INT}" }

        } catch (e: Exception) {
            AppLog.e { "Failed to initialize codec with CSD: ${e.message}" }
            codec?.release()
            codec = null
            codecStarted = false
        }
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
     * Returns true if frame was successfully queued, false if no buffer available.
     */
    private fun feedInput(data: ByteArray, length: Int): Boolean {
        val codec = this.codec ?: return false
        val buffers = this.inputBuffers ?: return false

        // Use 2ms timeout - short for low latency but gives codec a bit of time
        val inputIndex = codec.dequeueInputBuffer(2_000) // microseconds

        if (inputIndex < 0) {
            // No buffer available - frame will be lost (rare, only when codec is overwhelmed)
            return false
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
        return true
    }

    /**
     * Reset codec after error. Re-initializes with buffered CSD if available.
     */
    private fun resetCodec() {
        try {
            codec?.stop()
            codec?.release()
        } catch (ignored: Exception) {
        }

        codec = null
        inputBuffers = null
        codecStarted = false
        waitingForKeyframe = true  // Need keyframe after reset

        // Brief delay before reinit
        Thread.sleep(100)

        // Try to reinit with buffered CSD if we have it
        if (bufferedSps != null) {
            tryInitCodecWithCsd()
        }
        AppLog.i { "Codec reset complete, waiting for keyframe" }
    }

    private fun releaseCodec() {
        try {
            codec?.stop()
            codec?.release()
        } catch (ignored: Exception) {
        }

        codec = null
        inputBuffers = null
        codecStarted = false
        bufferedSps = null
        bufferedPps = null

        AppLog.i { "Codec released" }
    }

    /**
     * Check for valid NAL unit start code (0x00 0x00 0x00 0x01).
     */
    private fun hasValidStartCode(data: ByteArray): Boolean {
        return data.size > 4 &&
            data[0].toInt() == 0 &&
            data[1].toInt() == 0 &&
            data[2].toInt() == 0 &&
            data[3].toInt() == 1
    }

    /**
     * Get NAL unit type from data. Assumes valid start code.
     */
    private fun getNalType(data: ByteArray): Int {
        return data[4].toInt() and 0x1f
    }

    /**
     * Check if frame is a keyframe (IDR).
     * Validates start code before checking NAL type.
     * NAL type 5 = IDR (keyframe)
     */
    private fun isKeyFrame(data: ByteArray): Boolean {
        if (!hasValidStartCode(data)) return false
        val nalType = getNalType(data)
        return nalType == NAL_TYPE_IDR
    }

    companion object {
        // NAL unit types
        private const val NAL_TYPE_IDR = 5      // Keyframe
        private const val NAL_TYPE_SPS = 7      // Sequence Parameter Set
        private const val NAL_TYPE_PPS = 8      // Picture Parameter Set
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
