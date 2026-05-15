package ca.hld.covertart.device

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/** Real display size in pixels as (width, height) — the wallpaper target size. */
fun screenSize(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        bounds.width() to bounds.height()
    } else {
        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
        metrics.widthPixels to metrics.heightPixels
    }
}
