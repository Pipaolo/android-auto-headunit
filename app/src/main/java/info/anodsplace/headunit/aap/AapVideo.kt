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

    // Cache for SPS/PPS/IDR NAL units - these are needed to configure and start the decoder
    // We buffer them so they're available immediately when the surface becomes ready
    @Volatile private var cachedSps: ByteArray? = null
    @Volatile private var cachedPps: ByteArray? = null
    @Volatile private var cachedIdr: ByteArray? = null  // First IDR keyframe
    @Volatile private var cachedSpsPpsSent = false

    // For debugging - count frames processed
    private var processedFrames = 0

    fun process(message: AapMessage): Boolean {
        val flags = message.flags.toInt()
        val buf = message.data
        val len = message.size

        // Log first few frames for debugging
        if (processedFrames < 10) {
            val hexDump = buf.take(minOf(16, len)).joinToString(" ") { String.format("%02X", it) }
            android.util.Log.w("VideoDebug", "AapVideo.process: flags=$flags, len=$len, type=${message.type}, first16bytes=$hexDump")
            processedFrames++
        }

        // For complete frames (flag 11), cache SPS/PPS even if queue not ready
        // Must check BOTH offset 10 (standard) and offset 2 (message.type==1 case)
        if (flags == 11) {
            if (isValidNalUnit(buf, 10)) {
                cacheSpsPpsIfNeeded(buf, 10, len - 10)
            } else if (message.type == 1 && isValidNalUnit(buf, 2)) {
                cacheSpsPpsIfNeeded(buf, message.dataOffset, len - message.dataOffset)
            } else {
                // Debug: show what's at both offsets when neither matches
                val at2 = if (len > 6) buf.slice(2..5).joinToString(" ") { String.format("%02X", it) } else "N/A"
                val at10 = if (len > 14) buf.slice(10..13).joinToString(" ") { String.format("%02X", it) } else "N/A"
                android.util.Log.w("VideoDebug", "No NAL start code found - at offset 2: [$at2], at offset 10: [$at10]")
            }
        }

        val queue = frameQueue()
        if (queue == null) {
            android.util.Log.w("VideoDebug", "Frame queue NULL, caching attempted, dropping frame")
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
     * Scans for ALL NAL units in the data since SPS and PPS may be concatenated.
     */
    private fun cacheSpsPpsIfNeeded(data: ByteArray, offset: Int, length: Int) {
        if (length < 5) return

        val end = offset + length
        var pos = offset

        // Scan for all NAL start codes (00 00 00 01) in the data
        while (pos < end - 4) {
            // Look for NAL start code
            if (data[pos].toInt() == 0 && data[pos + 1].toInt() == 0 &&
                data[pos + 2].toInt() == 0 && data[pos + 3].toInt() == 1) {
                
                val nalType = data[pos + 4].toInt() and 0x1f
                
                // Find next NAL start code to determine this NAL's length
                var nextNalPos = pos + 4
                while (nextNalPos < end - 3) {
                    if (data[nextNalPos].toInt() == 0 && data[nextNalPos + 1].toInt() == 0 &&
                        data[nextNalPos + 2].toInt() == 0 && data[nextNalPos + 3].toInt() == 1) {
                        break
                    }
                    nextNalPos++
                }
                // If no next NAL found, this NAL extends to end
                if (nextNalPos >= end - 3) nextNalPos = end
                
                val nalLength = nextNalPos - pos
                android.util.Log.w("VideoDebug", "Found NAL at pos=$pos, type=$nalType, length=$nalLength")
                
                when (nalType) {
                    NAL_TYPE_SPS -> {
                        synchronized(cacheLock) {
                            cachedSps = data.copyOfRange(pos, pos + nalLength)
                        }
                        android.util.Log.w("VideoDebug", "CACHED SPS NAL unit ($nalLength bytes)")
                        AppLog.i { "Cached SPS NAL unit ($nalLength bytes)" }
                    }
                    NAL_TYPE_PPS -> {
                        synchronized(cacheLock) {
                            cachedPps = data.copyOfRange(pos, pos + nalLength)
                        }
                        android.util.Log.w("VideoDebug", "CACHED PPS NAL unit ($nalLength bytes)")
                        AppLog.i { "Cached PPS NAL unit ($nalLength bytes)" }
                    }
                    NAL_TYPE_IDR -> {
                        // Cache first IDR so decoder can start immediately when queue is ready
                        synchronized(cacheLock) {
                            if (cachedIdr == null) {
                                cachedIdr = data.copyOfRange(pos, pos + nalLength)
                                android.util.Log.w("VideoDebug", "CACHED IDR keyframe ($nalLength bytes)")
                                AppLog.i { "Cached IDR keyframe ($nalLength bytes)" }
                            }
                        }
                    }
                }
                
                pos = nextNalPos
            } else {
                pos++
            }
        }
    }

    /**
     * Inject cached SPS/PPS/IDR into the queue when it first becomes available.
     * This ensures the decoder can configure and start immediately without waiting for
     * the next keyframe from the phone.
     */
    private fun injectCachedSpsPps(queue: VideoFrameQueue) {
        synchronized(cacheLock) {
            if (cachedSpsPpsSent) return

            val sps = cachedSps
            val pps = cachedPps
            val idr = cachedIdr

            android.util.Log.w("VideoDebug", "injectCachedSpsPps: sps=${sps?.size ?: "null"}, pps=${pps?.size ?: "null"}, idr=${idr?.size ?: "null"}")

            if (sps != null && pps != null) {
                android.util.Log.w("VideoDebug", "INJECTING cached SPS (${sps.size} bytes) + PPS (${pps.size} bytes) + IDR (${idr?.size ?: 0} bytes)")
                AppLog.i { "Injecting cached SPS/PPS/IDR into queue for immediate decoder start" }
                queue.offer(sps, 0, sps.size)
                queue.offer(pps, 0, pps.size)
                // Also inject the first IDR keyframe if we have it
                if (idr != null) {
                    queue.offer(idr, 0, idr.size)
                }
                cachedSpsPpsSent = true
            } else {
                android.util.Log.w("VideoDebug", "Cannot inject - missing SPS or PPS")
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
            cachedIdr = null
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
