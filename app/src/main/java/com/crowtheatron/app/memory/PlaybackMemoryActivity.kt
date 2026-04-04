package com.crowtheatron.app.memory

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.crowtheatron.app.R
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.databinding.ActivityPlaybackMemoryBinding
import com.crowtheatron.app.player.PlayerActivity
import com.crowtheatron.app.ui.BottomNavHelper
import com.crowtheatron.app.ui.LibraryAdapter
import com.crowtheatron.app.ui.LibraryListItem
import com.crowtheatron.app.ui.setContentWithCrowInsets

class PlaybackMemoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaybackMemoryBinding
    private val repo by lazy { VideoRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackMemoryBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        BottomNavHelper.setup(this, binding.bottomNav, R.id.nav_memory)

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

        val videos = repo.listPlaybackMemory()
        val flat = videos.map { LibraryListItem.VideoRow(it) }
        adapter.submitList(flat)
        binding.empty.visibility =
            if (flat.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.recycler.visibility =
            if (flat.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }
}
