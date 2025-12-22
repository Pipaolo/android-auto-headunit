package info.anodsplace.headunit.view

import android.content.Context
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
    
    // Track if decoder has been started to avoid double-start
    private var decoderStarted = false
    
    // The screen resolution that was negotiated with the phone
    private val screenConfig: Screen
        get() = Screen.forResolution(App.provide(context).settings.activeResolution)

    init {
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
        decoderStarted = false
        videoController.stop("onDetachedFromWindow")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        AppLog.i { "surfaceCreated: view=${width}x${height}, screen=${screenConfig.width}x${screenConfig.height}, SDK=${Build.VERSION.SDK_INT}" }
        
        // Set fixed size to match the video resolution for proper buffer allocation
        // This enables hardware scaler usage which is efficient on all Android versions
        holder.setFixedSize(screenConfig.width, screenConfig.height)
        
        // On Android 5.0+ (API 21), start decoder after posting to allow buffer allocation
        // On pre-Lollipop, we must wait for surfaceChanged when buffers are fully allocated
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            post {
                if (windowToken != null && !decoderStarted) {
                    decoderStarted = true
                    videoController.onSurfaceAvailable(holder, screenConfig.width, screenConfig.height)
                }
            }
        }
        // Pre-Lollipop: decoder will be started in surfaceChanged after BufferQueue is ready
        
        surfaceCallback?.surfaceCreated(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        AppLog.i { "surfaceChanged: format=$format, size=${width}x${height}" }
        
        // On pre-Lollipop, start decoder here after BufferQueue is fully initialized
        // This avoids "can't dequeue multiple buffers without setting the buffer count" error
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && !decoderStarted) {
            decoderStarted = true
            videoController.onSurfaceAvailable(holder, screenConfig.width, screenConfig.height)
        }
        
        surfaceCallback?.surfaceChanged(holder, format, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        AppLog.i { "surfaceDestroyed" }
        decoderStarted = false
        videoController.stop("surfaceDestroyed")
        surfaceCallback?.surfaceDestroyed(holder)
    }
}
