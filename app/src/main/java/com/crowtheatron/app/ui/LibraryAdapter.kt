package com.crowtheatron.app.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crowtheatron.app.databinding.ItemLibraryHeaderBinding
import com.crowtheatron.app.databinding.ItemVideoCardBinding
import com.crowtheatron.app.util.FormatUtils

class LibraryAdapter(
    private val onOpen: (videoId: Long, playlistIds: LongArray, indexInPlaylist: Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<LibraryListItem>()
    private var playlistCache = LongArray(0)

    fun submitList(newItems: List<LibraryListItem>) {
        items.clear()
        items.addAll(newItems)
        playlistCache = playlistIdsInOrder(items)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is LibraryListItem.Header -> VT_HEADER
        is LibraryListItem.VideoRow -> VT_VIDEO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VT_HEADER -> HeaderVH(ItemLibraryHeaderBinding.inflate(inflater, parent, false))
            else -> VideoVH(ItemVideoCardBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LibraryListItem.Header -> (holder as HeaderVH).bind(item.folder)
            is LibraryListItem.VideoRow -> (holder as VideoVH).bind(item)
        }
    }

    fun spanSizeLookup(): GridLayoutManager.SpanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return if (getItemViewType(position) == VT_HEADER) 2 else 1
        }
    }

    inner class HeaderVH(private val b: ItemLibraryHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(folder: String) {
            b.headerTitle.text = folder
        }
    }

    inner class VideoVH(private val b: ItemVideoCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: LibraryListItem.VideoRow) {
            val e = row.entity
            b.videoTitle.text = e.title
            val meta = buildString {
                append(FormatUtils.formatDuration(e.durationMs))
                append(" · ")
                append(FormatUtils.formatSize(e.sizeBytes))
                if (e.favorite) append(" · ★")
            }
            b.videoMeta.text = meta
            val thumb = e.thumbnail
            if (thumb != null) {
                val bmp = BitmapFactory.decodeByteArray(thumb, 0, thumb.size)
                if (bmp != null) {
                    b.thumbnail.background = null
                    b.thumbnail.setImageBitmap(bmp)
                } else {
                    b.thumbnail.setImageDrawable(null)
                    b.thumbnail.setBackgroundColor(Color.DKGRAY)
                }
            } else {
                b.thumbnail.setImageDrawable(null)
                b.thumbnail.setBackgroundColor(Color.DKGRAY)
            }
            b.root.setOnClickListener {
                val idx = playlistCache.indexOfFirst { it == e.id }
                if (idx >= 0) onOpen(e.id, playlistCache, idx)
            }
        }
    }

    companion object {
        private const val VT_HEADER = 1
        private const val VT_VIDEO = 2
    }
}
