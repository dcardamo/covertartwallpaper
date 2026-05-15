package ca.hld.covertart.render

import ca.hld.covertart.core.SourceImage

/** Glues the pure planner to a platform executor. */
class WallpaperRenderer<T>(
    private val executor: WallpaperExecutor<T>,
    private val config: RenderConfig = RenderConfig(),
) {
    /**
     * @return A freshly allocated bitmap owned by the caller; the caller must recycle it when done.
     */
    fun render(source: SourceImage, targetWidth: Int, targetHeight: Int): T {
        val plan = planRender(source.width, source.height, targetWidth, targetHeight, config)
        return executor.execute(plan, source)
    }
}
