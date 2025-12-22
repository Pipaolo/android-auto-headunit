package info.anodsplace.headunit.view

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout

import info.anodsplace.headunit.App
import info.anodsplace.headunit.aap.protocol.Screen
import info.anodsplace.headunit.utils.AppLog

/**
 * SurfaceView-based projection view for Android Auto video.
 * This is the fallback/compatibility implementation that uses standard SurfaceView.
 * Margins are applied via layout parameters.
 */
class SurfaceProjectionView : SurfaceView, SurfaceHolder.Callback, BaseProjectionView {
    private var videoController = App.provide(context).videoDecoderController
    private var surfaceCallback: SurfaceHolder.Callback? = null
    private var currentMargins = Margins.ZERO
    
    // The screen resolution that was negotiated with the phone
    private val screenConfig: Screen
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

    override fun asView(): View = this

    override fun setSurfaceCallback(callback: SurfaceHolder.Callback) {
        this.surfaceCallback = callback
    }

    override fun screen(): Screen = screenConfig

    override fun applyMargins(margins: Margins) {
        currentMargins = margins
        
        // Apply margins via layout parameters
        val params = layoutParams
        if (params is FrameLayout.LayoutParams) {
            params.topMargin = margins.top
            params.bottomMargin = margins.bottom
            params.leftMargin = margins.left
            params.rightMargin = margins.right
            layoutParams = params
        }
    }

    override fun getEffectiveWidth(): Int {
        return width - currentMargins.horizontal
    }

    override fun getEffectiveHeight(): Int {
        return height - currentMargins.vertical
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        videoController.stop("onDetachedFromWindow")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        AppLog.i { "surfaceCreated: view=${width}x${height}, screen=${screenConfig.width}x${screenConfig.height}, SDK=${Build.VERSION.SDK_INT}" }
        
        // Set fixed size to match the video resolution for proper buffer allocation
        // This enables hardware scaler usage which is efficient on all Android versions
        holder.setFixedSize(screenConfig.width, screenConfig.height)
        
        // On Android 5.0+ (API 21), the BufferQueue uses triple buffering with async allocation.
        // We need to ensure buffers are allocated before starting the decoder to avoid
        // displaying stale/garbage frames during the transition.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Post decoder start to next frame to allow buffer allocation to complete
            post {
                if (isAttachedToWindow) {
                    videoController.onSurfaceAvailable(holder, screenConfig.width, screenConfig.height)
                }
            }
        } else {
            // Pre-Lollipop: Start decoder immediately (synchronous buffer model)
            videoController.onSurfaceAvailable(holder, screenConfig.width, screenConfig.height)
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
}
