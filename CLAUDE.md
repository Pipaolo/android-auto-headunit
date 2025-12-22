# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android Auto Headunit emulator written in Kotlin/Java. Connects to Android phones via USB or WiFi, establishes TLS-encrypted communication using the Android Auto Protocol (AAP), and streams video/audio while handling touch input and location data. TLS is implemented in pure Kotlin/Java (no native C code).

## Build Commands

```bash
./gradlew build              # Full build
./gradlew assembleDebug      # Build debug APK
./gradlew assembleRelease    # Build release APK
./gradlew clean              # Clean build artifacts
./gradlew test               # Run unit tests
```

Build outputs: `app/build/outputs/apk/`

## Architecture

### Communication Flow

```
USB/Socket Connection → AapService (Foreground) → AapTransport
    ├→ AapRead/AapMessageHandler (protocol parsing)
    ├→ AapAudio (AudioDecoder, MicRecorder)
    ├→ AapVideo (VideoDecoder via MediaCodec)
    └→ AapSslImpl (TLS encryption)
         ↓
    AapProjectionActivity (video display)
```

### Key Components

- **AapService** (`aap/AapService.kt`) - Foreground service managing the connection lifecycle
- **AapTransport** (`aap/AapTransport.kt`) - Main communication handler, message routing
- **AapProjectionActivity** (`aap/AapProjectionActivity.kt`) - Fullscreen video display with touch handling
- **AapSslImpl** (`aap/AapSslImpl.kt`) - Pure Java TLS implementation (no native code)
- **AccessoryConnection** (`connection/`) - Interface with USB and Socket implementations
- **AppComponent** (`AppComponent.kt`) - Service locator for dependency injection

### Protocol

The `aap/protocol/` directory contains AAP message handlers. Protocol buffers are in `aap/protocol/proto/` and generated classes handle:
- Control channel (handshake, version negotiation)
- Video/Audio streaming
- Touch/Input events
- Sensor data (GPS location)

### Threading Model

- Main thread: UI (AapProjectionActivity)
- HandlerThread: Network polling (AapTransport.pollThread)
- Separate threads: Audio playback, microphone recording

### Broadcast Intents

`contract/HeadUnitIntent.kt` defines intents for inter-component communication: Connected, Disconnect, Key events, Location updates.

## Key Configuration

- **compileSdkVersion**: 28
- **minSdkVersion**: 18 (Android 4.3)
- **Kotlin**: 1.3.50
- **Protobuf**: 3.0.1 (protobuf-lite)

Protocol buffer definitions are compiled via the protobuf-gradle-plugin.
