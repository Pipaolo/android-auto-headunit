package info.anodsplace.headunit.decoder

import info.anodsplace.headunit.utils.AppLog

/**
 * Monitors video decode performance and logs statistics.
 * 
 * Tracks frames decoded, frames dropped, and queue depth.
 * Logs warnings when performance degrades.
 * 
 * @param queue The frame queue to monitor
 * @param logIntervalMs Minimum time between log messages (default 5 seconds)
 */
class VideoPerformanceMonitor(
    private val queue: VideoFrameQueue,
    private val logIntervalMs: Long = 5000
) {
    private var lastLogTime = 0L
    private var lastDroppedCount = 0L
    
    @Volatile var framesDecoded = 0L
        private set

    /**
     * Called by decode thread after each successful frame decode.
     */
    fun onFrameDecoded() {
        framesDecoded++
        maybeLogStats()
    }

    private fun maybeLogStats() {
        val now = System.currentTimeMillis()
        if (now - lastLogTime < logIntervalMs) return

        val dropped = queue.droppedFrames
        val newDrops = dropped - lastDroppedCount
        val queueDepth = queue.size()

        // Only log if there are issues worth noting
        if (newDrops > 0 || queueDepth > 4) {
            AppLog.w {
                "Video stats: decoded=$framesDecoded, " +
                "dropped=$newDrops (total=$dropped), " +
                "queue=$queueDepth/8"
            }
        }

        lastDroppedCount = dropped
        lastLogTime = now
    }

    /**
     * Get current performance statistics.
     */
    fun getStats(): VideoStats = VideoStats(
        framesDecoded = framesDecoded,
        framesDropped = queue.droppedFrames,
        queueDepth = queue.size()
    )

    /**
     * Reset statistics. Call when restarting decode.
     */
    fun reset() {
        framesDecoded = 0
        lastDroppedCount = 0
        lastLogTime = 0
    }
}

/**
 * Snapshot of video performance statistics.
 */
data class VideoStats(
    val framesDecoded: Long,
    val framesDropped: Long,
    val queueDepth: Int
) {
    /**
     * Fraction of frames dropped (0.0 to 1.0).
     * Values > 0.05 indicate device is too slow.
     */
    val dropRate: Float
        get() = if (framesDecoded > 0) {
            framesDropped.toFloat() / (framesDecoded + framesDropped)
        } else 0f
}
