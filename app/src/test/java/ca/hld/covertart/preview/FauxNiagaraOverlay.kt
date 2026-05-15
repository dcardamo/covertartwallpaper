package ca.hld.covertart.preview

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Composites a left-pinned clock + app names onto a rendered wallpaper,
 * approximating the Niagara launcher so previews show text in context.
 * Mutates the given image in place.
 */
object FauxNiagaraOverlay {

    fun compositeOnto(wallpaper: BufferedImage) {
        val g = wallpaper.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        val pad = 48

        g.font = Font(Font.SANS_SERIF, Font.BOLD, 120)
        g.drawString("9:41", pad, 220)

        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 44)
        val apps = listOf("Phone", "Messages", "Tidal", "Spotify", "Settings", "Camera")
        apps.forEachIndexed { i, name -> g.drawString(name, pad, 360 + i * 70) }

        g.dispose()
    }
}
