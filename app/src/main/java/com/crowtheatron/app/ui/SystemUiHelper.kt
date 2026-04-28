package com.crowtheatron.app.ui

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.crowtheatron.app.R

/**
 * Applies system bar insets while keeping the navigation bar opaque.
 * Screens with a BottomNavigationView get bottom inset on the nav itself;
 * other screens get bottom inset on the root content so controls stay above
 * the system navigation bar.
 */
fun AppCompatActivity.setContentWithCrowInsets(contentRoot: View) {
    setContentView(contentRoot)

    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.navigationBarColor = Color.BLACK

    contentRoot.postDelayed({
        window.navigationBarColor = Color.BLACK
        window.navigationBarColor = 0xFF000000.toInt()
    }, 100)

    ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val bottomNav = (view as? ViewGroup)?.findViewById<View>(R.id.bottomNav)
        view.updatePadding(
            left = bars.left,
            top = bars.top,
            right = bars.right,
            bottom = if (bottomNav == null) bars.bottom else 0,
        )
        bottomNav?.updatePadding(bottom = bars.bottom)
        window.navigationBarColor = getColor(R.color.crow_pure_black)
        insets
    }

    ViewCompat.requestApplyInsets(contentRoot)
    contentRoot.post {
        window.navigationBarColor = getColor(R.color.crow_pure_black)
    }
}
