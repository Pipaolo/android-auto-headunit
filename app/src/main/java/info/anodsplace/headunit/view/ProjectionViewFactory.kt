package info.anodsplace.headunit.view

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import info.anodsplace.headunit.utils.Settings

/**
 * Factory for creating projection views.
 * Uses SurfaceView for optimal video playback performance and sharpness.
 */
object ProjectionViewFactory {

    /**
     * Create a projection view.
     *
     * Uses SurfaceView which provides:
     * - Direct hardware compositor rendering (no GPU texture copy)
     * - Hardware video scaler (sharp scaling, no bilinear blur)
     * - Lower latency and power usage
     *
     * @param context The context for view creation
     * @param settings The app settings
     * @return A SurfaceProjectionView instance
     */
    fun create(context: Context, settings: Settings): BaseProjectionView {
        return SurfaceProjectionView(context)
    }

    /**
     * Create a projection view and add it to a container with full match_parent layout.
     *
     * @param container The parent container (typically a FrameLayout)
     * @param settings The app settings containing render surface preference
     * @return The created BaseProjectionView
     */
    fun createAndAttach(container: FrameLayout, settings: Settings): BaseProjectionView {
        val view = create(container.context, settings)
        
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        container.addView(view.asView(), params)
        
        return view
    }

    /**
     * Calculate and apply margins to a projection view based on settings.
     *
     * @param view The projection view to apply margins to
     * @param settings The app settings
     * @param displayWidth The actual display width
     * @param displayHeight The actual display height
     * @return The combined margins that were applied
     */
    fun applyMarginsFromSettings(
        view: BaseProjectionView,
        settings: Settings,
        displayWidth: Int,
        displayHeight: Int
    ): Margins {
        val screen = view.screen()
        
        // Calculate letterbox margins if aspect ratio preservation is enabled
        val letterboxResult = if (settings.preserveAspectRatio) {
            AspectRatioCalculator.calculate(screen, displayWidth, displayHeight)
        } else {
            AspectRatioCalculator.Result(
                letterboxMargins = Margins.ZERO,
                adjustedDpi = screen.baseDpi,
                effectiveHeight = displayHeight
            )
        }
        
        // Get user-defined margins from settings
        val userMargins = Margins(
            top = settings.marginTop,
            bottom = settings.marginBottom,
            left = settings.marginLeft,
            right = settings.marginRight
        )
        
        // Combine letterbox and user margins
        val combinedMargins = AspectRatioCalculator.combineMargins(
            letterboxResult.letterboxMargins,
            userMargins
        )
        
        // Apply to view
        view.applyMargins(combinedMargins)
        
        return combinedMargins
    }
}
