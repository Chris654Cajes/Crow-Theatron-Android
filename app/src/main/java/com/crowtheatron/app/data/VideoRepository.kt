package com.crowtheatron.app.data

import android.content.Context
import com.crowtheatron.app.util.ThumbnailExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(context: Context) {
    private val app = context.applicationContext
    private val db  = CrowDbHelper(app)
    private val prefs = AppPrefs(app)

    // ── Video scanning ────────────────────────────────────────────────────────

    suspend fun importScanResults(entities: List<VideoEntity>, withThumbnails: Boolean = true) =
        withContext(Dispatchers.IO) {
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

    // ── Video queries ─────────────────────────────────────────────────────────

    fun getById(id: Long): VideoEntity?               = db.getById(id)
    fun listAllByFolder(): List<VideoEntity>          = db.listAllOrderedByFolder()
    fun listFavorites(): List<VideoEntity>            = db.listFavorites()
    fun listPlaybackMemory(): List<VideoEntity>       = db.listPlaybackMemory()
    fun listContinueWatching(): List<VideoEntity>     = db.listContinueWatching()
    fun listRecentlyPlayed(): List<VideoEntity>       = db.listRecentlyPlayed()
    fun search(query: String): List<VideoEntity>      = db.searchByTitle(query)

    fun savePlaybackPosition(id: Long, positionMs: Long) =
        db.updatePlaybackState(id, positionMs)

    fun savePreferences(entity: VideoEntity) = db.updatePreferences(entity)
    fun setFavorite(id: Long, favorite: Boolean) = db.setFavorite(id, favorite)

    // ── Playback Profiles ─────────────────────────────────────────────────────

    fun createProfile(profile: PlaybackProfile): Long = db.insertProfile(profile)

    fun updateProfile(profile: PlaybackProfile) = db.updateProfile(profile)

    fun deleteProfile(id: Long) = db.deleteProfile(id)

    fun getProfile(id: Long): PlaybackProfile? = db.getProfile(id)

    fun listProfilesForVideo(videoId: Long): List<PlaybackProfile> =
        db.listProfilesForVideo(videoId)

    fun getDefaultProfile(videoId: Long): PlaybackProfile? = db.getDefaultProfile(videoId)

    /**
     * Duplicate an existing profile with a new name.
     * Returns the id of the new profile.
     */
    fun duplicateProfile(profileId: Long, newName: String): Long? {
        val src = db.getProfile(profileId) ?: return null
        return db.insertProfile(src.copy(id = 0L, name = newName, isDefault = false,
            createdAt = System.currentTimeMillis()))
    }

    /**
     * Apply a PlaybackProfile onto a VideoEntity, returning the updated copy.
     * Use this when switching profiles to instantly load all saved settings.
     */
    fun applyProfileToEntity(entity: VideoEntity, profile: PlaybackProfile): VideoEntity =
        entity.copy(
            playbackSpeed          = profile.playbackSpeed,
            volumeLevel            = profile.volumeLevel,
            audioBoost             = profile.audioBoost,
            eqPreset               = profile.eqPreset,
            loopPlayback           = profile.loopPlayback,
            autoPlayNext           = profile.autoPlayNext,
            pitchSemitones         = profile.pitchSemitones,
            trimStartMs            = profile.trimStartMs,
            trimEndMs              = profile.trimEndMs,
            enhancement            = profile.enhancement,
            brightness             = profile.brightness,
            contrast               = profile.contrast,
            saturation             = profile.saturation,
            hue                    = profile.hue,
            sharpness              = profile.sharpness,
            zoomLevel              = profile.zoomLevel,
            cropMode               = profile.cropMode,
            subtitleTrackIndex     = profile.subtitleTrackIndex,
            subtitleOffsetMs       = profile.subtitleOffsetMs,
            subtitleSizeSp         = profile.subtitleSizeSp,
            subtitleBold           = profile.subtitleBold,
            subtitleBackgroundAlpha = profile.subtitleBackgroundAlpha,
            activeProfileId        = profile.id,
        )

    // ── Chapter Markers ───────────────────────────────────────────────────────

    fun addChapter(videoId: Long, positionMs: Long, label: String, auto: Boolean = false): Long =
        db.insertChapter(ChapterMarker(
            videoId = videoId, positionMs = positionMs, label = label,
            isAutoDetected = auto, createdAt = System.currentTimeMillis()
        ))

    fun updateChapter(chapter: ChapterMarker) = db.updateChapter(chapter)
    fun deleteChapter(id: Long) = db.deleteChapter(id)
    fun deleteAllChapters(videoId: Long) = db.deleteChaptersForVideo(videoId)
    fun listChapters(videoId: Long): List<ChapterMarker> = db.listChaptersForVideo(videoId)
}
