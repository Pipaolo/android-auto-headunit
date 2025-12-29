package info.anodsplace.headunit.aap

import info.anodsplace.headunit.decoder.VideoFrameQueue
import info.anodsplace.headunit.utils.AppLog
import java.nio.ByteBuffer

internal class AapVideo(private val frameQueue: () -> VideoFrameQueue?) {

    companion object {
        // Maximum frame size - 512KB to handle large I-frames at high resolutions (1080p+)
        const val MAX_FRAME_SIZE = 524288

        // NAL unit types
        private const val NAL_TYPE_IDR = 5      // Keyframe
        private const val NAL_TYPE_SPS = 7      // Sequence Parameter Set
        private const val NAL_TYPE_PPS = 8      // Picture Parameter Set
    }

    // Lock for fragment reassembly - protects fragmentBuffer, fragmentArray, and fragment state
    private val fragmentLock = Object()

    // Pre-allocated buffer for fragment reassembly - uses direct buffer to avoid extra copy
    private val fragmentBuffer = ByteBuffer.allocateDirect(MAX_FRAME_SIZE)

    // Pre-allocated array for copying from direct buffer to queue - avoids per-frame allocation
    private val fragmentArray = ByteArray(MAX_FRAME_SIZE)

    // Fragment state tracking to detect corruption
    private var fragmentInProgress = false
    private var fragmentExpectedChannel = -1

    // Lock for SPS/PPS cache - protects cachedSps, cachedPps, cachedSpsPpsSent
    private val cacheLock = Object()

    // Cache for SPS/PPS NAL units - these are needed to configure the decoder
    // We buffer them so they're available immediately when the surface becomes ready
    @Volatile private var cachedSps: ByteArray? = null
    @Volatile private var cachedPps: ByteArray? = null
    @Volatile private var cachedSpsPpsSent = false

    fun process(message: AapMessage): Boolean {
        val flags = message.flags.toInt()
        val buf = message.data
        val len = message.size

        // For complete frames (flag 11), cache SPS/PPS even if queue not ready
        if (flags == 11 && isValidNalUnit(buf, 10)) {
            cacheSpsPpsIfNeeded(buf, 10, len - 10)
        }

        val queue = frameQueue()
        if (queue == null) {
            AppLog.d { "Frame queue not ready, dropping video frame" }
            return true // Don't log error, queue may not be ready yet
        }
        
        // Inject cached SPS/PPS when queue first becomes available
        injectCachedSpsPps(queue)

        when (flags) {
            11 -> {
                // Complete frame (not fragmented)
                if (isValidNalUnit(buf, 10)) {
                    queue.offer(buf, 10, len - 10)
                    return true
                } else if (message.type == 1 && isValidNalUnit(buf, 2)) {
                    queue.offer(buf, message.dataOffset, len - message.dataOffset)
                    return true
                }
            }
            9 -> {
                // First fragment - start reassembly
                if (isValidNalUnit(buf, 10)) {
                    synchronized(fragmentLock) {
                        // Reset any previous incomplete fragment
                        if (fragmentInProgress) {
                            AppLog.w { "Starting new fragment while previous incomplete, discarding old data" }
                        }
                        fragmentBuffer.clear()
                        val dataLen = len - 10
                        if (dataLen <= MAX_FRAME_SIZE) {
                            fragmentBuffer.put(buf, 10, dataLen)
                            fragmentInProgress = true
                            fragmentExpectedChannel = message.channel
                        } else {
                            AppLog.e { "First fragment exceeds max frame size ($dataLen > $MAX_FRAME_SIZE)" }
                            fragmentInProgress = false
                        }
                    }
                    return true
                }
            }
            8 -> {
                // Middle fragment - continue reassembly
                synchronized(fragmentLock) {
                    if (!fragmentInProgress) {
                        // No first fragment received - discard this orphan fragment
                        AppLog.w { "Middle fragment received without first fragment, discarding" }
                        return true
                    }
                    if (message.channel != fragmentExpectedChannel) {
                        AppLog.w { "Middle fragment channel mismatch (expected=$fragmentExpectedChannel, got=${message.channel}), discarding" }
                        fragmentInProgress = false
                        fragmentBuffer.clear()
                        return true
                    }
                    if (fragmentBuffer.remaining() >= len) {
                        fragmentBuffer.put(buf, 0, len)
                    } else {
                        AppLog.e { "Fragment buffer overflow, dropping frame (remaining=${fragmentBuffer.remaining()}, needed=$len)" }
                        fragmentInProgress = false
                        fragmentBuffer.clear()
                    }
                }
                return true
            }
            10 -> {
                // Last fragment - complete and queue
                synchronized(fragmentLock) {
                    if (!fragmentInProgress) {
                        // No first fragment received - discard
                        AppLog.w { "Last fragment received without first fragment, discarding" }
                        return true
                    }
                    if (message.channel != fragmentExpectedChannel) {
                        AppLog.w { "Last fragment channel mismatch, discarding" }
                        fragmentInProgress = false
                        fragmentBuffer.clear()
                        return true
                    }
                    if (fragmentBuffer.remaining() < len) {
                        AppLog.e { "Fragment buffer overflow on last fragment, dropping frame" }
                        fragmentInProgress = false
                        fragmentBuffer.clear()
                        return true
                    }
                    fragmentBuffer.put(buf, 0, len)
                    fragmentBuffer.flip()

                    // Use pre-allocated array to avoid per-frame allocation (important for Android 4.3 GC)
                    val frameSize = fragmentBuffer.limit()
                    fragmentBuffer.get(fragmentArray, 0, frameSize)

                    // Cache SPS/PPS from reassembled frames too
                    cacheSpsPpsIfNeeded(fragmentArray, 0, frameSize)

                    queue.offer(fragmentArray, 0, frameSize)

                    // Reset fragment state
                    fragmentInProgress = false
                    fragmentBuffer.clear()
                }
                return true
            }
        }

        AppLog.e { "Video process error for: $message" }
        return false
    }
    
    /**
     * Cache SPS and PPS NAL units for later injection when decoder starts.
     * These are essential for decoder configuration.
     */
    private fun cacheSpsPpsIfNeeded(data: ByteArray, offset: Int, length: Int) {
        if (length < 5) return

        val nalType = data[offset + 4].toInt() and 0x1f

        when (nalType) {
            NAL_TYPE_SPS -> {
                synchronized(cacheLock) {
                    cachedSps = data.copyOfRange(offset, offset + length)
                }
                AppLog.i { "Cached SPS NAL unit (${length} bytes)" }
            }
            NAL_TYPE_PPS -> {
                synchronized(cacheLock) {
                    cachedPps = data.copyOfRange(offset, offset + length)
                }
                AppLog.i { "Cached PPS NAL unit (${length} bytes)" }
            }
        }
    }

    /**
     * Inject cached SPS/PPS into the queue when it first becomes available.
     * This ensures the decoder can configure immediately without waiting for
     * the next keyframe from the phone.
     */
    private fun injectCachedSpsPps(queue: VideoFrameQueue) {
        synchronized(cacheLock) {
            if (cachedSpsPpsSent) return

            val sps = cachedSps
            val pps = cachedPps

            if (sps != null && pps != null) {
                AppLog.i { "Injecting cached SPS/PPS into queue for immediate decoder config" }
                queue.offer(sps, 0, sps.size)
                queue.offer(pps, 0, pps.size)
                cachedSpsPpsSent = true
            }
        }
    }

    /**
     * Reset cached state when connection is closed.
     */
    fun reset() {
        synchronized(cacheLock) {
            cachedSps = null
            cachedPps = null
            cachedSpsPpsSent = false
        }
        synchronized(fragmentLock) {
            fragmentBuffer.clear()
            fragmentInProgress = false
            fragmentExpectedChannel = -1
        }
    }

    /**
     * Check for valid NAL unit start code (0x00 0x00 0x00 0x01)
     */
    private fun isValidNalUnit(buf: ByteArray, offset: Int): Boolean {
        return buf.size > offset + 3 &&
            buf[offset].toInt() == 0 && 
            buf[offset + 1].toInt() == 0 &&
            buf[offset + 2].toInt() == 0 && 
            buf[offset + 3].toInt() == 1
    }
}
