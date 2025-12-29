package info.anodsplace.headunit.utils

import android.os.Process
import info.anodsplace.headunit.aap.AapMessage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Dispatches AAP messages to dedicated threads per message type.
 * Decouples message processing from the USB read path to prevent blocking.
 */
class MessageDispatcher {

    enum class Type { AUDIO, VIDEO, CONTROL }

    data class QueuedMessage(
        val channel: Int,
        val message: AapMessage,
        val timestamp: Long = System.nanoTime()
    )

    private class DispatcherThread(
        val name: String,
        val priority: Int,
        val capacity: Int
    ) {
        val queue = LinkedBlockingQueue<QueuedMessage>(capacity)
        var thread: Thread? = null
        @Volatile var running = false
        @Volatile var callback: ((AapMessage) -> Unit)? = null
        @Volatile var droppedCount = 0L
    }

    private val dispatchers = mapOf(
        Type.AUDIO to DispatcherThread(
            name = "AAP-Audio-Dispatch",
            priority = Process.THREAD_PRIORITY_URGENT_AUDIO,
            capacity = 128  // ~2 seconds of audio frames, absorbs USB jitter
        ),
        Type.VIDEO to DispatcherThread(
            name = "AAP-Video-Dispatch",
            priority = Process.THREAD_PRIORITY_DISPLAY,
            capacity = 30  // ~1 second at 30fps
        ),
        Type.CONTROL to DispatcherThread(
            name = "AAP-Control-Dispatch",
            priority = Process.THREAD_PRIORITY_FOREGROUND,
            capacity = 128  // touch events, control messages
        )
    )

    fun setCallback(type: Type, callback: ((AapMessage) -> Unit)?) {
        dispatchers[type]?.callback = callback
    }

    /**
     * Dispatch a message to the appropriate queue.
     * This is non-blocking - if queue is full, oldest message is dropped.
     */
    fun dispatch(type: Type, channel: Int, message: AapMessage) {
        val dispatcher = dispatchers[type] ?: return
        if (!dispatcher.running) return

        val queued = QueuedMessage(channel, message)
        
        // Try to add to queue, drop oldest if full
        while (!dispatcher.queue.offer(queued)) {
            val dropped = dispatcher.queue.poll()
            if (dropped != null) {
                dispatcher.droppedCount++
                if (dispatcher.droppedCount % 100 == 1L) {
                    AppLog.w { "${dispatcher.name}: Queue full, dropped ${dispatcher.droppedCount} messages total" }
                }
            }
        }
    }

    fun start() {
        AppLog.i { "Starting MessageDispatcher" }
        dispatchers.values.forEach { dispatcher ->
            if (dispatcher.running) return@forEach
            
            dispatcher.running = true
            dispatcher.droppedCount = 0
            dispatcher.thread = createThread(dispatcher).also { it.start() }
        }
    }

    fun stop() {
        AppLog.i { "Stopping MessageDispatcher" }
        dispatchers.values.forEach { dispatcher ->
            dispatcher.running = false
        }
        
        // Interrupt and join all threads
        dispatchers.values.forEach { dispatcher ->
            dispatcher.thread?.let { thread ->
                thread.interrupt()
                try {
                    thread.join(500)
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }
            dispatcher.thread = null
            dispatcher.queue.clear()
        }
    }

    private fun createThread(dispatcher: DispatcherThread): Thread {
        return Thread({
            Process.setThreadPriority(dispatcher.priority)
            AppLog.i { "${dispatcher.name} thread started with priority ${dispatcher.priority}" }

            while (dispatcher.running) {
                try {
                    // Block for up to 100ms, then check running flag
                    val queued = dispatcher.queue.poll(100, TimeUnit.MILLISECONDS)
                    if (queued != null) {
                        dispatcher.callback?.invoke(queued.message)
                    }
                } catch (e: InterruptedException) {
                    // Normal shutdown
                    break
                } catch (e: Exception) {
                    AppLog.e(e) { "Error in ${dispatcher.name}" }
                    // Continue processing - don't kill thread on handler error
                }
            }

            AppLog.i { "${dispatcher.name} thread stopped" }
        }, dispatcher.name)
    }

    /**
     * Get current queue depth for diagnostics.
     */
    fun getQueueDepth(type: Type): Int {
        return dispatchers[type]?.queue?.size ?: 0
    }

    /**
     * Get total dropped message count for diagnostics.
     */
    fun getDroppedCount(type: Type): Long {
        return dispatchers[type]?.droppedCount ?: 0
    }
}
