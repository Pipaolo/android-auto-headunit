package info.anodsplace.headunit.aap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder

import info.anodsplace.headunit.App
import info.anodsplace.headunit.aap.protocol.messages.TouchEvent
import info.anodsplace.headunit.aap.protocol.messages.VideoFocusEvent
import info.anodsplace.headunit.app.SurfaceActivity
import info.anodsplace.headunit.utils.AppLog
import info.anodsplace.headunit.utils.IntentFilters
import info.anodsplace.headunit.contract.KeyIntent

class AapProjectionActivity : SurfaceActivity(), SurfaceHolder.Callback {
    // Pre-allocated list for touch pointer data - avoids GC on every touch event
    // Max 10 pointers is more than enough for any multi-touch scenario
    private val pointerDataPool = ArrayList<Triple<Int, Int, Int>>(10)

    private val disconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    private val keyCodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = intent.getParcelableExtra<KeyEvent>(KeyIntent.extraEvent) ?: return
            onKeyEvent(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        surface.setSurfaceCallback(this)
        surface.asView().setOnTouchListener { _, event ->
            sendTouchEvent(event)
            true
        }

        // Register disconnect receiver with LocalBroadcastManager for reliable intra-app communication
        App.provide(this).localBroadcastManager.registerReceiver(disconnectReceiver, IntentFilters.disconnect)
    }

    override fun onDestroy() {
        super.onDestroy()
        App.provide(this).localBroadcastManager.unregisterReceiver(disconnectReceiver)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(keyCodeReceiver)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keyCodeReceiver, IntentFilters.keyEvent, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(keyCodeReceiver, IntentFilters.keyEvent)
        }
    }

    val transport: AapTransport
        get() = App.provide(this).transport

    override fun surfaceCreated(holder: SurfaceHolder) {

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        transport.send(VideoFocusEvent(gain = true, unsolicited = false))
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        transport.send(VideoFocusEvent(gain = false, unsolicited = false))
    }

    private fun sendTouchEvent(event: MotionEvent) {
        val actionMasked = event.actionMasked
        val action = TouchEvent.motionEventToAction(actionMasked) ?: return
        val ts = SystemClock.elapsedRealtime()

        // Get the screen resolution (what the phone expects)
        val screen = surface.screen()

        // Calculate scale factors from rendered view to phone's expected resolution
        // Use effective width/height (after margins) for proper touch coordinate mapping
        val effectiveWidth = surface.getEffectiveWidth()
        val effectiveHeight = surface.getEffectiveHeight()
        
        // Fallback to view dimensions if effective size not yet calculated
        val viewWidth = if (effectiveWidth > 0) effectiveWidth else surface.asView().width
        val viewHeight = if (effectiveHeight > 0) effectiveHeight else surface.asView().height
        
        if (viewWidth <= 0 || viewHeight <= 0) return
        
        val scaleX = screen.width.toFloat() / viewWidth
        val scaleY = screen.height.toFloat() / viewHeight

        // Reuse pre-allocated list to avoid GC pressure on Android 4.3
        pointerDataPool.clear()
        repeat(event.pointerCount) { pointerIndex ->
            val pointerId = event.getPointerId(pointerIndex)
            val x = (event.getX(pointerIndex) * scaleX).toInt()
            val y = (event.getY(pointerIndex) * scaleY).toInt()
            if (x < 0 || x >= 65535 || y < 0 || y >= 65535) return
            pointerDataPool.add(Triple(pointerId, x, y))
        }

        transport.send(TouchEvent(ts, action, event.actionIndex, pointerDataPool))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        AppLog.i { "KeyCode: $keyCode" }
        onKeyEvent(keyCode, true)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        AppLog.i { "KeyCode: $keyCode" }
        onKeyEvent(keyCode, false)
        return super.onKeyUp(keyCode, event)
    }

    private fun onKeyEvent(keyCode: Int, isPress: Boolean) {
        transport.send(keyCode, isPress)
    }

    companion object {
        const val EXTRA_FOCUS = "focus"

        fun intent(context: Context): Intent {
            val aapIntent = Intent(context, AapProjectionActivity::class.java)
            aapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return aapIntent
        }
    }
}