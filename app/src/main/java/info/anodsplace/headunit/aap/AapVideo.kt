package info.anodsplace.headunit.aap

import info.anodsplace.headunit.decoder.VideoFrameQueue
import info.anodsplace.headunit.utils.AppLog
import java.nio.ByteBuffer

internal class AapVideo(private val frameQueue: () -> VideoFrameQueue?) {
    
    companion object {
        // Maximum frame size - matches protocol transport buffer size (128KB)
        const val MAX_FRAME_SIZE = 131072
    }
    
    // Pre-allocated buffer for fragment reassembly - uses direct buffer to avoid extra copy
    private val fragmentBuffer = ByteBuffer.allocateDirect(MAX_FRAME_SIZE)

    fun process(message: AapMessage): Boolean {
        val queue = frameQueue() ?: run {
            AppLog.d { "Frame queue not ready, dropping video frame" }
            return true // Don't log error, queue may not be ready yet
        }

        val flags = message.flags.toInt()
        val buf = message.data
        val len = message.size

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
                    fragmentBuffer.clear()
                    val dataLen = len - 10
                    if (dataLen <= MAX_FRAME_SIZE) {
                        fragmentBuffer.put(buf, 10, dataLen)
                        return true
                    } else {
                        AppLog.e { "First fragment exceeds max frame size ($dataLen > $MAX_FRAME_SIZE)" }
                        return true
                    }
                }
            }
            8 -> {
                // Middle fragment - continue reassembly
                if (fragmentBuffer.remaining() >= len) {
                    fragmentBuffer.put(buf, 0, len)
                    return true
                } else {
                    AppLog.e { "Fragment buffer overflow, dropping frame (remaining=${fragmentBuffer.remaining()}, needed=$len)" }
                    fragmentBuffer.clear()
                    return true
                }
            }
            10 -> {
                // Last fragment - complete and queue
                if (fragmentBuffer.remaining() < len) {
                    AppLog.e { "Fragment buffer overflow on last fragment, dropping frame" }
                    fragmentBuffer.clear()
                    return true
                }
                fragmentBuffer.put(buf, 0, len)
                fragmentBuffer.flip()
                
                // For direct buffers, we need to copy to array for offer()
                val array = ByteArray(fragmentBuffer.limit())
                fragmentBuffer.get(array)
                queue.offer(array, 0, array.size)
                
                fragmentBuffer.clear()
                return true
            }
        }

        AppLog.e { "Video process error for: $message" }
        return false
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
