package com.crowtheatron.app.data

import android.content.Context
import com.crowtheatron.app.util.ThumbnailExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(context: Context) {
    private val app = context.applicationContext
    private val db = CrowDbHelper(app)
    private val prefs = AppPrefs(app)

    suspend fun importScanResults(entities: List<VideoEntity>, withThumbnails: Boolean = true) = withContext(Dispatchers.IO) {
        for (e in entities) {
            val existingId = db.getIdByUri(e.uriString)
            val prepared = if (existingId == null) {
                e.copy(
                    seekJumpSec = prefs.defaultSeekJumpSec,
                    enhancement = prefs.defaultEnhancement,
                )
            } else e
            val id = db.insertOrMergeFromScan(prepared)
            if (withThumbnails && id > 0) {
                val existing = db.getById(id)
                if (existing?.thumbnail == null) {
                    val jpeg = ThumbnailExtractor.extractJpeg(app, e.contentUri)
                    if (jpeg != null) db.updateThumbnail(id, jpeg)
                }
            }
        }
    }

    fun getById(id: Long): VideoEntity? = db.getById(id)

    fun listAllByFolder(): List<VideoEntity> = db.listAllOrderedByFolder()

    fun listFavorites(): List<VideoEntity> = db.listFavorites()

    fun listPlaybackMemory(): List<VideoEntity> = db.listPlaybackMemory()

    fun search(query: String): List<VideoEntity> = db.searchByTitle(query)

    fun savePlaybackPosition(id: Long, positionMs: Long) {
        db.updatePlaybackState(id, positionMs)
    }

    fun savePreferences(entity: VideoEntity) {
        db.updatePreferences(entity)
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        db.setFavorite(id, favorite)
    }
}
