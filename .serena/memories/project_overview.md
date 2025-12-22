# Android Auto Headunit - Project Overview

## Purpose

Android Auto Headunit emulator written in Kotlin/Java. It connects to Android phones via USB or WiFi, establishes TLS-encrypted communication using the Android Auto Protocol (AAP), and streams video/audio while handling touch input and location data.

Key features:
- USB and WiFi connectivity to Android phones
- TLS encryption implemented in pure Kotlin/Java (no native C code)
- Multi-touch support
- Fullscreen/immersive mode with hidden system bars
- Auto-trust connected phones for immediate launch

## Tech Stack

- **Language**: Kotlin 1.9.25 (with some Java)
- **Build System**: Gradle with Android Gradle Plugin 8.5.2
- **Android SDK**: 
  - minSdk: 18 (Android 4.3)
  - targetSdk: 34
  - compileSdk: 34
- **Protobuf**: 3.25.1 (protobuf-javalite)
- **TLS**: Conscrypt 2.5.2 for modern TLS support on older Android
- **Android Libraries**:
  - AndroidX Activity, Fragment, Lifecycle, RecyclerView
  - LocalBroadcastManager for inter-component communication
  - ViewBinding for UI

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

- **AapService** (`aap/AapService.kt`) - Foreground service managing connection lifecycle
- **AapTransport** (`aap/AapTransport.kt`) - Main communication handler, message routing
- **AapProjectionActivity** (`aap/AapProjectionActivity.kt`) - Fullscreen video display with touch handling
- **AapSslImpl** (`aap/AapSslImpl.kt`) - Pure Java TLS implementation
- **AccessoryConnection** (`connection/`) - Interface with USB and Socket implementations
- **AppComponent** (`AppComponent.kt`) - Service locator for dependency injection

### Threading Model

- Main thread: UI (AapProjectionActivity)
- HandlerThread: Network polling (AapTransport.pollThread)
- Separate threads: Audio playback, microphone recording
