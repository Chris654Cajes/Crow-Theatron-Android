package com.crowtheatron.app.library

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.crowtheatron.app.R
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.databinding.ActivityLibraryBinding
import com.crowtheatron.app.player.PlayerActivity
import com.crowtheatron.app.ui.BottomNavHelper
import com.crowtheatron.app.ui.LibraryAdapter
import com.crowtheatron.app.ui.LibraryListItem
import com.crowtheatron.app.ui.buildGroupedItems
import com.crowtheatron.app.ui.setContentWithCrowInsets

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val repo by lazy { VideoRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ALL
        val navSelected = when (mode) {
            MODE_FAVORITES        -> R.id.nav_favorites
            MODE_CONTINUE         -> R.id.nav_library
            MODE_RECENTLY_PLAYED  -> R.id.nav_memory
            else                  -> R.id.nav_library
        }
        binding.toolbar.title = getString(when (mode) {
            MODE_FAVORITES       -> R.string.favorites_title
            MODE_CONTINUE        -> R.string.section_continue_watching
            MODE_RECENTLY_PLAYED -> R.string.memory_title
            else                 -> R.string.library_title
        })
        BottomNavHelper.setup(this, binding.bottomNav, navSelected)

        val adapter = LibraryAdapter { videoId, playlistIds, index ->
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_VIDEO_ID, videoId)
                    .putExtra(PlayerActivity.EXTRA_PLAYLIST_IDS, playlistIds)
                    .putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, index)
            )
        }
        val glm = GridLayoutManager(this, 2)
        glm.spanSizeLookup = adapter.spanSizeLookup()
        binding.recycler.layoutManager = glm
        binding.recycler.adapter = adapter

        val videos = when (mode) {
            MODE_FAVORITES       -> repo.listFavorites()
            MODE_CONTINUE        -> repo.listContinueWatching()
            MODE_RECENTLY_PLAYED -> repo.listRecentlyPlayed()
            else                 -> repo.listAllByFolder()
        }

        val items: List<LibraryListItem> = when (mode) {
            MODE_ALL -> buildGroupedItems(videos)
            else     -> {
                // Flat list with single section header
                if (videos.isEmpty()) emptyList()
                else buildList {
                    add(LibraryListItem.Header(binding.toolbar.title?.toString() ?: ""))
                    addAll(videos.map { LibraryListItem.VideoRow(it) })
                }
            }
        }

        adapter.submitList(items)
        binding.empty.visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.recycler.visibility =
            if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    companion object {
        const val EXTRA_MODE         = "mode"
        const val MODE_ALL           = "all"
        const val MODE_FAVORITES     = "favorites"
        const val MODE_CONTINUE      = "continue_watching"
        const val MODE_RECENTLY_PLAYED = "recently_played"
    }
}
