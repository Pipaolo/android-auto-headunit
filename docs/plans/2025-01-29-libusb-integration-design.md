# libusb Integration for High-Performance USB I/O

**Date:** 2025-01-29
**Status:** Approved

## Problem Statement

Sequential message processing in the USB read loop causes audio dropouts when touch events are processed. The current `UsbDeviceConnection.bulkTransfer()` is synchronous and all message types (audio, video, touch, control) share one processing thread.

When touch events are processed, audio packet processing is delayed, causing audible dropouts perceived as "audio ducking."

## Solution

Replace Android's Java USB API with libusb 1.0.29 via JNI for:
- True async USB I/O with callbacks
- Separate processing threads per channel type
- Audio on high-priority real-time thread
- Lock-free ring buffer to prevent allocation during streaming

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Native Layer (C/C++)                      │
├─────────────────────────────────────────────────────────────────┤
│  libusb async transfers                                          │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────────┐ │
│  │ USB Read    │───►│ Ring Buffer  │───►│ Channel Dispatcher  │ │
│  │ (async)     │    │ (lock-free)  │    │ (by channel type)   │ │
│  └─────────────┘    └──────────────┘    └─────────────────────┘ │
│                                                │                 │
│                     ┌──────────────────────────┼────────────┐   │
│                     ▼                          ▼            ▼   │
│              ┌───────────┐            ┌───────────┐  ┌────────┐ │
│              │ Audio Q   │            │ Video Q   │  │Control │ │
│              │ (hi-pri)  │            │ (med-pri) │  │Touch Q │ │
│              └───────────┘            └───────────┘  └────────┘ │
└────────────────────┬─────────────────────────┬───────────┬──────┘
                     │ JNI callback            │           │
┌────────────────────▼─────────────────────────▼───────────▼──────┐
│                        Kotlin Layer                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────┐ │
│  │ AudioProcessor  │  │ VideoProcessor  │  │ ControlProcessor │ │
│  │ (RT thread)     │  │ (normal)        │  │ (normal)         │ │
│  └─────────────────┘  └─────────────────┘  └──────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Native Layer Structure

```
app/src/main/cpp/
├── CMakeLists.txt
├── libusb-1.0.29/             # Extracted from tarball
├── usb_connection.h/.cpp      # libusb wrapper, async transfers
├── ring_buffer.h/.cpp         # Lock-free SPSC buffer (~500KB)
├── channel_dispatcher.h/.cpp  # Routes by AAP channel type
└── jni_bridge.cpp             # JNI bindings
```

### Native Classes

**UsbConnection:**
- `open(fd)` - Takes file descriptor from Android
- `startAsyncRead()` - Begins continuous async bulk reads
- `write(data, len)` - Async bulk write
- `close()` - Cleanup

**RingBuffer:**
- Lock-free single-producer single-consumer
- Sized for ~100ms audio data (~500KB)
- Zero allocation during streaming

**ChannelDispatcher:**
- Parses AAP message headers
- Routes to priority queues by channel type
- Audio → immediate JNI callback on RT thread
- Video → medium priority
- Control/Touch → normal priority

## JNI Interface

```kotlin
object NativeUsb {
    init {
        System.loadLibrary("headunit_usb")
    }

    external fun open(fileDescriptor: Int): Long
    external fun close(handle: Long)
    external fun startReading(handle: Long)
    external fun stopReading(handle: Long)
    external fun write(handle: Long, data: ByteArray, length: Int): Int

    // Callbacks from native threads
    @JvmStatic fun onAudioData(channel: Int, data: ByteArray, length: Int)
    @JvmStatic fun onVideoData(channel: Int, data: ByteArray, length: Int)
    @JvmStatic fun onControlData(channel: Int, data: ByteArray, length: Int)
    @JvmStatic fun onError(errorCode: Int, message: String)
}
```

## Kotlin Integration

**New files:**
- `NativeUsb.kt` - JNI bindings
- `NativeUsbAccessoryConnection.kt` - AccessoryConnection implementation

**Modified files:**
- `AapTransport.kt` - Use NativeUsbAccessoryConnection, remove poll loop
- `AapMessageHandlerImpl.kt` - Split into channel-specific handlers
- `AapService.kt` - Initialize native library
- `app/build.gradle` - Add CMake, raise minSdk to 21

**Deleted files:**
- `AapReadMultipleMessages.kt`
- `AapReadSingleMessage.kt`
- `UsbAccessoryConnection.kt`
- `AapRead.kt`

## Build Configuration

**build.gradle:**
```groovy
android {
    defaultConfig {
        minSdk 21
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
        }
    }
    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
            version "3.22.1"
        }
    }
}
```

**libusb source:**
- https://github.com/libusb/libusb/releases/download/v1.0.29/libusb-1.0.29.tar.bz2
- Extract to `app/src/main/cpp/libusb-1.0.29/`

## Implementation Tasks

1. Download and extract libusb 1.0.29
2. Create CMakeLists.txt with libusb integration
3. Implement native USB wrapper (usb_connection.cpp)
4. Implement ring buffer (ring_buffer.cpp)
5. Implement channel dispatcher (channel_dispatcher.cpp)
6. Implement JNI bridge (jni_bridge.cpp)
7. Create NativeUsb.kt JNI bindings
8. Create NativeUsbAccessoryConnection.kt
9. Split AapMessageHandlerImpl into channel handlers
10. Update AapTransport to use native connection
11. Update build.gradle (minSdk 21, CMake config)
12. Delete legacy USB code
13. Test and verify audio plays without dropouts

## APK Size Impact

- libusb: ~200KB per ABI
- Native code: ~50KB per ABI
- Total: ~1MB additional (4 ABIs)
