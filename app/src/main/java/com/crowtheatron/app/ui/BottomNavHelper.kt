package com.crowtheatron.app.ui

import android.app.Activity
import android.content.Intent
import com.crowtheatron.app.R
import com.crowtheatron.app.explore.ExploreActivity
import com.crowtheatron.app.folder.FolderSelectActivity
import com.crowtheatron.app.library.LibraryActivity
import com.crowtheatron.app.main.MainActivity
import com.crowtheatron.app.memory.PlaybackMemoryActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

object BottomNavHelper {

    fun setup(activity: Activity, bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNav.selectedItemId = selectedItemId
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) {
                return@setOnItemSelectedListener true
            }
            when (item.itemId) {
                R.id.nav_home -> {
                    activity.startActivity(
                        Intent(activity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    activity.finish()
                }
                R.id.nav_library -> {
                    activity.startActivity(
                        Intent(activity, LibraryActivity::class.java)
                            .putExtra(LibraryActivity.EXTRA_MODE, LibraryActivity.MODE_ALL)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                    activity.finish()
                }
                R.id.nav_favorites -> {
                    activity.startActivity(
                        Intent(activity, LibraryActivity::class.java)
                            .putExtra(LibraryActivity.EXTRA_MODE, LibraryActivity.MODE_FAVORITES)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                    activity.finish()
                }
                R.id.nav_memory -> {
                    activity.startActivity(
                        Intent(activity, PlaybackMemoryActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                    activity.finish()
                }
                R.id.nav_explore -> {
                    activity.startActivity(
                        Intent(activity, ExploreActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                    activity.finish()
                }
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    fun openFolderSelect(activity: Activity) {
        activity.startActivity(Intent(activity, FolderSelectActivity::class.java))
    }
}
