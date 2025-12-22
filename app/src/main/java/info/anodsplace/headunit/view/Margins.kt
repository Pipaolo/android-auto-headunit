package info.anodsplace.headunit.view

/**
 * Data class representing screen margins in pixels.
 * Used for letterboxing (aspect ratio correction) and user-defined margins.
 */
data class Margins(
    val top: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
    val right: Int = 0
) {
    /**
     * Combine two Margins by adding their values.
     * Useful for combining letterbox margins with user margins.
     */
    operator fun plus(other: Margins) = Margins(
        top = top + other.top,
        bottom = bottom + other.bottom,
        left = left + other.left,
        right = right + other.right
    )

    /**
     * Check if any margin is non-zero.
     */
    fun hasMargins() = top > 0 || bottom > 0 || left > 0 || right > 0

    /**
     * Total horizontal margin (left + right).
     */
    val horizontal: Int get() = left + right

    /**
     * Total vertical margin (top + bottom).
     */
    val vertical: Int get() = top + bottom

    companion object {
        val ZERO = Margins(0, 0, 0, 0)
    }
}
