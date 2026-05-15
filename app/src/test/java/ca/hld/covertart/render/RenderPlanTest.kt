package ca.hld.covertart.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RenderPlanTest {

    @Test
    fun squareArtOnPortraitScreenIsRightAnchored() {
        val plan = planRender(srcWidth = 1000, srcHeight = 1000, targetWidth = 1080, targetHeight = 1920)
        // height fills: scale = 1920/1000 = 1.92 -> scaledWidth = 1920
        assertEquals(IntRect(-840, 0, 1080, 1920), plan.dstRect)
        assertEquals(IntRect(0, 0, 1000, 1000), plan.srcRect)
        // targetSize is the full target canvas: (0, 0, targetWidth, targetHeight)
        assertEquals(IntRect(0, 0, 1080, 1920), plan.targetSize)
    }

    @Test
    fun nonSquareArtScalesByHeight() {
        val plan = planRender(srcWidth = 600, srcHeight = 800, targetWidth = 1080, targetHeight = 1920)
        // scale = 1920/800 = 2.4 -> scaledWidth = 1440 -> left = 1080 - 1440 = -360
        assertEquals(IntRect(-360, 0, 1080, 1920), plan.dstRect)
    }

    @Test
    fun scrimSpansConfiguredFractionOfWidth() {
        val plan = planRender(1000, 1000, 1080, 1920, RenderConfig(scrimEndFraction = 0.35f))
        // round(1080 * 0.35) = 378
        assertEquals(IntRect(0, 0, 378, 1920), plan.scrimRect)
        assertEquals(0.75f, plan.scrimStartAlpha, 0.0001f)
        assertEquals(0.35f, plan.scrimEndFraction, 0.0001f)
    }

    @Test
    fun rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException::class.java) { planRender(0, 100, 100, 100) }
        assertThrows(IllegalArgumentException::class.java) { planRender(100, 100, 100, 0) }
    }
}
