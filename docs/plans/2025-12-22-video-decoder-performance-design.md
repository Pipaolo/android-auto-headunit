# Video Decoder Performance Optimization Design

**Date:** 2025-12-22  
**Status:** Approved  
**Target:** API 18-20 (Android 4.3-4.4) headunits with limited CPU/GPU

## Problem Statement

The current VideoDecoder implementation suffers from multiple performance issues on older Android headunits:

- Choppy/stuttering video
- High latency between phone and headunit display
- Dropped frames with freezing
- Memory pressure and GC pauses

### Root Causes Identified

1. Global synchronized lock on all operations
2. `ByteBuffer.wrap()` allocations per frame causing GC pressure
3. 1 second timeout on input buffer dequeue blocks network thread
4. No backpressure or frame dropping when decoder falls behind
5. Deprecated `getInputBuffers()` API usage
6. Video processing on network poll thread (blocking I/O mixed with decode)

## Solution: Producer/Consumer Pipeline

Decouple network I/O from video decoding using a dedicated decode thread with a bounded queue.

### Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Network Thread │     │   Ring Buffer    │     │  Decode Thread  │
│  (Poll Thread)  │────▶│  (Pre-allocated) │────▶│  (New Dedicated)│
│                 │     │                  │     │                 │
│  AapVideo.kt    │     │  VideoFrameQueue │     │  VideoDecoder   │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                                                          │
                                                          ▼
                                                 ┌─────────────────┐
                                                 │   MediaCodec    │
                                                 │   + Surface     │
                                                 └─────────────────┘
```

### Key Principles

- **Thread ownership:** Network thread owns write side of queue. Decode thread owns codec.
- **No shared mutable state:** Only lock-free queue connects threads.
- **Bounded latency:** 6-frame queue caps buffering at ~100ms (60fps) or ~200ms (30fps).
- **Zero GC pressure:** All buffers pre-allocated at startup.

## Component Design

### 1. VideoFrameQueue

Lock-free single-producer/single-consumer ring buffer.

```kotlin
class VideoFrameQueue(
    private val capacity: Int = 6,           // 6 frames = ~100ms at 60fps
    private val maxFrameSize: Int = 65536    // 64KB per frame buffer
) {
    // Pre-allocated frame slots - no runtime allocation
    private val frames = Array(capacity) { ByteArray(maxFrameSize) }
    private val sizes = IntArray(capacity)
    
    // Lock-free indices (volatile for visibility)
    @Volatile private var writeIndex = 0
    @Volatile private var readIndex = 0
    
    @Volatile var droppedFrames = 0L
        private set

    /**
     * Called by network thread. Never blocks.
     */
    fun offer(data: ByteArray, offset: Int, length: Int): Boolean {
        val nextWrite = (writeIndex + 1) % capacity
        
        if (nextWrite == readIndex) {
            // Queue full - drop oldest frame
            readIndex = (readIndex + 1) % capacity
            droppedFrames++
        }
        
        System.arraycopy(data, offset, frames[writeIndex], 0, length)
        sizes[writeIndex] = length
        writeIndex = nextWrite
        return true
    }

    /**
     * Called by decode thread. Returns -1 if empty.
     */
    fun poll(outBuffer: ByteArray): Int {
        if (readIndex == writeIndex) {
            return -1
        }
        
        val length = sizes[readIndex]
        System.arraycopy(frames[readIndex], 0, outBuffer, 0, length)
        readIndex = (readIndex + 1) % capacity
        return length
    }
    
    fun isEmpty(): Boolean = readIndex == writeIndex
    
    fun size(): Int {
        val w = writeIndex
        val r = readIndex
        return if (w >= r) w - r else capacity - r + w
    }
}
```

**Buffer sizing:**
- 6 frames handles both 30fps (~200ms buffer) and 60fps (~100ms buffer)
- 64KB per slot covers largest expected NAL unit
- Total memory: 6 × 64KB = 384KB

**Drop policy:** When full, overwrite oldest unread frame to bound latency.

### 2. VideoDecodeThread

Dedicated HandlerThread that owns the MediaCodec.

```kotlin
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
    
    private val frameBuffer = ByteArray(65536)
    private val monitor = VideoPerformanceMonitor(queue)
    
    @Volatile private var running = false
    
    override fun onLooperPrepared() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
        initCodec()
        running = true
        Handler(looper).post { decodeLoop() }
    }
    
    private fun decodeLoop() {
        while (running) {
            try {
                drainOutput()
                
                val length = queue.poll(frameBuffer)
                if (length > 0) {
                    if (!codecConfigured && isSpsFrame(frameBuffer)) {
                        codecConfigured = true
                    }
                    
                    if (codecConfigured) {
                        feedInput(frameBuffer, length)
                        monitor.onFrameDecoded()
                    }
                } else {
                    Thread.sleep(1)
                }
                
            } catch (e: IllegalStateException) {
                resetCodec()
            } catch (e: Exception) {
                AppLog.e(e) { "Unexpected decode error" }
            }
        }
    }
    
    private fun drainOutput() {
        while (true) {
            val index = codec!!.dequeueOutputBuffer(codecBufferInfo, 0)
            if (index >= 0) {
                codec!!.releaseOutputBuffer(index, true)
            } else {
                break
            }
        }
    }
    
    private fun feedInput(data: ByteArray, length: Int) {
        val inputIndex = codec!!.dequeueInputBuffer(10_000) // 10ms timeout
        
        if (inputIndex < 0) {
            return // Will retry next loop
        }
        
        val buffer = inputBuffers!![inputIndex]
        buffer.clear()
        buffer.put(data, 0, length)
        buffer.flip()
        
        codec!!.queueInputBuffer(inputIndex, 0, length, 0, 0)
    }
    
    private fun initCodec() {
        codec = MediaCodec.createDecoderByType("video/avc")
        
        val format = MediaFormat.createVideoFormat("video/avc", width, height)
        if (Build.VERSION.SDK_INT >= 19) {
            format.setInteger("low-latency", 1)
        }
        
        codec!!.configure(format, surface, null, 0)
        codec!!.start()
        inputBuffers = codec!!.inputBuffers
    }
    
    private fun resetCodec() {
        try {
            codec?.stop()
            codec?.release()
        } catch (ignored: Exception) {}
        
        codecConfigured = false
        Thread.sleep(100)
        initCodec()
    }
    
    private fun isSpsFrame(data: ByteArray): Boolean {
        return (data[4].toInt() and 0x1f) == 7
    }
    
    fun stopDecoding() {
        running = false
    }
}
```

**Key improvements:**
- 10ms input buffer timeout instead of 1 second
- `THREAD_PRIORITY_DISPLAY` for smooth playback
- Drain output before feeding input to prevent codec stall
- Automatic codec reset on error

### 3. Modified AapVideo (Thin Writer)

```kotlin
internal class AapVideo(
    private val frameQueue: VideoFrameQueue
) {
    private val fragmentBuffer = ByteBuffer.allocateDirect(65536)
    
    fun process(message: AapMessage): Boolean {
        val flags = message.flags.toInt()
        val buf = message.data
        val len = message.size

        when (flags) {
            11 -> {
                // Complete frame
                if (isValidNalUnit(buf, 10)) {
                    frameQueue.offer(buf, 10, len - 10)
                    return true
                } else if (message.type == 1 && isValidNalUnit(buf, 2)) {
                    frameQueue.offer(buf, message.dataOffset, len - message.dataOffset)
                    return true
                }
            }
            9 -> {
                // First fragment
                if (isValidNalUnit(buf, 10)) {
                    fragmentBuffer.clear()
                    fragmentBuffer.put(buf, 10, len - 10)
                    return true
                }
            }
            8 -> {
                // Middle fragment
                fragmentBuffer.put(buf, 0, len)
                return true
            }
            10 -> {
                // Last fragment
                fragmentBuffer.put(buf, 0, len)
                fragmentBuffer.flip()
                frameQueue.offer(
                    fragmentBuffer.array(),
                    fragmentBuffer.arrayOffset(),
                    fragmentBuffer.limit()
                )
                fragmentBuffer.clear()
                return true
            }
        }

        return false
    }

    private fun isValidNalUnit(buf: ByteArray, offset: Int): Boolean {
        return buf[offset].toInt() == 0 && buf[offset+1].toInt() == 0 
            && buf[offset+2].toInt() == 0 && buf[offset+3].toInt() == 1
    }
}
```

### 4. VideoDecoderController (Lifecycle)

```kotlin
class VideoDecoderController {
    
    private var frameQueue: VideoFrameQueue? = null
    private var decodeThread: VideoDecodeThread? = null
    
    fun onSurfaceAvailable(holder: SurfaceHolder, width: Int, height: Int) {
        synchronized(this) {
            val cappedHeight = minOf(height, 1080)
            
            if (decodeThread != null) {
                return
            }
            
            frameQueue = VideoFrameQueue(capacity = 6)
            decodeThread = VideoDecodeThread(
                queue = frameQueue!!,
                surface = holder.surface,
                width = width,
                height = cappedHeight
            )
            
            decodeThread!!.start()
        }
    }
    
    fun stop(reason: String) {
        synchronized(this) {
            decodeThread?.let {
                it.stopDecoding()
                it.quitSafely()
                it.join(1000)
            }
            decodeThread = null
            frameQueue = null
        }
    }
    
    fun getFrameQueue(): VideoFrameQueue? = frameQueue
}
```

### 5. VideoPerformanceMonitor

```kotlin
class VideoPerformanceMonitor(
    private val queue: VideoFrameQueue,
    private val logIntervalMs: Long = 5000
) {
    private var lastLogTime = 0L
    private var lastDroppedCount = 0L
    private var framesDecoded = 0L
    
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
        
        if (newDrops > 0 || queueDepth > 3) {
            AppLog.w { 
                "Video stats: decoded=$framesDecoded, " +
                "dropped=$newDrops (total=$dropped), " +
                "queue=$queueDepth/6" 
            }
        }
        
        lastDroppedCount = dropped
        lastLogTime = now
    }
}

data class VideoStats(
    val framesDecoded: Long,
    val framesDropped: Long,
    val queueDepth: Int
) {
    val dropRate: Float 
        get() = if (framesDecoded > 0) {
            framesDropped.toFloat() / (framesDecoded + framesDropped)
        } else 0f
}
```

## Implementation Plan

### Files to Create

| File | Description |
|------|-------------|
| `decoder/VideoFrameQueue.kt` | Lock-free ring buffer |
| `decoder/VideoDecodeThread.kt` | Dedicated decode thread |
| `decoder/VideoPerformanceMonitor.kt` | Stats tracking |
| `decoder/VideoDecoderController.kt` | Lifecycle management |

### Files to Modify

| File | Changes |
|------|---------|
| `aap/AapVideo.kt` | Replace VideoDecoder with queue reference |
| `aap/AapProjectionActivity.kt` | Use VideoDecoderController |
| `aap/AapTransport.kt` | Wire up controller and queue |

### Files to Delete

| File | Reason |
|------|--------|
| `decoder/VideoDecoder.kt` | Replaced by new components |

## Expected Improvements

| Metric | Before | After |
|--------|--------|-------|
| GC pressure | High (allocations per frame) | Zero (pre-allocated) |
| Max latency | Unbounded | ~100ms (6-frame cap) |
| Network blocking | Up to 1 second | Never |
| Frame rate | 30fps struggling | 60fps capable |
| Thread priority | Default | THREAD_PRIORITY_DISPLAY |

## Monitoring

Log output for debugging:
```
W/HeadUnit: Video stats: decoded=1847, dropped=12 (total=12), queue=2/6
```

Drop rate interpretation:
- < 1%: Healthy
- 1-5%: Marginal, device near capacity
- > 5%: Device too slow for stream resolution
