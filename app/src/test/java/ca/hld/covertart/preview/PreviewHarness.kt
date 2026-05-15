package ca.hld.covertart.preview

import ca.hld.covertart.render.AwtExecutor
import ca.hld.covertart.render.AwtSourceImage
import ca.hld.covertart.render.RenderConfig
import ca.hld.covertart.render.WallpaperRenderer
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders every sample cover through the real pipeline + AWT executor,
 * composites the faux-Niagara overlay, and writes one PNG per cover.
 */
object PreviewHarness {

    const val TARGET_WIDTH = 1080
    const val TARGET_HEIGHT = 1920

    fun run(outputDir: File, config: RenderConfig = RenderConfig()): List<File> {
        outputDir.mkdirs()
        val renderer = WallpaperRenderer(AwtExecutor(), config)
        return SampleCovers.all.map { (name, cover) ->
            val wallpaper = renderer.render(AwtSourceImage(cover), TARGET_WIDTH, TARGET_HEIGHT)
            FauxNiagaraOverlay.compositeOnto(wallpaper)
            val out = File(outputDir, "$name.png")
            ImageIO.write(wallpaper, "png", out)
            out
        }
    }
}
