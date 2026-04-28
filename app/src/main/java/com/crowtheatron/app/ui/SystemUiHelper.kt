package com.crowtheatron.app.ui

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.crowtheatron.app.R
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * System bar insets applied precisely with opaque navigation bar:
 *  - Top inset → toolbar / content root top padding
 *  - Left/right insets → content root sides
 *  - Bottom inset → BottomNavigationView only (id=bottomNav), NOT the whole root,
 *    so the nav bar height is NOT added as extra margin below the nav bar.
 *  - Navigation bar remains opaque (black) as defined in theme
 */
fun AppCompatActivity.setContentWithCrowInsets(contentRoot: View) {
    // Don't use enableEdgeToEdge() as it makes navigation bar transparent
    // Instead, set up window manually for opaque navigation bar
    setContentView(contentRoot)
    
    // Set navigation bar to completely opaque black - aggressive approach
    // Ensure the window draws the background
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
// Clear translucent flag if set previously
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
// Set to solid black
    window.navigationBarColor = Color.BLACK
// Alternatively, for explicit opacity: window.navigationBarColor = 0xFF000000.toInt()



    // Force navigation bar color again after a delay to ensure it sticks
    contentRoot.postDelayed({
        window.navigationBarColor = Color.BLACK
        window.navigationBarColor = 0xFF000000.toInt()
    }, 100)
    
    ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        // Apply top/left/right to root; bottom goes to the BottomNav only
        view.updatePadding(bars.left, bars.top, bars.right, 0)

        // Find BottomNavigationView and apply only the bottom inset there
        val bottomNav = (view as? ViewGroup)?.findViewById<View>(com.crowtheatron.app.R.id.bottomNav)
        bottomNav?.updatePadding(bottom = bars.bottom)

        // Force navigation bar color again after insets are applied
        window.navigationBarColor = getColor(R.color.crow_pure_black)

        insets
    }
    
    // Additional enforcement after layout is complete
    contentRoot.post {
        window.navigationBarColor = getColor(R.color.crow_pure_black)
    }
}