package info.anodsplace.headunit.decoder

/**
 * Lock-free single-producer/single-consumer ring buffer for video frames.
 * 
 * Pre-allocates all memory at construction time to eliminate GC pressure
 * during streaming. When queue is full, drops oldest frame to bound latency.
 * 
 * @param capacity Number of frame slots (default 6 for ~100ms at 60fps)
 * @param maxFrameSize Maximum bytes per frame (default 512KB to handle high-resolution I-frames)
 */
class VideoFrameQueue(
    private val capacity: Int = 6,
    private val maxFrameSize: Int = MAX_FRAME_SIZE
) {
    
    companion object {
        // Maximum frame size - 512KB to handle large I-frames at high resolutions (1080p+)
        const val MAX_FRAME_SIZE = 524288
    }
    // Pre-allocated frame slots - no runtime allocation
    private val frames = Array(capacity) { ByteArray(maxFrameSize) }
    private val sizes = IntArray(capacity)

    // Lock-free indices (volatile for visibility across threads)
    @Volatile private var writeIndex = 0
    @Volatile private var readIndex = 0

    // Statistics for monitoring
    @Volatile var droppedFrames = 0L
        private set

    /**
     * Queue a frame for decoding. Called by network thread.
     * Never blocks - if queue is full, drops oldest frame.
     * 
     * @param data Source byte array containing frame data
     * @param offset Start offset in source array
     * @param length Number of bytes to copy
     * @return true if frame was queued (always returns true)
     */
    fun offer(data: ByteArray, offset: Int, length: Int): Boolean {
        val nextWrite = (writeIndex + 1) % capacity

        if (nextWrite == readIndex) {
            // Queue full - drop oldest frame to bound latency
            readIndex = (readIndex + 1) % capacity
            droppedFrames++
        }

        // Copy into pre-allocated slot
        val copyLength = minOf(length, maxFrameSize)
        System.arraycopy(data, offset, frames[writeIndex], 0, copyLength)
        sizes[writeIndex] = copyLength

        // Publish (volatile write ensures visibility to consumer)
        writeIndex = nextWrite
        return true
    }

    /**
     * Retrieve next frame for decoding. Called by decode thread.
     * 
     * @param outBuffer Destination buffer (must be at least maxFrameSize)
     * @return Number of bytes copied, or -1 if queue is empty
     */
    fun poll(outBuffer: ByteArray): Int {
        if (readIndex == writeIndex) {
            return -1 // Empty
        }

        val length = sizes[readIndex]
        System.arraycopy(frames[readIndex], 0, outBuffer, 0, length)

        // Consume (volatile write ensures visibility to producer)
        readIndex = (readIndex + 1) % capacity
        return length
    }

    /**
     * Check if queue is empty.
     */
    fun isEmpty(): Boolean = readIndex == writeIndex

    /**
     * Get current number of queued frames.
     */
    fun size(): Int {
        val w = writeIndex
        val r = readIndex
        return if (w >= r) w - r else capacity - r + w
    }

    /**
     * Clear all queued frames. Not thread-safe - call only when stopped.
     */
    fun clear() {
        readIndex = 0
        writeIndex = 0
    }
}
