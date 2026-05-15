package ca.hld.covertart.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import ca.hld.covertart.core.SourceImage
import kotlin.math.roundToInt

/** A SourceImage backed by an android.graphics.Bitmap (device path). */
class AndroidSourceImage(val bitmap: Bitmap) : SourceImage {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
}

/** Device-path executor: android.graphics.Canvas + LinearGradient. */
class AndroidCanvasExecutor : WallpaperExecutor<Bitmap> {

    override fun execute(plan: RenderPlan, source: SourceImage): Bitmap {
        val src = (source as? AndroidSourceImage
            ?: error("AndroidCanvasExecutor requires an AndroidSourceImage source")).bitmap
        val out = Bitmap.createBitmap(
            plan.targetSize.width,
            plan.targetSize.height,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(out)

        val srcRect = Rect(plan.srcRect.left, plan.srcRect.top, plan.srcRect.right, plan.srcRect.bottom)
        val dstRect = Rect(plan.dstRect.left, plan.dstRect.top, plan.dstRect.right, plan.dstRect.bottom)
        canvas.drawBitmap(src, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

        val scrimEndX = plan.scrimRect.right.toFloat()
        val startColor = Color.argb((plan.scrimStartAlpha * 255f).roundToInt(), 0, 0, 0)
        val endColor = Color.argb(0, 0, 0, 0)
        val scrimPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, scrimEndX, 0f, startColor, endColor, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, scrimEndX, plan.scrimRect.bottom.toFloat(), scrimPaint)
        return out
    }
}
