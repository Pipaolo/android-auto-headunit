# Task Completion Checklist

When completing a development task, perform these steps:

## 1. Code Verification

- [ ] Code compiles without errors: `./gradlew assembleDebug`
- [ ] No obvious runtime issues (review changes for null safety, threading)

## 2. Build Verification

```bash
# Always run a build to verify changes
./gradlew build
```

This will:
- Compile all code (including protobuf generation)
- Run lint checks (though abortOnError is false)
- Run unit tests if any exist

## 3. Manual Testing (if applicable)

For changes affecting:
- **USB connectivity**: Test with actual Android phone
- **Video/Audio**: Verify streams work correctly
- **Touch input**: Test multi-touch gestures
- **UI changes**: Check on device/emulator

## 4. Git Hygiene

```bash
# Check what changed
git status
git diff

# Commit with descriptive message
git add .
git commit -m "type(scope): description"
```

Commit message format:
- `feat(component)`: New feature
- `fix(component)`: Bug fix
- `refactor(component)`: Code restructuring
- `docs`: Documentation only

## Notes

- No formal code formatting tool - maintain existing style
- No automated test suite currently configured
- APK outputs at: `app/build/outputs/apk/`
