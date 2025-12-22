# Projection Scaling Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix aspect ratio distortion by adding letterbox support, GLES TextureView rendering, and user-configurable margins.

**Architecture:** Factory pattern creates either GlesProjectionView (TextureView, default) or SurfaceProjectionView based on settings. AspectRatioCalculator computes letterbox margins and DPI adjustment. Settings stores all configuration.

**Tech Stack:** Kotlin, Android TextureView, GLES 2.0, SharedPreferences, PreferenceFragmentCompat

---

## Task 1: Add Margins Data Class

**Files:**
- Create: `app/src/main/java/info/anodsplace/headunit/view/Margins.kt`

**Step 1: Create Margins data class**

```kotlin
package info.anodsplace.headunit.view

/**
 * Represents display margins in pixels.
 * Used for both letterbox margins and user-configured margins.
 */
data class Margins(
    val top: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
    val right: Int = 0
) {
    /** Combine two Margins by adding their values */
    operator fun plus(other: Margins) = Margins(
        top = top + other.top,
        bottom = bottom + other.bottom,
        left = left + other.left,
        right = right + other.right
    )
    
    /** Check if any margin is non-zero */
    fun hasMargins() = top > 0 || bottom > 0 || left > 0 || right > 0
    
    /** Total horizontal margin */
    val horizontal: Int get() = left + right
    
    /** Total vertical margin */
    val vertical: Int get() = top + bottom
    
    companion object {
        val ZERO = Margins(0, 0, 0, 0)
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 2: Update Settings Storage

**Files:**
- Modify: `app/src/main/java/info/anodsplace/headunit/utils/Settings.kt`

**Step 1: Add RenderSurface enum and new properties**

Add after the existing `NightMode` enum:

```kotlin
enum class RenderSurface(val value: Int) {
    GLES_TEXTURE_VIEW(0),
    SURFACE_VIEW(1);

    companion object {
        private val map = values().associateBy(RenderSurface::value)
        fun fromInt(value: Int) = map[value] ?: GLES_TEXTURE_VIEW
    }
}
```

Add these properties after `manualDpi`:

```kotlin
// Render surface type (GLES TextureView default for better performance)
var renderSurface: RenderSurface
    get() = RenderSurface.fromInt(prefs.getInt("render-surface", RenderSurface.GLES_TEXTURE_VIEW.value))
    set(value) { prefs.edit().putInt("render-surface", value.value).apply() }

// Preserve aspect ratio with letterboxing
var preserveAspectRatio: Boolean
    get() = prefs.getBoolean("preserve-aspect-ratio", true)
    set(value) { prefs.edit().putBoolean("preserve-aspect-ratio", value).apply() }

// User margins in pixels
var marginTop: Int
    get() = prefs.getInt("margin-top", 0)
    set(value) { prefs.edit().putInt("margin-top", value).apply() }

var marginBottom: Int
    get() = prefs.getInt("margin-bottom", 0)
    set(value) { prefs.edit().putInt("margin-bottom", value).apply() }

var marginLeft: Int
    get() = prefs.getInt("margin-left", 0)
    set(value) { prefs.edit().putInt("margin-left", value).apply() }

var marginRight: Int
    get() = prefs.getInt("margin-right", 0)
    set(value) { prefs.edit().putInt("margin-right", value).apply() }
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 3: Implement AspectRatioCalculator

**Files:**
- Create: `app/src/main/java/info/anodsplace/headunit/view/AspectRatioCalculator.kt`

**Step 1: Create AspectRatioCalculator**

```kotlin
package info.anodsplace.headunit.view

import info.anodsplace.headunit.aap.protocol.Screen
import info.anodsplace.headunit.utils.Settings

/**
 * Calculates letterbox margins and DPI adjustments for aspect ratio correction.
 * 
 * Only adds top/bottom letterboxing to maintain landscape full-width.
 * Never adds left/right pillarbox.
 */
object AspectRatioCalculator {
    
    /**
     * Compute letterbox margins to preserve aspect ratio.
     * Only adds top/bottom bars, never left/right.
     *
     * @param videoWidth Video resolution width
     * @param videoHeight Video resolution height
     * @param displayWidth Actual display width
     * @param displayHeight Actual display height
     * @return Margins for letterboxing (only top/bottom will be non-zero)
     */
    fun computeLetterbox(
        videoWidth: Int,
        videoHeight: Int,
        displayWidth: Int,
        displayHeight: Int
    ): Margins {
        if (videoWidth <= 0 || videoHeight <= 0 || displayWidth <= 0 || displayHeight <= 0) {
            return Margins.ZERO
        }
        
        val videoAspect = videoWidth.toFloat() / videoHeight
        val displayAspect = displayWidth.toFloat() / displayHeight
        
        // Only add top/bottom letterbox when display is "taller" than video
        return if (displayAspect < videoAspect) {
            // Display is taller - need top/bottom letterbox
            val effectiveHeight = (displayWidth / videoAspect).toInt()
            val totalMargin = displayHeight - effectiveHeight
            val marginTop = totalMargin / 2
            val marginBottom = totalMargin - marginTop // Handle odd pixels
            Margins(top = marginTop, bottom = marginBottom, left = 0, right = 0)
        } else {
            // Display is wider or equal - no letterboxing needed
            Margins.ZERO
        }
    }
    
    /**
     * Combine letterbox margins with user-configured margins.
     */
    fun computeTotalMargins(
        letterbox: Margins,
        settings: Settings
    ): Margins {
        return letterbox + Margins(
            top = settings.marginTop,
            bottom = settings.marginBottom,
            left = settings.marginLeft,
            right = settings.marginRight
        )
    }
    
    /**
     * Compute adjusted DPI based on effective display area after letterboxing.
     * When letterboxing reduces the effective area, DPI is adjusted proportionally
     * so UI elements appear the same size.
     *
     * @param baseDpi The base DPI for the video resolution
     * @param displayHeight Original display height
     * @param letterbox Letterbox margins being applied
     * @return Adjusted DPI value
     */
    fun computeAdjustedDpi(
        baseDpi: Int,
        displayHeight: Int,
        letterbox: Margins
    ): Int {
        if (displayHeight <= 0) return baseDpi
        
        val effectiveHeight = displayHeight - letterbox.vertical
        if (effectiveHeight <= 0) return baseDpi
        
        val scaleFactor = effectiveHeight.toFloat() / displayHeight
        return (baseDpi * scaleFactor).toInt().coerceAtLeast(120) // Minimum 120 DPI
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 4: Create BaseProjectionView Interface

**Files:**
- Create: `app/src/main/java/info/anodsplace/headunit/view/BaseProjectionView.kt`

**Step 1: Create interface**

```kotlin
package info.anodsplace.headunit.view

import android.view.Surface
import android.view.SurfaceHolder
import info.anodsplace.headunit.aap.protocol.Screen

/**
 * Common interface for projection views (SurfaceView and TextureView implementations).
 */
interface BaseProjectionView {
    /** Set callback for surface lifecycle events */
    fun setSurfaceCallback(callback: SurfaceHolder.Callback)
    
    /** Get the screen configuration */
    fun screen(): Screen
    
    /** Get the current Surface for MediaCodec output */
    fun getSurface(): Surface?
    
    /** Apply margins to the view */
    fun applyMargins(margins: Margins)
    
    /** View width */
    val viewWidth: Int
    
    /** View height */
    val viewHeight: Int
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 5: Rename and Update SurfaceProjectionView

**Files:**
- Rename: `app/src/main/java/info/anodsplace/headunit/view/ProjectionView.kt` → `SurfaceProjectionView.kt`
- Modify: Update class to implement BaseProjectionView

**Step 1: Update the class**

```kotlin
package info.anodsplace.headunit.view

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

import info.anodsplace.headunit.App
import info.anodsplace.headunit.aap.protocol.Screen
import info.anodsplace.headunit.utils.AppLog

/**
 * SurfaceView-based projection view.
 * Fallback option for devices with TextureView issues.
 */
class SurfaceProjectionView : SurfaceView, SurfaceHolder.Callback, BaseProjectionView {
    private var videoController = App.provide(context).videoDecoderController
    private var surfaceCallback: SurfaceHolder.Callback? = null
    private var currentMargins: Margins = Margins.ZERO
    
    // The screen resolution that was negotiated with the phone
    private val screen: Screen
        get() = Screen.forResolution(App.provide(context).settings.activeResolution)

    init {
        // Configure SurfaceHolder for video playback
        // On Android 5.0+ (API 21), let the system/MediaCodec handle format negotiation
        // to avoid buffer queue issues with the new asynchronous RenderThread
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Pre-Lollipop: Explicitly set pixel format for proper buffer allocation
            holder.setFormat(PixelFormat.RGBX_8888)
        }
        holder.addCallback(this)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun setSurfaceCallback(callback: SurfaceHolder.Callback) {
        this.surfaceCallback = callback
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        videoController.stop("onDetachedFromWindow")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        AppLog.i { "surfaceCreated: view=${width}x${height}, screen=${screen.width}x${screen.height}, SDK=${Build.VERSION.SDK_INT}" }
        
        // Set fixed size to match the video resolution for proper buffer allocation
        // This enables hardware scaler usage which is efficient on all Android versions
        holder.setFixedSize(screen.width, screen.height)
        
        // On Android 5.0+ (API 21), the BufferQueue uses triple buffering with async allocation.
        // We need to ensure buffers are allocated before starting the decoder to avoid
        // displaying stale/garbage frames during the transition.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Post decoder start to next frame to allow buffer allocation to complete
            post {
                if (isAttachedToWindow) {
                    videoController.onSurfaceAvailable(holder, screen.width, screen.height)
                }
            }
        } else {
            // Pre-Lollipop: Start decoder immediately (synchronous buffer model)
            videoController.onSurfaceAvailable(holder, screen.width, screen.height)
        }
        
        surfaceCallback?.surfaceCreated(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        AppLog.i { "surfaceChanged: format=$format, size=${width}x${height}" }
        surfaceCallback?.surfaceChanged(holder, format, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        AppLog.i { "surfaceDestroyed" }
        videoController.stop("surfaceDestroyed")
        surfaceCallback?.surfaceDestroyed(holder)
    }
    
    override fun screen(): Screen = screen
    
    override fun getSurface(): Surface? = holder.surface
    
    override fun applyMargins(margins: Margins) {
        currentMargins = margins
        // Apply margins via layout params - handled by parent FrameLayout
        val params = layoutParams as? android.widget.FrameLayout.LayoutParams
        if (params != null) {
            params.topMargin = margins.top
            params.bottomMargin = margins.bottom
            params.leftMargin = margins.left
            params.rightMargin = margins.right
            layoutParams = params
        }
    }
    
    override val viewWidth: Int get() = width
    override val viewHeight: Int get() = height
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 6: Implement GlesProjectionView

**Files:**
- Create: `app/src/main/java/info/anodsplace/headunit/view/GlesProjectionView.kt`

**Step 1: Create TextureView-based implementation**

```kotlin
package info.anodsplace.headunit.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView

import info.anodsplace.headunit.App
import info.anodsplace.headunit.aap.protocol.Screen
import info.anodsplace.headunit.utils.AppLog

/**
 * TextureView-based projection view with GLES 2.0 support.
 * Default option - provides GPU-accelerated rendering with transform support.
 */
class GlesProjectionView : TextureView, TextureView.SurfaceTextureListener, BaseProjectionView {
    private var videoController = App.provide(context).videoDecoderController
    private var surfaceCallback: SurfaceHolder.Callback? = null
    private var currentMargins: Margins = Margins.ZERO
    private var surface: Surface? = null
    
    // Wrapper to adapt TextureView to SurfaceHolder.Callback interface
    private var holderCallbackAdapter: SurfaceHolderCallbackAdapter? = null
    
    // The screen resolution that was negotiated with the phone
    private val screen: Screen
        get() = Screen.forResolution(App.provide(context).settings.activeResolution)

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        surfaceTextureListener = this
    }

    override fun setSurfaceCallback(callback: SurfaceHolder.Callback) {
        this.surfaceCallback = callback
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        videoController.stop("onDetachedFromWindow")
        surface?.release()
        surface = null
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        AppLog.i { "onSurfaceTextureAvailable: view=${width}x${height}, screen=${screen.width}x${screen.height}" }
        
        // Set buffer size to match video resolution
        surfaceTexture.setDefaultBufferSize(screen.width, screen.height)
        
        // Create Surface from SurfaceTexture
        surface = Surface(surfaceTexture)
        
        // Apply transform matrix for proper scaling and margins
        updateTransform()
        
        // Create adapter for SurfaceHolder.Callback compatibility
        holderCallbackAdapter = SurfaceHolderCallbackAdapter(surface!!)
        
        // On Android 5.0+ (API 21), post to next frame for async buffer allocation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            post {
                if (isAttachedToWindow && surface != null) {
                    videoController.onSurfaceAvailable(holderCallbackAdapter!!, screen.width, screen.height)
                    surfaceCallback?.surfaceCreated(holderCallbackAdapter!!)
                }
            }
        } else {
            videoController.onSurfaceAvailable(holderCallbackAdapter!!, screen.width, screen.height)
            surfaceCallback?.surfaceCreated(holderCallbackAdapter!!)
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        AppLog.i { "onSurfaceTextureSizeChanged: ${width}x${height}" }
        updateTransform()
        holderCallbackAdapter?.let {
            surfaceCallback?.surfaceChanged(it, 0, width, height)
        }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        AppLog.i { "onSurfaceTextureDestroyed" }
        videoController.stop("surfaceTextureDestroyed")
        holderCallbackAdapter?.let {
            surfaceCallback?.surfaceDestroyed(it)
        }
        surface?.release()
        surface = null
        holderCallbackAdapter = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // Called when the SurfaceTexture is updated via updateTexImage()
        // No action needed - video frames are rendered automatically
    }
    
    /**
     * Update the transform matrix to handle aspect ratio and margins.
     */
    private fun updateTransform() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val videoWidth = screen.width.toFloat()
        val videoHeight = screen.height.toFloat()
        
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return
        }
        
        val matrix = Matrix()
        
        // Calculate effective display area after margins
        val effectiveWidth = viewWidth - currentMargins.horizontal
        val effectiveHeight = viewHeight - currentMargins.vertical
        
        // Scale video to fill effective area
        val scaleX = effectiveWidth / viewWidth
        val scaleY = effectiveHeight / viewHeight
        
        // Apply scale from center
        matrix.setScale(scaleX, scaleY, viewWidth / 2, viewHeight / 2)
        
        // Translate for margins (offset from center)
        val offsetX = (currentMargins.left - currentMargins.right) / 2f
        val offsetY = (currentMargins.top - currentMargins.bottom) / 2f
        matrix.postTranslate(offsetX, offsetY)
        
        setTransform(matrix)
        
        AppLog.i { "Transform updated: scale=($scaleX, $scaleY), offset=($offsetX, $offsetY), margins=$currentMargins" }
    }
    
    override fun screen(): Screen = screen
    
    override fun getSurface(): Surface? = surface
    
    override fun applyMargins(margins: Margins) {
        currentMargins = margins
        updateTransform()
    }
    
    override val viewWidth: Int get() = width
    override val viewHeight: Int get() = height
    
    /**
     * Adapter to provide SurfaceHolder-like interface for TextureView's Surface.
     * This allows the VideoDecoderController to work with both SurfaceView and TextureView.
     */
    private inner class SurfaceHolderCallbackAdapter(private val textureSurface: Surface) : SurfaceHolder {
        override fun getSurface(): Surface = textureSurface
        
        override fun addCallback(callback: SurfaceHolder.Callback) {
            // Not needed - we handle callbacks directly
        }
        
        override fun removeCallback(callback: SurfaceHolder.Callback) {
            // Not needed
        }
        
        override fun isCreating(): Boolean = false
        
        override fun setType(type: Int) {
            // Deprecated, ignored
        }
        
        override fun setFixedSize(width: Int, height: Int) {
            surfaceTexture?.setDefaultBufferSize(width, height)
        }
        
        override fun setSizeFromLayout() {
            // Not applicable for TextureView
        }
        
        override fun setFormat(format: Int) {
            // Not applicable for TextureView
        }
        
        override fun setKeepScreenOn(screenOn: Boolean) {
            // Handled at window level
        }
        
        override fun lockCanvas(): android.graphics.Canvas? = null
        
        override fun lockCanvas(dirty: android.graphics.Rect?): android.graphics.Canvas? = null
        
        override fun unlockCanvasAndPost(canvas: android.graphics.Canvas) {
            // Not used
        }
        
        override fun getSurfaceFrame(): android.graphics.Rect {
            return android.graphics.Rect(0, 0, screen.width, screen.height)
        }
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 7: Create ProjectionViewFactory

**Files:**
- Create: `app/src/main/java/info/anodsplace/headunit/view/ProjectionViewFactory.kt`

**Step 1: Create factory**

```kotlin
package info.anodsplace.headunit.view

import android.content.Context
import android.view.View
import info.anodsplace.headunit.utils.AppLog
import info.anodsplace.headunit.utils.Settings

/**
 * Factory for creating the appropriate projection view based on settings.
 */
object ProjectionViewFactory {
    
    /**
     * Create a projection view based on the render surface setting.
     *
     * @param context Android context
     * @param settings App settings
     * @return A View that implements BaseProjectionView
     */
    fun create(context: Context, settings: Settings): View {
        val view = when (settings.renderSurface) {
            Settings.RenderSurface.GLES_TEXTURE_VIEW -> {
                AppLog.i { "Creating GlesProjectionView (TextureView)" }
                GlesProjectionView(context)
            }
            Settings.RenderSurface.SURFACE_VIEW -> {
                AppLog.i { "Creating SurfaceProjectionView (SurfaceView)" }
                SurfaceProjectionView(context)
            }
        }
        
        // Apply initial margins if aspect ratio preservation is enabled
        if (settings.preserveAspectRatio) {
            // Margins will be calculated and applied when surface is ready
            // and display dimensions are known
        }
        
        return view
    }
    
    /**
     * Get the BaseProjectionView interface from a view created by this factory.
     */
    fun asBaseProjectionView(view: View): BaseProjectionView? {
        return view as? BaseProjectionView
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 8: Update Screen.kt with Letterbox Support

**Files:**
- Modify: `app/src/main/java/info/anodsplace/headunit/aap/protocol/Screen.kt`

**Step 1: Update computeDpi to support letterbox adjustment**

Replace the `computeDpi` function:

```kotlin
/**
 * Compute the effective DPI for the video resolution.
 * 
 * When preserveAspectRatio is true, adjusts DPI based on letterbox margins
 * so UI elements appear the same size in the reduced display area.
 *
 * @param screen The video resolution being requested
 * @param displayWidth The actual display width in pixels
 * @param displayHeight The actual display height in pixels
 * @param letterboxMargins The letterbox margins being applied (if any)
 * @return The computed DPI to send to the phone
 */
fun computeDpi(screen: Screen, displayWidth: Int, displayHeight: Int, letterboxMargins: info.anodsplace.headunit.view.Margins = info.anodsplace.headunit.view.Margins.ZERO): Int {
    val baseDpi = screen.baseDpi
    
    // If letterboxing is applied, adjust DPI proportionally
    if (letterboxMargins.hasMargins() && displayHeight > 0) {
        val effectiveHeight = displayHeight - letterboxMargins.vertical
        if (effectiveHeight > 0) {
            val scaleFactor = effectiveHeight.toFloat() / displayHeight
            return (baseDpi * scaleFactor).toInt().coerceAtLeast(120)
        }
    }
    
    return baseDpi
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 9: Update ServiceDiscoveryResponse

**Files:**
- Modify: `app/src/main/java/info/anodsplace/headunit/aap/protocol/messages/ServiceDiscoveryResponse.kt`

**Step 1: Update to use letterbox margins and adjusted DPI**

Add import at top:
```kotlin
import info.anodsplace.headunit.view.AspectRatioCalculator
import info.anodsplace.headunit.view.Margins
```

Update the makeProto function's video configuration section:

```kotlin
// Compute letterbox margins if aspect ratio preservation is enabled
val letterboxMargins = if (settings.preserveAspectRatio) {
    AspectRatioCalculator.computeLetterbox(screen.width, screen.height, displayWidth, displayHeight)
} else {
    Margins.ZERO
}

// Combine letterbox with user margins
val totalMargins = AspectRatioCalculator.computeTotalMargins(letterboxMargins, settings)

// Use manual DPI if set, otherwise compute based on screen stretch and letterbox
val computedDpi = Screen.computeDpi(screen, displayWidth, displayHeight, letterboxMargins)
val effectiveDpi = if (settings.manualDpi > 0) settings.manualDpi else computedDpi

AppLog.i { "Display: ${displayWidth}x${displayHeight}, resolution: ${screen.width}x${screen.height}, DPI: $effectiveDpi (manual: ${settings.manualDpi}, computed: $computedDpi), letterbox: $letterboxMargins, userMargins: (${settings.marginTop}, ${settings.marginBottom}, ${settings.marginLeft}, ${settings.marginRight})" }
```

And update the video config margins:
```kotlin
marginHeight = totalMargins.vertical
marginWidth = totalMargins.horizontal
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 10: Update arrays.xml with Render Surface Options

**Files:**
- Modify: `app/src/main/res/values/arrays.xml`

**Step 1: Add render surface arrays**

```xml
<!-- Render surface options -->
<string-array name="render_surface_entries">
    <item>GLES 2.0 (GPU backed surface)</item>
    <item>SurfaceView (Compatibility)</item>
</string-array>
<string-array name="render_surface_values">
    <item>0</item>
    <item>1</item>
</string-array>
```

**Step 2: Verify no XML errors**

Run: `./gradlew processDebugResources`

---

## Task 11: Update preferences_graphics.xml

**Files:**
- Modify: `app/src/main/res/xml/preferences_graphics.xml`

**Step 1: Add new preferences**

```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <ListPreference
        android:key="render_surface"
        android:title="Render surface"
        android:summary="GLES 2.0 is GPU backed for better performance. Use SurfaceView for compatibility."
        android:entries="@array/render_surface_entries"
        android:entryValues="@array/render_surface_values"
        android:defaultValue="0" />

    <ListPreference
        android:key="resolution"
        android:title="Resolution"
        android:summary="Select video resolution"
        android:entries="@array/resolution_entries"
        android:entryValues="@array/resolution_values"
        android:defaultValue="1080" />

    <SwitchPreferenceCompat
        android:key="preserve_aspect_ratio"
        android:title="Preserve aspect ratio"
        android:summary="Add letterbox bars to prevent distortion"
        android:defaultValue="true" />

    <ListPreference
        android:key="manual_dpi"
        android:title="DPI Scale"
        android:summary="Adjust UI element size (Auto = computed from screen)"
        android:entries="@array/dpi_entries"
        android:entryValues="@array/dpi_string_values"
        android:defaultValue="0" />

    <PreferenceCategory
        android:title="Margin Adjustment (pixels)"
        android:summary="Fine-tune display margins for your screen">

        <SeekBarPreference
            android:key="margin_top"
            android:title="Top margin"
            android:defaultValue="0"
            android:max="200"
            app:showSeekBarValue="true" />

        <SeekBarPreference
            android:key="margin_bottom"
            android:title="Bottom margin"
            android:defaultValue="0"
            android:max="200"
            app:showSeekBarValue="true" />

        <SeekBarPreference
            android:key="margin_left"
            android:title="Left margin"
            android:defaultValue="0"
            android:max="200"
            app:showSeekBarValue="true" />

        <SeekBarPreference
            android:key="margin_right"
            android:title="Right margin"
            android:defaultValue="0"
            android:max="200"
            app:showSeekBarValue="true" />

    </PreferenceCategory>

    <ListPreference
        android:key="day_night_mode"
        android:title="Day/Night Calculation"
        android:summary="How to determine day or night mode"
        android:entries="@array/day_night_entries"
        android:entryValues="@array/day_night_values"
        android:defaultValue="auto" />

</PreferenceScreen>
```

**Step 2: Verify no XML errors**

Run: `./gradlew processDebugResources`

---

## Task 12: Update GraphicsSettingsFragment

**Files:**
- Modify: `app/src/main/java/info/anodsplace/headunit/ui/settings/GraphicsSettingsFragment.kt`

**Step 1: Add preference change listeners for new settings**

```kotlin
package info.anodsplace.headunit.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceManager
import info.anodsplace.headunit.R
import info.anodsplace.headunit.utils.AppLog
import info.anodsplace.headunit.utils.Settings

class GraphicsSettingsFragment : BaseSettingsFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override val titleResId: Int = R.string.settings_graphics

    override val preferencesResId: Int = R.xml.preferences_graphics

    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(requireContext())

        // Initialize preferences with current values from Settings
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        // Sync DPI
        val currentDpi = settings.manualDpi
        prefs.edit().putString("manual_dpi", currentDpi.toString()).apply()
        
        // Sync render surface
        prefs.edit().putString("render_surface", settings.renderSurface.value.toString()).apply()
        
        // Sync preserve aspect ratio
        prefs.edit().putBoolean("preserve_aspect_ratio", settings.preserveAspectRatio).apply()
        
        // Sync margins
        prefs.edit()
            .putInt("margin_top", settings.marginTop)
            .putInt("margin_bottom", settings.marginBottom)
            .putInt("margin_left", settings.marginLeft)
            .putInt("margin_right", settings.marginRight)
            .apply()
        
        AppLog.i { "GraphicsSettings: initialized preferences" }
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.registerOnSharedPreferenceChangeListener(this)
        
        // Sync current preference values to Settings on resume
        syncAllToSettings(prefs)
    }

    override fun onPause() {
        super.onPause()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        syncAllToSettings(prefs)
    }

    private fun syncAllToSettings(prefs: SharedPreferences) {
        // DPI
        val dpi = prefs.getString("manual_dpi", "0")?.toIntOrNull() ?: 0
        settings.manualDpi = dpi
        
        // Render surface
        val renderSurface = prefs.getString("render_surface", "0")?.toIntOrNull() ?: 0
        settings.renderSurface = Settings.RenderSurface.fromInt(renderSurface)
        
        // Preserve aspect ratio
        settings.preserveAspectRatio = prefs.getBoolean("preserve_aspect_ratio", true)
        
        // Margins
        settings.marginTop = prefs.getInt("margin_top", 0)
        settings.marginBottom = prefs.getInt("margin_bottom", 0)
        settings.marginLeft = prefs.getInt("margin_left", 0)
        settings.marginRight = prefs.getInt("margin_right", 0)
        
        AppLog.i { "GraphicsSettings: synced to Settings - DPI=$dpi, renderSurface=$renderSurface, preserveAspect=${settings.preserveAspectRatio}, margins=(${settings.marginTop}, ${settings.marginBottom}, ${settings.marginLeft}, ${settings.marginRight})" }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null) return
        
        when (key) {
            "manual_dpi" -> {
                val dpi = sharedPreferences.getString(key, "0")?.toIntOrNull() ?: 0
                settings.manualDpi = dpi
                AppLog.i { "GraphicsSettings: manual_dpi changed to $dpi" }
            }
            "render_surface" -> {
                val value = sharedPreferences.getString(key, "0")?.toIntOrNull() ?: 0
                settings.renderSurface = Settings.RenderSurface.fromInt(value)
                AppLog.i { "GraphicsSettings: render_surface changed to ${settings.renderSurface}" }
            }
            "preserve_aspect_ratio" -> {
                settings.preserveAspectRatio = sharedPreferences.getBoolean(key, true)
                AppLog.i { "GraphicsSettings: preserve_aspect_ratio changed to ${settings.preserveAspectRatio}" }
            }
            "margin_top" -> {
                settings.marginTop = sharedPreferences.getInt(key, 0)
                AppLog.i { "GraphicsSettings: margin_top changed to ${settings.marginTop}" }
            }
            "margin_bottom" -> {
                settings.marginBottom = sharedPreferences.getInt(key, 0)
                AppLog.i { "GraphicsSettings: margin_bottom changed to ${settings.marginBottom}" }
            }
            "margin_left" -> {
                settings.marginLeft = sharedPreferences.getInt(key, 0)
                AppLog.i { "GraphicsSettings: margin_left changed to ${settings.marginLeft}" }
            }
            "margin_right" -> {
                settings.marginRight = sharedPreferences.getInt(key, 0)
                AppLog.i { "GraphicsSettings: margin_right changed to ${settings.marginRight}" }
            }
        }
    }

    companion object {
        fun newInstance() = GraphicsSettingsFragment()
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 13: Update Layout and Activity to Use Factory

**Files:**
- Modify: `app/src/main/res/layout/activity_headunit.xml`
- Modify: `app/src/main/java/info/anodsplace/headunit/app/SurfaceActivity.kt`

**Step 1: Update layout to use FrameLayout container**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/projection_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- ProjectionView is added programmatically by SurfaceActivity -->

</FrameLayout>
```

**Step 2: Update SurfaceActivity to use factory**

```kotlin
package info.anodsplace.headunit.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

import info.anodsplace.headunit.R
import info.anodsplace.headunit.utils.Settings
import info.anodsplace.headunit.utils.hideSystemUI
import info.anodsplace.headunit.view.BaseProjectionView
import info.anodsplace.headunit.view.ProjectionViewFactory


abstract class SurfaceActivity : Activity() {

    private lateinit var container: FrameLayout
    private var projectionView: View? = null
    
    protected val surface: BaseProjectionView
        get() = projectionView as BaseProjectionView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_headunit)
        
        container = findViewById(R.id.projection_container)
        
        // Create projection view based on settings
        val settings = Settings(this)
        projectionView = ProjectionViewFactory.create(this, settings)
        
        // Add to container with match_parent
        container.addView(projectionView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        
        window.decorView.setOnSystemUiVisibilityChangeListener { window.decorView.hideSystemUI() }
        window.decorView.hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        window.decorView.hideSystemUI()
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 14: Update AapProjectionActivity

**Files:**
- Modify: `app/src/main/java/info/anodsplace/headunit/aap/AapProjectionActivity.kt`

**Step 1: Update to work with BaseProjectionView**

Change `surface.screen()` calls and touch handling to work with the interface.

The existing code should mostly work since `BaseProjectionView` has `screen()` method. Just need to ensure `surface.width/height` uses the interface properties.

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`

---

## Task 15: Final Build and Test

**Step 1: Full build**

Run: `./gradlew assembleDebug`

**Step 2: Manual testing checklist**

- [ ] App launches without crash
- [ ] Graphics settings shows new options (render surface, aspect ratio, margins)
- [ ] Changing render surface setting works
- [ ] Aspect ratio toggle affects letterboxing
- [ ] Margin sliders adjust display margins
- [ ] Video projection works with both render surfaces
- [ ] Touch input is correctly scaled

**Step 3: Commit all changes**

```bash
git add -A
git commit -m "feat(video): add projection scaling, GLES TextureView, and margin controls

- Add GlesProjectionView with TextureView for GPU-accelerated rendering
- Add aspect ratio preservation with top/bottom letterboxing
- Add user-configurable margins (px with % display)
- Add render surface selection (GLES 2.0 default, SurfaceView fallback)
- Auto-adjust DPI based on effective display area
- Update Graphics settings with new controls

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Summary

| Task | Component | Files |
|------|-----------|-------|
| 1 | Margins data class | `view/Margins.kt` |
| 2 | Settings storage | `utils/Settings.kt` |
| 3 | AspectRatioCalculator | `view/AspectRatioCalculator.kt` |
| 4 | BaseProjectionView interface | `view/BaseProjectionView.kt` |
| 5 | SurfaceProjectionView | `view/SurfaceProjectionView.kt` |
| 6 | GlesProjectionView | `view/GlesProjectionView.kt` |
| 7 | ProjectionViewFactory | `view/ProjectionViewFactory.kt` |
| 8 | Screen.kt update | `aap/protocol/Screen.kt` |
| 9 | ServiceDiscoveryResponse | `aap/protocol/messages/ServiceDiscoveryResponse.kt` |
| 10 | arrays.xml | `res/values/arrays.xml` |
| 11 | preferences_graphics.xml | `res/xml/preferences_graphics.xml` |
| 12 | GraphicsSettingsFragment | `ui/settings/GraphicsSettingsFragment.kt` |
| 13 | Layout and SurfaceActivity | `res/layout/activity_headunit.xml`, `app/SurfaceActivity.kt` |
| 14 | AapProjectionActivity | `aap/AapProjectionActivity.kt` |
| 15 | Final build and test | - |
