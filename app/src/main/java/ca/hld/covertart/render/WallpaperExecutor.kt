package ca.hld.covertart.render

import ca.hld.covertart.core.SourceImage

/**
 * Paints a [RenderPlan] into a platform bitmap of type [T]. Implementations are
 * deliberately thin and downcast [SourceImage] to their matching concrete type.
 *
 * @return A freshly allocated bitmap owned by the caller; the caller must recycle it when done.
 */
interface WallpaperExecutor<T> {
    fun execute(plan: RenderPlan, source: SourceImage): T
}
