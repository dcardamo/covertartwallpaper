package ca.hld.covertart.preview

import java.awt.Color
import java.awt.Graphics2D
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.util.Random

/**
 * Procedurally generated, license-clean sample covers chosen to stress
 * left-edge text legibility: dark, bright, pale, busy, high-contrast-edge.
 * Deterministic — these double as render-pipeline test fixtures.
 */
object SampleCovers {

    private const val SIZE = 1000

    val all: Map<String, BufferedImage> by lazy {
        mapOf(
            "dark" to gradient(Color(18, 18, 24), Color(40, 30, 60)),
            "bright" to gradient(Color(255, 220, 80), Color(255, 140, 0)),
            "pale" to gradient(Color(245, 245, 240), Color(220, 225, 235)),
            "busy" to busy(),
            "contrast-edge" to contrastEdge(),
        )
    }

    private fun newCanvas(): Pair<BufferedImage, Graphics2D> {
        val img = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        return img to img.createGraphics()
    }

    private fun gradient(a: Color, b: Color): BufferedImage {
        val (img, g) = newCanvas()
        g.paint = GradientPaint(0f, 0f, a, SIZE.toFloat(), SIZE.toFloat(), b)
        g.fillRect(0, 0, SIZE, SIZE)
        g.dispose()
        return img
    }

    private fun busy(): BufferedImage {
        val (img, g) = newCanvas()
        val rnd = Random(42)
        repeat(400) {
            g.color = Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            g.fillRect(rnd.nextInt(SIZE), rnd.nextInt(SIZE), 30 + rnd.nextInt(120), 30 + rnd.nextInt(120))
        }
        g.dispose()
        return img
    }

    private fun contrastEdge(): BufferedImage {
        val (img, g) = newCanvas()
        g.color = Color.WHITE
        g.fillRect(0, 0, SIZE, SIZE)
        g.color = Color.BLACK
        // Hard dark band exactly where Niagara's text sits.
        g.fillRect(0, 0, SIZE / 3, SIZE)
        g.dispose()
        return img
    }
}
