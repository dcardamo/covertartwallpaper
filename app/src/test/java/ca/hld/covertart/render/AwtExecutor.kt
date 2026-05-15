package ca.hld.covertart.render

import ca.hld.covertart.core.SourceImage
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/** A SourceImage backed by a java.awt BufferedImage (host/preview path). */
class AwtSourceImage(val image: BufferedImage) : SourceImage {
    override val width: Int get() = image.width
    override val height: Int get() = image.height
}

/** Host-path executor: java.awt Graphics2D + GradientPaint. */
class AwtExecutor : WallpaperExecutor<BufferedImage> {

    override fun execute(plan: RenderPlan, source: SourceImage): BufferedImage {
        val src = (source as? AwtSourceImage
            ?: error("AwtExecutor requires an AwtSourceImage source")).image
        val out = BufferedImage(plan.targetSize.width, plan.targetSize.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)
        g.drawImage(
            src,
            plan.dstRect.left, plan.dstRect.top, plan.dstRect.right, plan.dstRect.bottom,
            plan.srcRect.left, plan.srcRect.top, plan.srcRect.right, plan.srcRect.bottom,
            null,
        )

        val scrimEndX = plan.scrimRect.right.toFloat()
        val start = Color(0f, 0f, 0f, plan.scrimStartAlpha)
        val end = Color(0f, 0f, 0f, 0f)
        g.paint = GradientPaint(0f, 0f, start, scrimEndX, 0f, end)
        g.fillRect(0, 0, plan.scrimRect.right, plan.scrimRect.bottom)
        g.dispose()
        return out
    }
}
