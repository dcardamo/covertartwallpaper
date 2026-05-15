package ca.hld.covertart.render

/** Inclusive-left, exclusive-right integer rectangle. No platform dependency. */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Tunable visual parameters for the "G" treatment. The defaults are the
 * preview-harness starting point — iterate on these via build/previews/.
 */
data class RenderConfig(
    /** Opacity of the black scrim at x = 0. */
    val scrimStartAlpha: Float = 0.75f,
    /** Scrim fades to transparent by this fraction of the target width. */
    val scrimEndFraction: Float = 0.35f,
)

/** Pure description of how to paint one wallpaper frame. No platform types. */
data class RenderPlan(
    val targetSize: IntRect,
    val srcRect: IntRect,
    val dstRect: IntRect,
    val scrimRect: IntRect,
    val scrimStartAlpha: Float,
    val scrimEndFraction: Float,
)

/**
 * "G" treatment: scale the source so its HEIGHT fills the target, anchor its
 * RIGHT edge to the target's right edge (the left slice runs off-screen behind
 * where Niagara's text sits). No vertical crop.
 */
fun planRender(
    srcWidth: Int,
    srcHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    config: RenderConfig = RenderConfig(),
): RenderPlan {
    require(srcWidth > 0 && srcHeight > 0) { "source dimensions must be positive" }
    require(targetWidth > 0 && targetHeight > 0) { "target dimensions must be positive" }

    val scale = targetHeight.toFloat() / srcHeight.toFloat()
    val scaledWidth = Math.round(srcWidth * scale)
    val dstLeft = targetWidth - scaledWidth // negative when the art overflows left edge

    val scrimWidth = Math.round(targetWidth * config.scrimEndFraction)

    return RenderPlan(
        targetSize = IntRect(0, 0, targetWidth, targetHeight),
        srcRect = IntRect(0, 0, srcWidth, srcHeight),
        dstRect = IntRect(dstLeft, 0, targetWidth, targetHeight),
        scrimRect = IntRect(0, 0, scrimWidth, targetHeight),
        scrimStartAlpha = config.scrimStartAlpha,
        scrimEndFraction = config.scrimEndFraction,
    )
}
