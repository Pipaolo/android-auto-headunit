# Message Dispatcher Design

## Problem

Audio crackling caused by blocking in `NativeUsbAccessoryConnection.handleRawData()`:
- Everything runs inside `synchronized(fifo)` including message dispatch
- `AudioTrack.write()` blocks when 50-100ms buffer is full
- `sendMediaAck()` does USB write inside the lock
- USB callback cannot deliver new data while lock is held
- Result: timing irregularities and audio crackling

## Solution

Decouple message dispatch from FIFO parsing using dedicated queues and threads per message type.

## Architecture

```
USB Callback (libusb thread)
    │
    ▼
handleRawData() [synchronized(fifo)]
    │ parse + decrypt only
    ▼
MessageDispatcher.dispatch(type, channel, message)
    │
    ├─► AudioQueue ──► AudioThread ──► onAudioMessage
    │
    ├─► VideoQueue ──► VideoThread ──► onVideoMessage  
    │
    └─► ControlQueue ──► ControlThread ──► onControlMessage (touch, etc.)
```

## Thread Priorities

| Queue | Android Priority | Rationale |
|-------|------------------|-----------|
| Audio | `THREAD_PRIORITY_URGENT_AUDIO` | Crackling prevention |
| Control | `THREAD_PRIORITY_FOREGROUND` | Touch responsiveness |
| Video | `THREAD_PRIORITY_DISPLAY` | Smooth video |

## MessageDispatcher Class

Location: `app/src/main/java/info/anodsplace/headunit/utils/MessageDispatcher.kt`

```kotlin
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
        val capacity: Int,
        var callback: ((AapMessage) -> Unit)?
    ) {
        val queue = LinkedBlockingQueue<QueuedMessage>(capacity)
        var thread: Thread? = null
        @Volatile var running = false
    }
    
    private val dispatchers = mapOf(
        Type.AUDIO to DispatcherThread(
            name = "AAP-Audio-Dispatch",
            priority = Process.THREAD_PRIORITY_URGENT_AUDIO,
            capacity = 64  // ~1 second of audio frames
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
    
    fun setCallback(type: Type, callback: ((AapMessage) -> Unit)?)
    fun dispatch(type: Type, channel: Int, message: AapMessage)
    fun start()
    fun stop()
}
```

## Queue Configuration

- Audio: capacity 64 (~1 second of audio frames)
- Video: capacity 30 (~1 second at 30fps)
- Control: capacity 128 (touch events, control messages)

Overflow strategy: Drop oldest message and log warning.

## Integration with NativeUsbAccessoryConnection

Key changes:
1. Add `MessageDispatcher` instance
2. Modify callback setters to register with dispatcher
3. Collect messages inside synchronized block
4. Dispatch outside synchronized block (non-blocking enqueue)
5. Start/stop dispatcher with reading lifecycle

```kotlin
private fun handleRawData(data: ByteArray, length: Int) {
    val messages = mutableListOf<Triple<MessageDispatcher.Type, Int, AapMessage>>()
    
    synchronized(fifo) {
        // ... existing parse/decrypt logic ...
        // Collect instead of dispatch:
        val type = when {
            isAudioChannel(channel) -> MessageDispatcher.Type.AUDIO
            isVideoChannel(channel) -> MessageDispatcher.Type.VIDEO
            else -> MessageDispatcher.Type.CONTROL
        }
        messages.add(Triple(type, channel, msg))
    }
    
    // Dispatch OUTSIDE the lock (non-blocking enqueue)
    messages.forEach { (type, channel, msg) ->
        dispatcher.dispatch(type, channel, msg)
    }
}
```

## Thread Lifecycle

- `start()`: Sets `running = true`, creates and starts all 3 threads
- `stop()`: Sets `running = false`, interrupts threads, waits for join (500ms timeout)
- `dispatch()`: Returns immediately if not running (message dropped)

## Error Handling

- Thread continues on handler exceptions (logged, not fatal)
- Queue poll with 100ms timeout to check running flag
- InterruptedException triggers clean shutdown
- Log warnings when queue drops messages due to overflow
