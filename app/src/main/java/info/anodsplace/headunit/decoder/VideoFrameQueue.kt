package info.anodsplace.headunit.decoder

/**
 * Thread-safe single-producer/single-consumer ring buffer for video frames.
 *
 * Pre-allocates all memory at construction time to eliminate GC pressure
 * during streaming. When queue is full, drops oldest frame to bound latency.
 *
 * Uses lightweight synchronization to ensure memory visibility between
 * producer (USB callback thread) and consumer (decode thread).
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

    // Indices protected by lock for proper memory ordering
    private val lock = Object()
    private var writeIndex = 0
    private var readIndex = 0

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
        synchronized(lock) {
            val currentWrite = writeIndex
            val nextWrite = (currentWrite + 1) % capacity

            if (nextWrite == readIndex) {
                // Queue full - drop oldest frame to bound latency
                readIndex = (readIndex + 1) % capacity
                droppedFrames++
            }

            // Copy into pre-allocated slot
            val copyLength = minOf(length, maxFrameSize)
            System.arraycopy(data, offset, frames[currentWrite], 0, copyLength)
            sizes[currentWrite] = copyLength

            // Publish - synchronized block ensures memory barrier
            writeIndex = nextWrite
        }
        return true
    }

    /**
     * Retrieve next frame for decoding. Called by decode thread.
     *
     * @param outBuffer Destination buffer (must be at least maxFrameSize)
     * @return Number of bytes copied, or -1 if queue is empty
     */
    fun poll(outBuffer: ByteArray): Int {
        synchronized(lock) {
            if (readIndex == writeIndex) {
                return -1 // Empty
            }

            val currentRead = readIndex
            val length = sizes[currentRead]
            System.arraycopy(frames[currentRead], 0, outBuffer, 0, length)

            // Consume - synchronized block ensures memory barrier
            readIndex = (currentRead + 1) % capacity
            return length
        }
    }

    /**
     * Check if queue is empty.
     */
    fun isEmpty(): Boolean = synchronized(lock) { readIndex == writeIndex }

    /**
     * Get current number of queued frames.
     */
    fun size(): Int = synchronized(lock) {
        val w = writeIndex
        val r = readIndex
        if (w >= r) w - r else capacity - r + w
    }

    /**
     * Clear all queued frames.
     */
    fun clear() {
        synchronized(lock) {
            readIndex = 0
            writeIndex = 0
        }
    }
}
