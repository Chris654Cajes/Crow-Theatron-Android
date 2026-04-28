package com.crowtheatron.app.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.crowtheatron.app.R
import com.crowtheatron.app.data.CrowDbHelper
import com.crowtheatron.app.databinding.ActivityMainBinding
import com.crowtheatron.app.enhancement.VideoEnhancementActivity
import com.crowtheatron.app.library.LibraryActivity
import com.crowtheatron.app.memory.PlaybackMemoryActivity
import com.crowtheatron.app.settings.SettingsActivity
import com.crowtheatron.app.ui.BottomNavHelper
import com.crowtheatron.app.ui.setContentWithCrowInsets

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        BottomNavHelper.setup(this, binding.bottomNav, R.id.nav_home)

        binding.bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.nav_home) {
                BottomNavHelper.openFolderSelect(this)
            }
        }

        binding.btnPickFolder.setOnClickListener { BottomNavHelper.openFolderSelect(this) }

        binding.btnOpenLibrary.setOnClickListener {
            startActivity(
                Intent(this, LibraryActivity::class.java)
                    .putExtra(LibraryActivity.EXTRA_MODE, LibraryActivity.MODE_ALL)
            )
        }

        binding.btnResetLibrary.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset library?")
                .setMessage("This will delete all videos from the library, including playback history and preferences. You will be asked to select a folder again. This cannot be undone.")
                .setPositiveButton("Reset") { _, _ ->
                    val db = CrowDbHelper(this)
                    db.writableDatabase.execSQL("DELETE FROM ${CrowDbHelper.TABLE_VIDEOS}")
                    db.close()
                    BottomNavHelper.openFolderSelect(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnPlaybackMemory.setOnClickListener {
            startActivity(Intent(this, PlaybackMemoryActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnEnhancement.setOnClickListener {
            startActivity(Intent(this, VideoEnhancementActivity::class.java))
        }
    }
}