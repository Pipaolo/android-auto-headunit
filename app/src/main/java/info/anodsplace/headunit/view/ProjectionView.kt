package info.anodsplace.headunit.view

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

import info.anodsplace.headunit.App
import info.anodsplace.headunit.decoder.VideoDecoderController
import info.anodsplace.headunit.utils.AppLog

class ProjectionView : SurfaceView, SurfaceHolder.Callback {
    private var videoController = App.provide(context).videoDecoderController
    private var surfaceCallback: SurfaceHolder.Callback? = null

    init {
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
        AppLog.i { "holder $holder" }
        surfaceCallback?.surfaceCreated(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        AppLog.i { "holder $holder, format: $format, width: $width, height: $height" }
        videoController.onSurfaceAvailable(holder, width, height)
        surfaceCallback?.surfaceChanged(holder, format, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        AppLog.i { "holder $holder" }
        videoController.stop("surfaceDestroyed")
        surfaceCallback?.surfaceDestroyed(holder)
    }
}
