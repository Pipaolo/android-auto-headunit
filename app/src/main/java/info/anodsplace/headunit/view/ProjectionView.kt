package info.anodsplace.headunit.view

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

import info.anodsplace.headunit.App
import info.anodsplace.headunit.aap.protocol.Screen
import info.anodsplace.headunit.utils.AppLog

class ProjectionView : SurfaceView, SurfaceHolder.Callback {
    private var videoController = App.provide(context).videoDecoderController
    private var surfaceCallback: SurfaceHolder.Callback? = null
    
    // The screen resolution that was negotiated with the phone
    private val screen: Screen
        get() = Screen.forResolution(App.provide(context).settings.activeResolution)

    init {
        // Configure SurfaceHolder for video playback
        holder.setFormat(PixelFormat.RGBX_8888)
        holder.addCallback(this)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setSurfaceCallback(surfaceCallback: SurfaceHolder.Callback) {
        this.surfaceCallback = surfaceCallback
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        videoController.stop("onDetachedFromWindow")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        AppLog.i { "surfaceCreated: view=${width}x${height}, screen=${screen.width}x${screen.height}" }
        
        // Set fixed size to match the video resolution for proper buffer allocation
        // This is important for older Android versions
        holder.setFixedSize(screen.width, screen.height)
        
        // Start decoder with the negotiated screen resolution
        videoController.onSurfaceAvailable(holder, screen.width, screen.height)
        
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
    
    fun screen(): Screen = screen
}
