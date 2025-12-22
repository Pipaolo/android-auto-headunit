# USB Device List Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add manual USB device selection via AlertDialog when tapping the USB tile on HomeFragment.

**Architecture:** HomeFragment queries UsbManager for connected devices, displays them in an AlertDialog with name and status, handles permission requests via UsbReceiver.Listener pattern, then starts AapService with the selected device.

**Tech Stack:** Kotlin, AndroidX AlertDialog, Android UsbManager API, existing UsbReceiver/UsbAccessoryMode components.

---

### Task 1: Add String Resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Step 1: Add new string resources**

Add these strings for the USB device selection feature:

```xml
<!-- USB Device Selection -->
<string name="usb_no_devices_found">No USB devices found</string>
<string name="usb_select_device_title">Select USB Device</string>
<string name="usb_status_accessory_mode">Accessory mode</string>
<string name="usb_status_ready_to_connect">Ready</string>
<string name="usb_status_requesting_permission">Requesting permission…</string>
```

**Step 2: Verify XML is valid**

Run: `./gradlew assembleDebug 2>&1 | head -20`
Expected: Build starts without XML parse errors

**Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(strings): add USB device selection strings"
```

---

### Task 2: Add statusText Extension to UsbDeviceCompat

**Files:**
- Modify: `app/src/main/java/info/anodsplace/headunit/connection/UsbDeviceCompat.kt`

**Step 1: Add statusText extension property**

Add at the end of the file, after the `uniqueName` extension:

```kotlin
val UsbDevice.statusText: String
    get() = if (isInAccessoryMode) "Accessory mode" else "Ready"
```

**Step 2: Build to verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/info/anodsplace/headunit/connection/UsbDeviceCompat.kt
git commit -m "feat(usb): add statusText extension property"
```

---

### Task 3: Add UsbManager and UsbReceiver to HomeFragment

**Files:**
- Modify: `app/src/main/java/info/anodsplace/headunit/ui/home/HomeFragment.kt`

**Step 1: Add imports and class-level properties**

Add these imports at the top:

```kotlin
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.widget.ArrayAdapter
import android.widget.Toast
import info.anodsplace.headunit.App
import info.anodsplace.headunit.aap.AapService
import info.anodsplace.headunit.connection.UsbAccessoryMode
import info.anodsplace.headunit.connection.UsbReceiver
import info.anodsplace.headunit.connection.isInAccessoryMode
import info.anodsplace.headunit.connection.statusText
import info.anodsplace.headunit.connection.uniqueName
```

**Step 2: Implement UsbReceiver.Listener interface**

Change class declaration:

```kotlin
class HomeFragment : Fragment(), UsbReceiver.Listener {
```

**Step 3: Add class-level properties**

Add after `private var usbStatusText: TextView? = null`:

```kotlin
private lateinit var usbManager: UsbManager
private var usbReceiver: UsbReceiver? = null
private var pendingDevice: UsbDevice? = null
```

**Step 4: Build to verify (will have errors - need to implement interface)**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: Errors about unimplemented UsbReceiver.Listener methods

**Step 5: Add stub implementations for UsbReceiver.Listener**

Add these methods to the class:

```kotlin
override fun onUsbDetach(device: UsbDevice) {
    // Device disconnected - update UI if needed
    pendingDevice = null
}

override fun onUsbAttach(device: UsbDevice) {
    // Device attached - could refresh list if dialog is open
}

override fun onUsbPermission(granted: Boolean, connect: Boolean, device: UsbDevice) {
    if (granted && connect) {
        connectToDevice(device)
    } else {
        updateUsbStatus(getString(R.string.usb_status_ready))
        pendingDevice = null
    }
}

private fun connectToDevice(device: UsbDevice) {
    // Will be implemented in next task
}
```

**Step 6: Build to verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/info/anodsplace/headunit/ui/home/HomeFragment.kt
git commit -m "feat(home): add UsbReceiver.Listener interface to HomeFragment"
```

---

### Task 4: Initialize and Register UsbReceiver

**Files:**
- Modify: `app/src/main/java/info/anodsplace/headunit/ui/home/HomeFragment.kt`

**Step 1: Initialize UsbManager in onViewCreated**

Add at the start of `onViewCreated`, before other code:

```kotlin
usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
```

**Step 2: Register UsbReceiver in onResume**

Add method:

```kotlin
override fun onResume() {
    super.onResume()
    usbReceiver = UsbReceiver(this)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requireContext().registerReceiver(usbReceiver, UsbReceiver.createFilter(), Context.RECEIVER_EXPORTED)
    } else {
        requireContext().registerReceiver(usbReceiver, UsbReceiver.createFilter())
    }
}
```

**Step 3: Unregister UsbReceiver in onPause**

Add method:

```kotlin
override fun onPause() {
    super.onPause()
    usbReceiver?.let {
        requireContext().unregisterReceiver(it)
    }
    usbReceiver = null
}
```

**Step 4: Build to verify**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/info/anodsplace/headunit/ui/home/HomeFragment.kt
git commit -m "feat(home): register UsbReceiver for permission callbacks"
```

---

### Task 5: Implement Device Enumeration and Dialog

**Files:**
- Modify: `app/src/main/java/info/anodsplace/headunit/ui/home/HomeFragment.kt`

**Step 1: Replace onUsbConnectClicked with device enumeration**

Replace the existing `onUsbConnectClicked` method:

```kotlin
private fun onUsbConnectClicked() {
    // Check if already connected
    if (App.provide(requireContext()).transport.isAlive) {
        Toast.makeText(requireContext(), "Already connected", Toast.LENGTH_SHORT).show()
        return
    }

    val deviceList = usbManager.deviceList
    if (deviceList.isEmpty()) {
        Toast.makeText(requireContext(), R.string.usb_no_devices_found, Toast.LENGTH_SHORT).show()
        return
    }

    showDeviceSelectionDialog(deviceList.values.toList())
}
```

**Step 2: Add showDeviceSelectionDialog method**

Add this new method:

```kotlin
private fun showDeviceSelectionDialog(devices: List<UsbDevice>) {
    val deviceNames = devices.map { "${it.uniqueName}\n${it.statusText}" }.toTypedArray()

    AlertDialog.Builder(requireContext())
        .setTitle(R.string.usb_select_device_title)
        .setItems(deviceNames) { _, which ->
            val selectedDevice = devices[which]
            onDeviceSelected(selectedDevice)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
```

**Step 3: Add onDeviceSelected method**

Add this new method:

```kotlin
private fun onDeviceSelected(device: UsbDevice) {
    updateUsbStatus(getString(R.string.usb_status_connecting))

    if (usbManager.hasPermission(device)) {
        connectToDevice(device)
    } else {
        pendingDevice = device
        requestUsbPermission(device)
    }
}
```

**Step 4: Add requestUsbPermission method**

Add this new method:

```kotlin
private fun requestUsbPermission(device: UsbDevice) {
    updateUsbStatus(getString(R.string.usb_status_requesting_permission))

    val intent = Intent(UsbReceiver.ACTION_USB_DEVICE_PERMISSION).apply {
        putExtra(UsbManager.EXTRA_DEVICE, device)
        putExtra(UsbReceiver.EXTRA_CONNECT, true)
        setPackage(requireContext().packageName)
    }

    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    val pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, intent, flags)
    usbManager.requestPermission(device, pendingIntent)
}
```

**Step 5: Build to verify**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/info/anodsplace/headunit/ui/home/HomeFragment.kt
git commit -m "feat(home): implement USB device enumeration and selection dialog"
```

---

### Task 6: Implement Device Connection Logic

**Files:**
- Modify: `app/src/main/java/info/anodsplace/headunit/ui/home/HomeFragment.kt`

**Step 1: Replace connectToDevice stub with implementation**

Replace the empty `connectToDevice` method:

```kotlin
private fun connectToDevice(device: UsbDevice) {
    pendingDevice = null

    if (device.isInAccessoryMode) {
        // Already in accessory mode, start service directly
        startAapService(device)
    } else {
        // Need to switch to accessory mode first
        updateUsbStatus(getString(R.string.usb_status_connecting))
        val usbMode = UsbAccessoryMode(usbManager)

        if (usbMode.connectAndSwitch(device)) {
            // Device will re-enumerate in accessory mode
            // UsbAttachedActivity will handle it, or we wait for re-attach
            updateUsbStatus(getString(R.string.usb_status_ready))
        } else {
            Toast.makeText(requireContext(), "Failed to switch to accessory mode", Toast.LENGTH_SHORT).show()
            updateUsbStatus(getString(R.string.usb_status_ready))
        }
    }
}
```

**Step 2: Add startAapService method**

Add this new method:

```kotlin
private fun startAapService(device: UsbDevice) {
    val serviceIntent = AapService.createIntent(device, requireContext())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        requireContext().startForegroundService(serviceIntent)
    } else {
        requireContext().startService(serviceIntent)
    }
}
```

**Step 3: Build to verify**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/info/anodsplace/headunit/ui/home/HomeFragment.kt
git commit -m "feat(home): implement USB device connection logic"
```

---

### Task 7: Full Build and Integration Test

**Files:**
- None (testing only)

**Step 1: Run full debug build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Verify APK was generated**

Run: `ls -la app/build/outputs/apk/debug/`
Expected: app-debug.apk file present

**Step 3: Final commit with all changes**

Run: `git log --oneline -6`
Expected: See all 6 commits from this implementation

---

## Summary of Changes

| File | Change |
|------|--------|
| `strings.xml` | Added 5 new string resources |
| `UsbDeviceCompat.kt` | Added `statusText` extension property |
| `HomeFragment.kt` | Implemented UsbReceiver.Listener, device enumeration, AlertDialog selection, permission handling, connection logic |

## Testing Checklist

Manual testing on device:

1. [ ] Tap USB tile with no devices connected → Toast "No USB devices found"
2. [ ] Tap USB tile with device connected → AlertDialog appears with device list
3. [ ] Select device → Permission dialog appears (first time)
4. [ ] Grant permission → Device switches to accessory mode or connects
5. [ ] Deny permission → Returns to "Ready" state
6. [ ] Connect when already connected → Toast "Already connected"
