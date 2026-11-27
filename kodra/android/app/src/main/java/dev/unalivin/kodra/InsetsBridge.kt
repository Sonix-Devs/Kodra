package dev.unalivin.kodra

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Edge-to-edge + immersive + IME handling.
 *
 * Owner decision: immersive by default with transient-bars-by-swipe, edge-to-edge,
 * and an in-app setting to turn it off. Foreground notification also has Stop action.
 *
 * This bridge:
 * - Sets `decorFitsSystemWindows=false` so WebView draws behind bars.
 * - Uses `WindowInsetsControllerCompat` transientBarsBySwipe.
 * - Handles display cutout via `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`.
 */
object InsetsBridge {
    fun apply(activity: Activity, webView: View, immersive: Boolean = true) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val controller = WindowInsetsControllerCompat(activity.window, webView)
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = true
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }

        webView.setOnApplyWindowInsetsListener { _, insets ->
            WindowInsetsCompat.toWindowInsetsCompat(insets)
                .isVisible(WindowInsetsCompat.Type.ime())
            insets
        }
    }

    fun setImmersive(activity: Activity, webView: View, enabled: Boolean) {
        apply(activity, webView, enabled)
    }
}
