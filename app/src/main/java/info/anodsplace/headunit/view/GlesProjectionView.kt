package info.anodsplace.headunit.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.View

import info.anodsplace.headunit.App
import info.anodsplace.headunit.aap.protocol.Screen
import info.anodsplace.headunit.utils.AppLog

/**
 * TextureView-based projection view for Android Auto video.
 * Uses GPU-backed rendering with transform matrix for aspect ratio correction and margins.
 * This is the preferred implementation for better performance.
 */
class GlesProjectionView : TextureView, TextureView.SurfaceTextureListener, BaseProjectionView {
    private var videoController = App.provide(context).videoDecoderController
    private var surfaceCallback: SurfaceHolder.Callback? = null
    private var currentMargins = Margins.ZERO
    private var surface: Surface? = null
    
    // Transform matrix for positioning and scaling
    private val transformMatrix = Matrix()
    
    // Adapter to bridge TextureView's SurfaceTexture to SurfaceHolder.Callback
    private var surfaceHolderAdapter: SurfaceHolderAdapter? = null
    
    // The screen resolution that was negotiated with the phone
    private val screenConfig: Screen
        get() = Screen.forResolution(App.provide(context).settings.activeResolution)

    init {
        surfaceTextureListener = this
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun asView(): View = this

    override fun setSurfaceCallback(callback: SurfaceHolder.Callback) {
        this.surfaceCallback = callback
    }

    override fun screen(): Screen = screenConfig

    override fun applyMargins(margins: Margins) {
        currentMargins = margins
        updateTransform()
    }

    override fun getEffectiveWidth(): Int {
        return width - currentMargins.horizontal
    }

    override fun getEffectiveHeight(): Int {
        return height - currentMargins.vertical
    }

    /**
     * Update the transform matrix to position the video with margins.
     * The video is scaled to fit within the effective area (after margins).
     */
    private fun updateTransform() {
        if (width == 0 || height == 0) return

        val effectiveWidth = width - currentMargins.horizontal
        val effectiveHeight = height - currentMargins.vertical

        if (effectiveWidth <= 0 || effectiveHeight <= 0) return

        // Calculate scale to fit video within effective area
        val scaleX = effectiveWidth.toFloat() / width.toFloat()
        val scaleY = effectiveHeight.toFloat() / height.toFloat()

        transformMatrix.reset()
        
        // Scale to effective size
        transformMatrix.setScale(scaleX, scaleY)
        
        // Translate to account for margins (centered with margins applied)
        transformMatrix.postTranslate(currentMargins.left.toFloat(), currentMargins.top.toFloat())
        
        setTransform(transformMatrix)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        videoController.stop("onDetachedFromWindow")
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        AppLog.i { "onSurfaceTextureAvailable: size=${width}x${height}, screen=${screenConfig.width}x${screenConfig.height}" }
        
        // Set buffer size to match video resolution
        surfaceTexture.setDefaultBufferSize(screenConfig.width, screenConfig.height)
        
        // Create Surface from SurfaceTexture for MediaCodec
        surface = Surface(surfaceTexture)
        
        // Create adapter and notify callback
        surfaceHolderAdapter = SurfaceHolderAdapter(surface!!, screenConfig.width, screenConfig.height)
        surfaceCallback?.surfaceCreated(surfaceHolderAdapter!!)
        
        // Update transform with current margins
        updateTransform()
        
        // Start video decoder
        videoController.onSurfaceAvailable(surfaceHolderAdapter!!, screenConfig.width, screenConfig.height)
        
        surfaceCallback?.surfaceChanged(surfaceHolderAdapter!!, 0, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        AppLog.i { "onSurfaceTextureSizeChanged: size=${width}x${height}" }
        
        // Update transform for new size
        updateTransform()
        
        surfaceHolderAdapter?.let { adapter ->
            surfaceCallback?.surfaceChanged(adapter, 0, width, height)
        }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        AppLog.i { "onSurfaceTextureDestroyed" }
        
        videoController.stop("surfaceTextureDestroyed")
        
        surfaceHolderAdapter?.let { adapter ->
            surfaceCallback?.surfaceDestroyed(adapter)
        }
        
        surface?.release()
        surface = null
        surfaceHolderAdapter = null
        
        // Return true to release the SurfaceTexture
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // Called when the SurfaceTexture is updated (frame rendered)
        // No action needed for video playback
    }

    /**
     * Adapter class to bridge TextureView's Surface to SurfaceHolder interface.
     * This allows reusing the existing video controller code that expects SurfaceHolder.
     */
    private inner class SurfaceHolderAdapter(
        private val surface: Surface,
        private val surfaceWidth: Int,
        private val surfaceHeight: Int
    ) : SurfaceHolder {

        override fun getSurface(): Surface = surface

        override fun addCallback(callback: SurfaceHolder.Callback) {
            // Not used - we manage callbacks directly
        }

        override fun removeCallback(callback: SurfaceHolder.Callback) {
            // Not used - we manage callbacks directly
        }

        override fun isCreating(): Boolean = false

        override fun setType(type: Int) {
            // Deprecated, ignored
        }

        override fun setFixedSize(width: Int, height: Int) {
            // TextureView uses setDefaultBufferSize on SurfaceTexture instead
            surfaceTexture?.setDefaultBufferSize(width, height)
        }

        override fun setSizeFromLayout() {
            // Not applicable for TextureView
        }

        override fun setFormat(format: Int) {
            // TextureView always uses RGBA_8888, ignoring format requests
        }

        override fun setKeepScreenOn(screenOn: Boolean) {
            this@GlesProjectionView.keepScreenOn = screenOn
        }

        override fun lockCanvas(): android.graphics.Canvas? {
            // Not supported for video playback surface
            return null
        }

        override fun lockCanvas(dirty: android.graphics.Rect?): android.graphics.Canvas? {
            // Not supported for video playback surface
            return null
        }

        override fun unlockCanvasAndPost(canvas: android.graphics.Canvas) {
            // Not supported for video playback surface
        }

        override fun getSurfaceFrame(): android.graphics.Rect {
            return android.graphics.Rect(0, 0, surfaceWidth, surfaceHeight)
        }
    }
}
