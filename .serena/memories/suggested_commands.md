# Suggested Commands

## Build Commands

```bash
# Full build (compiles, runs tests, lints)
./gradlew build

# Build debug APK only
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build artifacts
./gradlew clean

# Run unit tests
./gradlew test
```

Build outputs are located at: `app/build/outputs/apk/`

## System Commands (Darwin/macOS)

```bash
# List files
ls -la

# Find files
find . -name "*.kt"

# Search in files
grep -r "pattern" --include="*.kt"

# Git commands
git status
git diff
git add .
git commit -m "message"
git log --oneline
```

## Android Development

```bash
# Install APK on connected device
adb install app/build/outputs/apk/debug/app-debug.apk

# View device logs
adb logcat | grep -i headunit

# List connected devices
adb devices
```

## Protobuf

Protocol buffer definitions are in `app/src/main/proto/` and are compiled automatically by the protobuf-gradle-plugin during build.
