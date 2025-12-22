package info.anodsplace.headunit.view

import android.view.SurfaceHolder
import android.view.View
import info.anodsplace.headunit.aap.protocol.Screen

/**
 * Interface for projection views that display Android Auto video.
 * Implemented by both SurfaceProjectionView (SurfaceView) and GlesProjectionView (TextureView).
 */
interface BaseProjectionView {

    /**
     * Get the underlying View for layout purposes.
     */
    fun asView(): View

    /**
     * Set a callback to receive surface lifecycle events.
     */
    fun setSurfaceCallback(callback: SurfaceHolder.Callback)

    /**
     * Get the current screen configuration (video resolution).
     */
    fun screen(): Screen

    /**
     * Apply margins to the projection view.
     * This includes both letterbox margins (for aspect ratio) and user-defined margins.
     *
     * @param margins Combined margins to apply
     */
    fun applyMargins(margins: Margins)

    /**
     * Get the effective display width after margins are applied.
     */
    fun getEffectiveWidth(): Int

    /**
     * Get the effective display height after margins are applied.
     */
    fun getEffectiveHeight(): Int
}
