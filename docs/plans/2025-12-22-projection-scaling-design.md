# Projection View Scaling & Render Surface Design

**Date:** 2025-12-22  
**Status:** Approved

## Problem

Video projection is stretched to fill the screen without preserving aspect ratio, causing distortion (circles become ovals). Additionally, the render surface option (GLES 2.0 vs SurfaceView) shown in UI is not implemented.

## Solution Overview

1. **Aspect ratio correction** - Top/bottom letterboxing only (landscape full-width always)
2. **User-controlled margins** - Pixels with percentage shown in UI
3. **Render surface selection** - GLES 2.0 via TextureView (default) + SurfaceView fallback
4. **DPI auto-adjustment** - Scales proportionally when letterboxing reduces effective area

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Settings Storage                         │
│  - renderSurface: GLES_TEXTURE_VIEW | SURFACE_VIEW          │
│  - marginTop, marginBottom, marginLeft, marginRight (px)    │
│  - preserveAspectRatio: Boolean                             │
│  - manualDpi: Int (0 = auto-compute with scaling)           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   ProjectionView Factory                     │
│  Creates either:                                            │
│  - GlesProjectionView (TextureView + GLES 2.0) [default]    │
│  - SurfaceProjectionView (existing SurfaceView)             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  Aspect Ratio Calculator                     │
│  - Computes top/bottom letterbox margins                    │
│  - Combines with user margins                               │
│  - Calculates effective DPI based on visible area           │
└─────────────────────────────────────────────────────────────┘
```

## Aspect Ratio & Letterboxing Logic

```kotlin
// Given:
// - videoWidth, videoHeight (e.g., 1920x1080 = 16:9)
// - displayWidth, displayHeight (e.g., 1280x720 or 1024x600)

videoAspect = videoWidth / videoHeight  // e.g., 1.777 (16:9)
displayAspect = displayWidth / displayHeight  // e.g., 1.707 (1024x600)

// Only add TOP/BOTTOM letterbox (never left/right, to keep landscape full-width)
if (displayAspect < videoAspect) {
    // Display is "taller" than video - add top/bottom bars
    effectiveHeight = displayWidth / videoAspect
    letterboxTop = (displayHeight - effectiveHeight) / 2
    letterboxBottom = letterboxTop
} else {
    // Display is wider or equal - no letterboxing needed
    letterboxTop = 0
    letterboxBottom = 0
}
```

**Final margins = letterbox + user margins:**
```
finalTop = letterboxTop + userMarginTop
finalBottom = letterboxBottom + userMarginBottom
finalLeft = userMarginLeft
finalRight = userMarginRight
```

**DPI Auto-Adjustment:**
```kotlin
// Effective area after letterboxing
effectiveHeight = displayHeight - letterboxTop - letterboxBottom
scaleFactor = effectiveHeight / displayHeight

// Adjust DPI proportionally (smaller area = lower DPI to keep UI same apparent size)
adjustedDpi = baseDpi * scaleFactor
```

## Render Surface Implementation

### GlesProjectionView (TextureView) - Default

```kotlin
class GlesProjectionView : TextureView, TextureView.SurfaceTextureListener {
    // TextureView receives video frames via SurfaceTexture
    // GLES 2.0 renders texture to screen with transform matrix
    // Transform matrix handles: aspect ratio, margins, positioning
    
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null  // Pass to MediaCodec
    
    // Matrix applies letterbox + margins without re-encoding
    private val transformMatrix = Matrix()
    
    fun updateTransform(letterboxTop: Int, letterboxBottom: Int, margins: Margins) {
        // Calculate scale and translation to position video correctly
        transformMatrix.setScale(scaleX, scaleY)
        transformMatrix.postTranslate(offsetX, offsetY)
        setTransform(transformMatrix)
    }
}
```

### SurfaceProjectionView (existing) - Fallback

Keep existing SurfaceView implementation for compatibility. Margins applied via view padding/layout params.

### Factory

```kotlin
object ProjectionViewFactory {
    fun create(context: Context, settings: Settings): View {
        return when (settings.renderSurface) {
            RenderSurface.GLES_TEXTURE_VIEW -> GlesProjectionView(context)
            RenderSurface.SURFACE_VIEW -> SurfaceProjectionView(context)
        }
    }
}
```

## Settings Storage

**New properties in `Settings.kt`:**

```kotlin
// Render surface type
enum class RenderSurface(val value: Int) {
    GLES_TEXTURE_VIEW(0),  // Default
    SURFACE_VIEW(1)
}
var renderSurface: RenderSurface

// Margins in pixels (0 = none)
var marginTop: Int      // Default: 0
var marginBottom: Int   // Default: 0
var marginLeft: Int     // Default: 0
var marginRight: Int    // Default: 0

// Aspect ratio preservation
var preserveAspectRatio: Boolean  // Default: true
```

## UI Changes

**Updated `preferences_graphics.xml`:**

```xml
<!-- Render Surface -->
<ListPreference
    android:key="render_surface"
    android:title="Render surface"
    android:summary="GLES 2.0 for better performance, SurfaceView for compatibility"
    android:defaultValue="0" />

<!-- Aspect Ratio -->
<SwitchPreferenceCompat
    android:key="preserve_aspect_ratio"
    android:title="Preserve aspect ratio"
    android:summary="Add letterbox bars to prevent distortion"
    android:defaultValue="true" />

<!-- Margins (shown as pixels with % equivalent) -->
<SeekBarPreference android:key="margin_top" android:title="Margin Top" />
<SeekBarPreference android:key="margin_bottom" android:title="Margin Bottom" />
<SeekBarPreference android:key="margin_left" android:title="Margin Left" />
<SeekBarPreference android:key="margin_right" android:title="Margin Right" />
```

**UI display format:** `"24px (3%)"` - shows both pixel value and percentage of screen dimension.

## File Changes

### Files to Modify

| File | Changes |
|------|---------|
| `Settings.kt` | Add `renderSurface`, `marginTop/Bottom/Left/Right`, `preserveAspectRatio` |
| `Screen.kt` | Add `computeLetterbox()`, update `computeDpi()` for auto-adjustment |
| `ServiceDiscoveryResponse.kt` | Use computed margins and adjusted DPI instead of hardcoded values |
| `preferences_graphics.xml` | Add render surface, aspect ratio toggle, margin sliders |
| `GraphicsSettingsFragment.kt` | Handle new preferences, show px/% format |
| `ProjectionView.kt` | Rename to `SurfaceProjectionView.kt`, extract interface |
| `AapProjectionActivity.kt` | Use factory to create correct view type |

### New Files to Create

| File | Purpose |
|------|---------|
| `GlesProjectionView.kt` | TextureView implementation with GLES transforms |
| `ProjectionViewFactory.kt` | Creates appropriate view based on settings |
| `AspectRatioCalculator.kt` | Computes letterbox margins and DPI adjustment |
| `Margins.kt` | Data class for margin values |

## Integration Flow

```
App Start → Settings.renderSurface → ProjectionViewFactory
    → GlesProjectionView or SurfaceProjectionView
    → AspectRatioCalculator computes letterbox + combines user margins
    → ServiceDiscoveryResponse sends adjusted DPI to phone
    → View applies transforms for correct display
```

## Android Version Compatibility

- GLES 2.0: Supported since Android 2.2 (API 8)
- TextureView: Supported since Android 4.0 (API 14)
- Target min SDK: Android 4.3 (API 18) - fully compatible
- GLES 2.0 (TextureView) is the default for better performance
