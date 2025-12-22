# USB Device List Feature Design

## Overview

Add the ability to manually select and connect to USB devices from the HomeFragment USB tile, rather than relying solely on automatic USB attach detection.

## User Flow

1. User taps USB tile on home screen
2. App queries `UsbManager.getDeviceList()` for connected USB devices
3. **If no devices:** Show toast "No USB devices found"
4. **If devices found:** Show AlertDialog with list of devices
5. User selects a device from the list
6. App requests USB permission (if not already granted)
7. On permission granted, switch device to accessory mode (if needed)
8. Start `AapService` with the selected device
9. `AapProjectionActivity` launches automatically

## Dialog Design

- **Type:** AlertDialog with list items
- **Title:** "Select USB Device"
- **List item format:** Device name + status
  - Example: "Samsung 04e8:6860" with subtitle "Ready" or "Accessory mode"
- **Actions:** Cancel button to dismiss

## Components

### Files to Modify

**HomeFragment.kt**
- Add `UsbManager` reference
- Implement `UsbReceiver.Listener` for permission callbacks
- New `onUsbConnectClicked()` logic:
  - Enumerate connected USB devices
  - Show AlertDialog with device list
  - Handle device selection
  - Request permission if needed
  - Start connection flow

**UsbDeviceCompat.kt**
- Add `statusText` extension property
- Returns "Accessory mode" if device is in accessory mode
- Returns "Ready" otherwise

**String resources**
- Dialog title
- Status texts ("Ready", "Accessory mode")
- "No USB devices found" message

### Files Unchanged

- `AapService.kt` - Already has `createIntent(device, context)`
- `UsbAccessoryMode.kt` - Already handles accessory mode switching
- `UsbAttachedActivity.kt` - Continues handling automatic USB attach events

## Connection Sequence

```
USB Tile Tap
    │
    ▼
Query UsbManager.getDeviceList()
    │
    ├─[No devices]──► Toast "No USB devices found" ──► END
    │
    ▼
Show AlertDialog with devices
    │
    ▼
User selects device
    │
    ├─[No permission]──► requestPermission() ──► Wait for callback
    │                                                   │
    ▼                                                   │
[Has permission] ◄──────────────────────────────────────┘
    │
    ├─[Not in accessory mode]──► UsbAccessoryMode.connectAndSwitch()
    │                                      │
    │                                      ▼
    │                           Device re-enumerates
    │                                      │
    ▼                                      ▼
startService(AapService.createIntent(device))
    │
    ▼
AapProjectionActivity launches
```

## Edge Cases

| Scenario | Handling |
|----------|----------|
| No devices connected | Toast "No USB devices found" |
| Device disconnected while dialog open | AapService fails gracefully with existing "Cannot connect" toast |
| Permission denied | No action, user can retry |
| Already connected to a device | Check `transport.isAlive`, prevent duplicate connections |
| Device already in accessory mode | Skip `connectAndSwitch()`, start AapService directly |

## Status Updates

The USB tile status text reflects current state:
- "Ready" - Default state
- "Connecting..." - After device selection
- Returns to "Ready" on disconnect

## Dependencies

No new dependencies required. Uses existing:
- `AlertDialog` from AndroidX
- `UsbManager` from Android SDK
- Existing `UsbReceiver` for permission callbacks
