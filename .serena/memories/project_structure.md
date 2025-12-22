# Project Structure

```
android-auto-headunit/
├── app/                              # Main application module
│   ├── build.gradle                  # Module build config
│   └── src/main/
│       ├── java/info/anodsplace/headunit/
│       │   ├── aap/                  # Android Auto Protocol implementation
│       │   │   ├── AapService.kt     # Foreground service for connection
│       │   │   ├── AapTransport.kt   # Main message handler & routing
│       │   │   ├── AapProjectionActivity.kt  # Video display activity
│       │   │   ├── AapSsl.kt         # TLS interface
│       │   │   ├── AapSslImpl.kt     # Pure Java TLS implementation
│       │   │   ├── AapRead.kt        # Message reader interface
│       │   │   ├── AapAudio.kt       # Audio handling
│       │   │   ├── AapVideo.kt       # Video handling
│       │   │   ├── AapControl.kt     # Control channel handler
│       │   │   └── protocol/         # Protocol definitions
│       │   │       ├── Channel.kt    # Channel constants
│       │   │       ├── MsgType.kt    # Message type constants
│       │   │       └── messages/     # Protocol message classes
│       │   │
│       │   ├── connection/           # USB/Socket connections
│       │   │   ├── AccessoryConnection.kt     # Interface
│       │   │   ├── UsbAccessoryConnection.kt  # USB implementation
│       │   │   ├── SocketAccessoryConnection.kt  # WiFi implementation
│       │   │   └── UsbReceiver.kt    # USB broadcast receiver
│       │   │
│       │   ├── decoder/              # Media decoding
│       │   │   ├── AudioDecoder.kt   # Audio stream decoder
│       │   │   ├── VideoDecoderController.kt  # Video decode management
│       │   │   ├── VideoDecodeThread.kt  # Video decode worker
│       │   │   └── MicRecorder.kt    # Microphone input
│       │   │
│       │   ├── ui/                   # User interface
│       │   │   ├── home/             # Home screen
│       │   │   ├── settings/         # Settings screens
│       │   │   └── onboarding/       # First-run onboarding
│       │   │
│       │   ├── main/                 # Main activity
│       │   │   └── MainActivity.kt
│       │   │
│       │   ├── app/                  # Application components
│       │   │   ├── BootCompleteReceiver.kt  # Boot listener
│       │   │   └── UsbAttachedActivity.kt   # USB attach handler
│       │   │
│       │   ├── location/             # GPS services
│       │   │   └── GpsLocationService.kt
│       │   │
│       │   ├── utils/                # Utilities
│       │   │   ├── Settings.kt       # SharedPreferences wrapper
│       │   │   ├── AppLog.kt         # Logging
│       │   │   └── NetworkUtils.kt   # Network helpers
│       │   │
│       │   ├── contract/             # Intent contracts
│       │   │   └── HeadUnitIntent.kt
│       │   │
│       │   ├── view/                 # Custom views
│       │   │   └── ProjectionView.kt
│       │   │
│       │   ├── App.kt                # Application class
│       │   └── AppComponent.kt       # Service locator/DI
│       │
│       ├── proto/                    # Protocol buffer definitions
│       ├── res/                      # Android resources
│       └── AndroidManifest.xml
│
├── build.gradle                      # Root build config
├── settings.gradle                   # Gradle settings
├── gradle.properties                 # Gradle properties
├── gradlew / gradlew.bat            # Gradle wrapper
├── CLAUDE.md                         # AI assistant instructions
└── README.md                         # Project documentation
```

## Key Entry Points

1. **MainActivity** - Main launcher activity
2. **AapService** - Background connection service
3. **AapProjectionActivity** - Video display during connection
4. **UsbAttachedActivity** - Handles USB device attachment
