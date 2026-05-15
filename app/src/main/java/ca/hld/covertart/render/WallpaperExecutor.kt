package ca.hld.covertart.render

import ca.hld.covertart.core.SourceImage

/**
 * Paints a [RenderPlan] into a platform bitmap of type [T]. Implementations are
 * deliberately thin and downcast [SourceImage] to their matching concrete type.
 */
interface WallpaperExecutor<T> {
    fun execute(plan: RenderPlan, source: SourceImage): T
}
