package com.crowtheatron.app.library

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.crowtheatron.app.R
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.databinding.ActivityLibraryBinding
import com.crowtheatron.app.player.PlayerActivity
import com.crowtheatron.app.ui.BottomNavHelper
import com.crowtheatron.app.ui.LibraryAdapter
import com.crowtheatron.app.ui.setContentWithCrowInsets
import com.crowtheatron.app.ui.buildGroupedItems

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val repo by lazy { VideoRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ALL
        val navSelected = if (mode == MODE_FAVORITES) R.id.nav_favorites else R.id.nav_library
        binding.toolbar.title = getString(
            if (mode == MODE_FAVORITES) R.string.favorites_title else R.string.library_title
        )
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

        val videos = if (mode == MODE_FAVORITES) repo.listFavorites() else repo.listAllByFolder()
        val items = buildGroupedItems(videos)
        adapter.submitList(items)
        binding.empty.visibility =
            if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.recycler.visibility =
            if (items.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_ALL = "all"
        const val MODE_FAVORITES = "favorites"
    }
}
