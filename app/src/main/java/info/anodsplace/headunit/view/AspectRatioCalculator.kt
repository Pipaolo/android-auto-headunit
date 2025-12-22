package info.anodsplace.headunit.view

import info.anodsplace.headunit.aap.protocol.Screen

/**
 * Calculates letterbox margins for aspect ratio correction and adjusts DPI accordingly.
 * 
 * Only adds TOP/BOTTOM letterboxing to maintain landscape full-width always.
 * When letterboxing reduces the effective display area, DPI is scaled proportionally
 * so that UI elements maintain the same apparent size.
 */
object AspectRatioCalculator {

    /**
     * Result of aspect ratio calculation containing letterbox margins and adjusted DPI.
     */
    data class Result(
        val letterboxMargins: Margins,
        val adjustedDpi: Int,
        val effectiveHeight: Int
    )

    /**
     * Calculate letterbox margins needed to preserve the video aspect ratio.
     * Only adds top/bottom margins (never left/right) to ensure landscape full-width.
     *
     * @param videoWidth Width of the video resolution (e.g., 1920)
     * @param videoHeight Height of the video resolution (e.g., 1080)
     * @param displayWidth Width of the display in pixels
     * @param displayHeight Height of the display in pixels
     * @param baseDpi The base DPI for the video resolution
     * @return Result containing letterbox margins and adjusted DPI
     */
    fun calculate(
        videoWidth: Int,
        videoHeight: Int,
        displayWidth: Int,
        displayHeight: Int,
        baseDpi: Int
    ): Result {
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val displayAspect = displayWidth.toFloat() / displayHeight.toFloat()

        val letterboxMargins: Margins
        val effectiveHeight: Int

        if (displayAspect < videoAspect) {
            // Display is "taller" than video - add top/bottom bars
            val calculatedHeight = displayWidth.toFloat() / videoAspect
            effectiveHeight = calculatedHeight.toInt()
            val totalMargin = displayHeight - effectiveHeight
            val halfMargin = totalMargin / 2
            letterboxMargins = Margins(
                top = halfMargin,
                bottom = totalMargin - halfMargin, // Handle odd pixel counts
                left = 0,
                right = 0
            )
        } else {
            // Display is wider or equal - no letterboxing needed
            effectiveHeight = displayHeight
            letterboxMargins = Margins.ZERO
        }

        // Adjust DPI proportionally based on effective area
        // Smaller visible area = lower DPI to keep UI elements same apparent size
        val scaleFactor = effectiveHeight.toFloat() / displayHeight.toFloat()
        val adjustedDpi = (baseDpi * scaleFactor).toInt()

        return Result(
            letterboxMargins = letterboxMargins,
            adjustedDpi = adjustedDpi,
            effectiveHeight = effectiveHeight
        )
    }

    /**
     * Calculate letterbox margins for a given Screen and display dimensions.
     *
     * @param screen The video resolution screen configuration
     * @param displayWidth Width of the display in pixels
     * @param displayHeight Height of the display in pixels
     * @return Result containing letterbox margins and adjusted DPI
     */
    fun calculate(screen: Screen, displayWidth: Int, displayHeight: Int): Result {
        return calculate(
            videoWidth = screen.width,
            videoHeight = screen.height,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            baseDpi = screen.baseDpi
        )
    }

    /**
     * Combine letterbox margins with user-defined margins.
     *
     * @param letterboxMargins Margins from aspect ratio calculation
     * @param userMargins User-defined margins from settings
     * @return Combined margins (letterbox + user)
     */
    fun combineMargins(letterboxMargins: Margins, userMargins: Margins): Margins {
        return letterboxMargins + userMargins
    }
}
