package com.crowtheatron.app.ui

import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Edge-to-edge with system bar insets applied to [contentRoot]. Theme uses black status/navigation
 * bars with light (white) icons; [isAppearanceLightStatusBars] stays false so icons remain light.
 */
fun AppCompatActivity.setContentWithCrowInsets(contentRoot: View) {
    enableEdgeToEdge()
    setContentView(contentRoot)
    WindowCompat.getInsetsController(window, contentRoot).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
    ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
