package ca.hld.covertart.device

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap

/** Sets a bitmap as both the home and lock screen wallpaper in one call. */
class WallpaperApplier(context: Context) {

    private val wallpaperManager = WallpaperManager.getInstance(context)

    /** @throws java.io.IOException if the system rejects the bitmap. */
    fun apply(bitmap: Bitmap) {
        wallpaperManager.setBitmap(
            bitmap,
            /* visibleCropHint = */ null,
            /* allowBackup = */ true,
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
        )
    }
}
