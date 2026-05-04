package com.crowtheatron.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CrowDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_VIDEOS)
        db.execSQL("CREATE INDEX idx_videos_folder   ON $TABLE_VIDEOS($COL_FOLDER)")
        db.execSQL("CREATE INDEX idx_videos_favorite ON $TABLE_VIDEOS($COL_FAVORITE)")
        db.execSQL("CREATE INDEX idx_videos_played   ON $TABLE_VIDEOS($COL_LAST_PLAYED)")
        db.execSQL(CREATE_PROFILES)
        db.execSQL("CREATE INDEX idx_profiles_video  ON $TABLE_PROFILES($PRO_VIDEO_ID)")
        db.execSQL(CREATE_CHAPTERS)
        db.execSQL("CREATE INDEX idx_chapters_video  ON $TABLE_CHAPTERS($CHA_VIDEO_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_SPEED REAL NOT NULL DEFAULT 1.0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_VOLUME REAL NOT NULL DEFAULT 1.0") }
        }
        if (oldVersion < 3) {
            // Extended enhancement sliders
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_BRIGHTNESS REAL NOT NULL DEFAULT 0.0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_CONTRAST REAL NOT NULL DEFAULT 1.0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_SATURATION REAL NOT NULL DEFAULT 1.0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_HUE REAL NOT NULL DEFAULT 0.0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_SHARPNESS REAL NOT NULL DEFAULT 0.0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_ZOOM REAL NOT NULL DEFAULT 1.0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_CROP TEXT NOT NULL DEFAULT 'FIT'") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_AUDIO_BOOST REAL NOT NULL DEFAULT 1.0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_EQ_PRESET TEXT NOT NULL DEFAULT 'FLAT'") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_SUB_TRACK INTEGER NOT NULL DEFAULT -1") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_SUB_OFFSET INTEGER NOT NULL DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_SUB_SIZE REAL NOT NULL DEFAULT 16.0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_SUB_BOLD INTEGER NOT NULL DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_SUB_BG_ALPHA INTEGER NOT NULL DEFAULT 128") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_ORIENTATION INTEGER NOT NULL DEFAULT -1") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_ACTIVE_PROFILE INTEGER NOT NULL DEFAULT 0") }
            // New tables
            runCatching { db.execSQL(CREATE_PROFILES) }
            runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_profiles_video ON $TABLE_PROFILES($PRO_VIDEO_ID)") }
            runCatching { db.execSQL(CREATE_CHAPTERS) }
            runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_chapters_video ON $TABLE_CHAPTERS($CHA_VIDEO_ID)") }
        }
    }

    // ── Videos ────────────────────────────────────────────────────────────────

    fun insertOrMergeFromScan(entity: VideoEntity): Long {
        val db = writableDatabase
        val existingId = getIdByUri(entity.uriString)
        if (existingId == null) {
            return db.insert(TABLE_VIDEOS, null, entity.toContentValues())
        }
        val cv = ContentValues().apply {
            put(COL_TITLE, entity.title)
            put(COL_FOLDER, entity.folderGroup)
            put(COL_DURATION_MS, entity.durationMs)
            put(COL_SIZE_BYTES, entity.sizeBytes)
        }
        db.update(TABLE_VIDEOS, cv, "$COL_ID = ?", arrayOf(existingId.toString()))
        return existingId
    }

    fun getIdByUri(uriString: String): Long? {
        readableDatabase.query(
            TABLE_VIDEOS, arrayOf(COL_ID), "$COL_URI = ?", arrayOf(uriString),
            null, null, null
        ).use { c -> if (c.moveToFirst()) return c.getLong(0) }
        return null
    }

    fun updateThumbnail(id: Long, bytes: ByteArray?) {
        val cv = ContentValues().apply { put(COL_THUMB, bytes) }
        writableDatabase.update(TABLE_VIDEOS, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun updatePlaybackState(id: Long, positionMs: Long, lastPlayedAt: Long = System.currentTimeMillis()) {
        val cv = ContentValues().apply {
            put(COL_POSITION_MS, positionMs)
            put(COL_LAST_PLAYED, lastPlayedAt)
        }
        writableDatabase.update(TABLE_VIDEOS, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun updatePreferences(entity: VideoEntity) {
        val cv = ContentValues().apply {
            put(COL_PITCH_SEMITONES, entity.pitchSemitones)
            put(COL_TRIM_START_MS, entity.trimStartMs)
            put(COL_TRIM_END_MS, entity.trimEndMs)
            put(COL_FAVORITE, if (entity.favorite) 1 else 0)
            put(COL_SEEK_JUMP_SEC, entity.seekJumpSec)
            put(COL_AUTO_NEXT, if (entity.autoPlayNext) 1 else 0)
            put(COL_LOOP, if (entity.loopPlayback) 1 else 0)
            put(COL_ENHANCEMENT, entity.enhancement.storageKey)
            put(COL_SPEED, entity.playbackSpeed)
            put(COL_VOLUME, entity.volumeLevel)
            put(COL_BRIGHTNESS, entity.brightness)
            put(COL_CONTRAST, entity.contrast)
            put(COL_SATURATION, entity.saturation)
            put(COL_HUE, entity.hue)
            put(COL_SHARPNESS, entity.sharpness)
            put(COL_ZOOM, entity.zoomLevel)
            put(COL_CROP, entity.cropMode.storageKey)
            put(COL_AUDIO_BOOST, entity.audioBoost)
            put(COL_EQ_PRESET, entity.eqPreset.storageKey)
            put(COL_SUB_TRACK, entity.subtitleTrackIndex)
            put(COL_SUB_OFFSET, entity.subtitleOffsetMs)
            put(COL_SUB_SIZE, entity.subtitleSizeSp)
            put(COL_SUB_BOLD, if (entity.subtitleBold) 1 else 0)
            put(COL_SUB_BG_ALPHA, entity.subtitleBackgroundAlpha)
            put(COL_ORIENTATION, entity.preferredOrientation)
            put(COL_ACTIVE_PROFILE, entity.activeProfileId)
        }
        writableDatabase.update(TABLE_VIDEOS, cv, "$COL_ID = ?", arrayOf(entity.id.toString()))
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        val cv = ContentValues().apply { put(COL_FAVORITE, if (favorite) 1 else 0) }
        writableDatabase.update(TABLE_VIDEOS, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getById(id: Long): VideoEntity? {
        readableDatabase.query(
            TABLE_VIDEOS, null, "$COL_ID = ?", arrayOf(id.toString()),
            null, null, null
        ).use { c -> if (c.moveToFirst()) return c.toEntity() }
        return null
    }

    fun listAllOrderedByFolder(): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        readableDatabase.query(
            TABLE_VIDEOS, null, null, null, null, null,
            "$COL_FOLDER COLLATE NOCASE ASC, $COL_TITLE COLLATE NOCASE ASC"
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

    fun listFavorites(): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        readableDatabase.query(
            TABLE_VIDEOS, null, "$COL_FAVORITE = 1", null, null, null, "$COL_LAST_PLAYED DESC"
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

    fun listContinueWatching(limit: Int = 50): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        // Videos with saved position > 5 seconds and < 95% complete
        readableDatabase.rawQuery(
            """SELECT * FROM $TABLE_VIDEOS 
               WHERE $COL_POSITION_MS > 5000 
               AND $COL_DURATION_MS > 0 
               AND CAST($COL_POSITION_MS AS REAL) / $COL_DURATION_MS < 0.95
               ORDER BY $COL_LAST_PLAYED DESC 
               LIMIT ?""",
            arrayOf(limit.toString())
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

    fun listRecentlyPlayed(limit: Int = 100): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        readableDatabase.query(
            TABLE_VIDEOS, null, "$COL_LAST_PLAYED > 0", null, null, null,
            "$COL_LAST_PLAYED DESC", limit.toString()
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

    fun listPlaybackMemory(limit: Int = 200): List<VideoEntity> = listRecentlyPlayed(limit)

    fun searchByTitle(query: String): List<VideoEntity> {
        if (query.isBlank()) return emptyList()
        val list = mutableListOf<VideoEntity>()
        readableDatabase.query(
            TABLE_VIDEOS, null, "$COL_TITLE LIKE ? COLLATE NOCASE", arrayOf("%${query.trim()}%"),
            null, null, "$COL_FOLDER COLLATE NOCASE ASC, $COL_TITLE COLLATE NOCASE ASC"
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

    fun deleteById(id: Long) {
        writableDatabase.delete(TABLE_VIDEOS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    // ── Playback Profiles ──────────────────────────────────────────────────────

    fun insertProfile(p: PlaybackProfile): Long {
        val db = writableDatabase
        // If setting as default, clear existing default for this video
        if (p.isDefault) clearDefaultProfiles(db, p.videoId)
        return db.insert(TABLE_PROFILES, null, p.toContentValues())
    }

    fun updateProfile(p: PlaybackProfile) {
        val db = writableDatabase
        if (p.isDefault) clearDefaultProfiles(db, p.videoId, excludeId = p.id)
        db.update(TABLE_PROFILES, p.toContentValues(), "$PRO_ID = ?", arrayOf(p.id.toString()))
    }

    fun deleteProfile(id: Long) {
        writableDatabase.delete(TABLE_PROFILES, "$PRO_ID = ?", arrayOf(id.toString()))
    }

    fun getProfile(id: Long): PlaybackProfile? {
        readableDatabase.query(
            TABLE_PROFILES, null, "$PRO_ID = ?", arrayOf(id.toString()),
            null, null, null
        ).use { c -> if (c.moveToFirst()) return c.toProfile() }
        return null
    }

    fun listProfilesForVideo(videoId: Long): List<PlaybackProfile> {
        val list = mutableListOf<PlaybackProfile>()
        readableDatabase.query(
            TABLE_PROFILES, null, "$PRO_VIDEO_ID = ?", arrayOf(videoId.toString()),
            null, null, "$PRO_CREATED_AT ASC"
        ).use { c -> while (c.moveToNext()) list.add(c.toProfile()) }
        return list
    }

    fun getDefaultProfile(videoId: Long): PlaybackProfile? {
        readableDatabase.query(
            TABLE_PROFILES, null, "$PRO_VIDEO_ID = ? AND $PRO_IS_DEFAULT = 1", arrayOf(videoId.toString()),
            null, null, null, "1"
        ).use { c -> if (c.moveToFirst()) return c.toProfile() }
        return null
    }

    private fun clearDefaultProfiles(db: SQLiteDatabase, videoId: Long, excludeId: Long = -1L) {
        val cv = ContentValues().apply { put(PRO_IS_DEFAULT, 0) }
        if (excludeId > 0) {
            db.update(TABLE_PROFILES, cv, "$PRO_VIDEO_ID = ? AND $PRO_ID != ?",
                arrayOf(videoId.toString(), excludeId.toString()))
        } else {
            db.update(TABLE_PROFILES, cv, "$PRO_VIDEO_ID = ?", arrayOf(videoId.toString()))
        }
    }

    // ── Chapter Markers ────────────────────────────────────────────────────────

    fun insertChapter(c: ChapterMarker): Long =
        writableDatabase.insert(TABLE_CHAPTERS, null, c.toContentValues())

    fun updateChapter(c: ChapterMarker) {
        writableDatabase.update(TABLE_CHAPTERS, c.toContentValues(), "$CHA_ID = ?", arrayOf(c.id.toString()))
    }

    fun deleteChapter(id: Long) {
        writableDatabase.delete(TABLE_CHAPTERS, "$CHA_ID = ?", arrayOf(id.toString()))
    }

    fun deleteChaptersForVideo(videoId: Long) {
        writableDatabase.delete(TABLE_CHAPTERS, "$CHA_VIDEO_ID = ?", arrayOf(videoId.toString()))
    }

    fun listChaptersForVideo(videoId: Long): List<ChapterMarker> {
        val list = mutableListOf<ChapterMarker>()
        readableDatabase.query(
            TABLE_CHAPTERS, null, "$CHA_VIDEO_ID = ?", arrayOf(videoId.toString()),
            null, null, "$CHA_POSITION_MS ASC"
        ).use { c -> while (c.moveToNext()) list.add(c.toChapter()) }
        return list
    }

    companion object {
        const val DB_NAME    = "crow_theatron.db"
        const val DB_VERSION = 3

        // ── videos table ──────────────────────────────────────────────────────
        const val TABLE_VIDEOS          = "videos"
        const val COL_ID                = "id"
        const val COL_URI               = "uri"
        const val COL_TITLE             = "title"
        const val COL_FOLDER            = "folder_group"
        const val COL_DURATION_MS       = "duration_ms"
        const val COL_SIZE_BYTES        = "size_bytes"
        const val COL_THUMB             = "thumbnail"
        const val COL_POSITION_MS       = "position_ms"
        const val COL_PITCH_SEMITONES   = "pitch_semitones"
        const val COL_TRIM_START_MS     = "trim_start_ms"
        const val COL_TRIM_END_MS       = "trim_end_ms"
        const val COL_FAVORITE          = "favorite"
        const val COL_SEEK_JUMP_SEC     = "seek_jump_sec"
        const val COL_AUTO_NEXT         = "auto_play_next"
        const val COL_LOOP              = "loop_playback"
        const val COL_ENHANCEMENT       = "enhancement"
        const val COL_LAST_PLAYED       = "last_played_at"
        const val COL_SPEED             = "playback_speed"
        const val COL_VOLUME            = "volume_level"
        const val COL_BRIGHTNESS        = "brightness"
        const val COL_CONTRAST          = "contrast"
        const val COL_SATURATION        = "saturation"
        const val COL_HUE               = "hue"
        const val COL_SHARPNESS         = "sharpness"
        const val COL_ZOOM              = "zoom_level"
        const val COL_CROP              = "crop_mode"
        const val COL_AUDIO_BOOST       = "audio_boost"
        const val COL_EQ_PRESET         = "eq_preset"
        const val COL_SUB_TRACK         = "subtitle_track"
        const val COL_SUB_OFFSET        = "subtitle_offset_ms"
        const val COL_SUB_SIZE          = "subtitle_size_sp"
        const val COL_SUB_BOLD          = "subtitle_bold"
        const val COL_SUB_BG_ALPHA      = "subtitle_bg_alpha"
        const val COL_ORIENTATION       = "preferred_orientation"
        const val COL_ACTIVE_PROFILE    = "active_profile_id"

        private val CREATE_VIDEOS = """
            CREATE TABLE $TABLE_VIDEOS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_URI TEXT NOT NULL UNIQUE,
                $COL_TITLE TEXT NOT NULL,
                $COL_FOLDER TEXT NOT NULL,
                $COL_DURATION_MS INTEGER NOT NULL DEFAULT 0,
                $COL_SIZE_BYTES INTEGER NOT NULL DEFAULT 0,
                $COL_THUMB BLOB,
                $COL_POSITION_MS INTEGER NOT NULL DEFAULT 0,
                $COL_PITCH_SEMITONES INTEGER NOT NULL DEFAULT 0,
                $COL_TRIM_START_MS INTEGER NOT NULL DEFAULT 0,
                $COL_TRIM_END_MS INTEGER NOT NULL DEFAULT 0,
                $COL_FAVORITE INTEGER NOT NULL DEFAULT 0,
                $COL_SEEK_JUMP_SEC INTEGER NOT NULL DEFAULT 10,
                $COL_AUTO_NEXT INTEGER NOT NULL DEFAULT 0,
                $COL_LOOP INTEGER NOT NULL DEFAULT 0,
                $COL_ENHANCEMENT TEXT NOT NULL DEFAULT 'NONE',
                $COL_LAST_PLAYED INTEGER NOT NULL DEFAULT 0,
                $COL_SPEED REAL NOT NULL DEFAULT 1.0,
                $COL_VOLUME REAL NOT NULL DEFAULT 1.0,
                $COL_BRIGHTNESS REAL NOT NULL DEFAULT 0.0,
                $COL_CONTRAST REAL NOT NULL DEFAULT 1.0,
                $COL_SATURATION REAL NOT NULL DEFAULT 1.0,
                $COL_HUE REAL NOT NULL DEFAULT 0.0,
                $COL_SHARPNESS REAL NOT NULL DEFAULT 0.0,
                $COL_ZOOM REAL NOT NULL DEFAULT 1.0,
                $COL_CROP TEXT NOT NULL DEFAULT 'FIT',
                $COL_AUDIO_BOOST REAL NOT NULL DEFAULT 1.0,
                $COL_EQ_PRESET TEXT NOT NULL DEFAULT 'FLAT',
                $COL_SUB_TRACK INTEGER NOT NULL DEFAULT -1,
                $COL_SUB_OFFSET INTEGER NOT NULL DEFAULT 0,
                $COL_SUB_SIZE REAL NOT NULL DEFAULT 16.0,
                $COL_SUB_BOLD INTEGER NOT NULL DEFAULT 0,
                $COL_SUB_BG_ALPHA INTEGER NOT NULL DEFAULT 128,
                $COL_ORIENTATION INTEGER NOT NULL DEFAULT -1,
                $COL_ACTIVE_PROFILE INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        // ── playback_profiles table ────────────────────────────────────────────
        const val TABLE_PROFILES        = "playback_profiles"
        const val PRO_ID                = "id"
        const val PRO_VIDEO_ID          = "video_id"
        const val PRO_NAME              = "name"
        const val PRO_IS_DEFAULT        = "is_default"
        const val PRO_CREATED_AT        = "created_at"
        const val PRO_SPEED             = "speed"
        const val PRO_VOLUME            = "volume"
        const val PRO_AUDIO_BOOST       = "audio_boost"
        const val PRO_EQ_PRESET         = "eq_preset"
        const val PRO_LOOP              = "loop"
        const val PRO_AUTO_NEXT         = "auto_next"
        const val PRO_PITCH             = "pitch"
        const val PRO_TRIM_START        = "trim_start_ms"
        const val PRO_TRIM_END          = "trim_end_ms"
        const val PRO_ENHANCEMENT       = "enhancement"
        const val PRO_BRIGHTNESS        = "brightness"
        const val PRO_CONTRAST          = "contrast"
        const val PRO_SATURATION        = "saturation"
        const val PRO_HUE               = "hue"
        const val PRO_SHARPNESS         = "sharpness"
        const val PRO_ZOOM              = "zoom"
        const val PRO_CROP              = "crop"
        const val PRO_SUB_TRACK         = "sub_track"
        const val PRO_SUB_OFFSET        = "sub_offset_ms"
        const val PRO_SUB_SIZE          = "sub_size_sp"
        const val PRO_SUB_BOLD          = "sub_bold"
        const val PRO_SUB_BG_ALPHA      = "sub_bg_alpha"

        private val CREATE_PROFILES = """
            CREATE TABLE $TABLE_PROFILES (
                $PRO_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $PRO_VIDEO_ID INTEGER NOT NULL,
                $PRO_NAME TEXT NOT NULL,
                $PRO_IS_DEFAULT INTEGER NOT NULL DEFAULT 0,
                $PRO_CREATED_AT INTEGER NOT NULL DEFAULT 0,
                $PRO_SPEED REAL NOT NULL DEFAULT 1.0,
                $PRO_VOLUME REAL NOT NULL DEFAULT 1.0,
                $PRO_AUDIO_BOOST REAL NOT NULL DEFAULT 1.0,
                $PRO_EQ_PRESET TEXT NOT NULL DEFAULT 'FLAT',
                $PRO_LOOP INTEGER NOT NULL DEFAULT 0,
                $PRO_AUTO_NEXT INTEGER NOT NULL DEFAULT 0,
                $PRO_PITCH INTEGER NOT NULL DEFAULT 0,
                $PRO_TRIM_START INTEGER NOT NULL DEFAULT 0,
                $PRO_TRIM_END INTEGER NOT NULL DEFAULT 0,
                $PRO_ENHANCEMENT TEXT NOT NULL DEFAULT 'NONE',
                $PRO_BRIGHTNESS REAL NOT NULL DEFAULT 0.0,
                $PRO_CONTRAST REAL NOT NULL DEFAULT 1.0,
                $PRO_SATURATION REAL NOT NULL DEFAULT 1.0,
                $PRO_HUE REAL NOT NULL DEFAULT 0.0,
                $PRO_SHARPNESS REAL NOT NULL DEFAULT 0.0,
                $PRO_ZOOM REAL NOT NULL DEFAULT 1.0,
                $PRO_CROP TEXT NOT NULL DEFAULT 'FIT',
                $PRO_SUB_TRACK INTEGER NOT NULL DEFAULT -1,
                $PRO_SUB_OFFSET INTEGER NOT NULL DEFAULT 0,
                $PRO_SUB_SIZE REAL NOT NULL DEFAULT 16.0,
                $PRO_SUB_BOLD INTEGER NOT NULL DEFAULT 0,
                $PRO_SUB_BG_ALPHA INTEGER NOT NULL DEFAULT 128
            )
        """.trimIndent()

        // ── chapters table ────────────────────────────────────────────────────
        const val TABLE_CHAPTERS        = "chapter_markers"
        const val CHA_ID                = "id"
        const val CHA_VIDEO_ID          = "video_id"
        const val CHA_POSITION_MS       = "position_ms"
        const val CHA_LABEL             = "label"
        const val CHA_AUTO              = "is_auto_detected"
        const val CHA_CREATED_AT        = "created_at"

        private val CREATE_CHAPTERS = """
            CREATE TABLE $TABLE_CHAPTERS (
                $CHA_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $CHA_VIDEO_ID INTEGER NOT NULL,
                $CHA_POSITION_MS INTEGER NOT NULL,
                $CHA_LABEL TEXT NOT NULL,
                $CHA_AUTO INTEGER NOT NULL DEFAULT 0,
                $CHA_CREATED_AT INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
    }
}

// ── Extension: VideoEntity ↔ ContentValues ──────────────────────────────────

private fun VideoEntity.toContentValues(): ContentValues = ContentValues().apply {
    put(CrowDbHelper.COL_URI,              uriString)
    put(CrowDbHelper.COL_TITLE,            title)
    put(CrowDbHelper.COL_FOLDER,           folderGroup)
    put(CrowDbHelper.COL_DURATION_MS,      durationMs)
    put(CrowDbHelper.COL_SIZE_BYTES,       sizeBytes)
    put(CrowDbHelper.COL_THUMB,            thumbnail)
    put(CrowDbHelper.COL_POSITION_MS,      positionMs)
    put(CrowDbHelper.COL_PITCH_SEMITONES,  pitchSemitones)
    put(CrowDbHelper.COL_TRIM_START_MS,    trimStartMs)
    put(CrowDbHelper.COL_TRIM_END_MS,      trimEndMs)
    put(CrowDbHelper.COL_FAVORITE,         if (favorite) 1 else 0)
    put(CrowDbHelper.COL_SEEK_JUMP_SEC,    seekJumpSec)
    put(CrowDbHelper.COL_AUTO_NEXT,        if (autoPlayNext) 1 else 0)
    put(CrowDbHelper.COL_LOOP,             if (loopPlayback) 1 else 0)
    put(CrowDbHelper.COL_ENHANCEMENT,      enhancement.storageKey)
    put(CrowDbHelper.COL_LAST_PLAYED,      lastPlayedAt)
    put(CrowDbHelper.COL_SPEED,            playbackSpeed)
    put(CrowDbHelper.COL_VOLUME,           volumeLevel)
    put(CrowDbHelper.COL_BRIGHTNESS,       brightness)
    put(CrowDbHelper.COL_CONTRAST,         contrast)
    put(CrowDbHelper.COL_SATURATION,       saturation)
    put(CrowDbHelper.COL_HUE,              hue)
    put(CrowDbHelper.COL_SHARPNESS,        sharpness)
    put(CrowDbHelper.COL_ZOOM,             zoomLevel)
    put(CrowDbHelper.COL_CROP,             cropMode.storageKey)
    put(CrowDbHelper.COL_AUDIO_BOOST,      audioBoost)
    put(CrowDbHelper.COL_EQ_PRESET,        eqPreset.storageKey)
    put(CrowDbHelper.COL_SUB_TRACK,        subtitleTrackIndex)
    put(CrowDbHelper.COL_SUB_OFFSET,       subtitleOffsetMs)
    put(CrowDbHelper.COL_SUB_SIZE,         subtitleSizeSp)
    put(CrowDbHelper.COL_SUB_BOLD,         if (subtitleBold) 1 else 0)
    put(CrowDbHelper.COL_SUB_BG_ALPHA,     subtitleBackgroundAlpha)
    put(CrowDbHelper.COL_ORIENTATION,      preferredOrientation)
    put(CrowDbHelper.COL_ACTIVE_PROFILE,   activeProfileId)
}

private fun android.database.Cursor.toEntity(): VideoEntity {
    fun str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 }?.let { getString(it) }
    fun lng(col: String, def: Long = 0L): Long = getColumnIndex(col).takeIf { it >= 0 }?.let { getLong(it) } ?: def
    fun int(col: String, def: Int = 0): Int = getColumnIndex(col).takeIf { it >= 0 }?.let { getInt(it) } ?: def
    fun flt(col: String, def: Float = 0f): Float = getColumnIndex(col).takeIf { it >= 0 }?.let { getFloat(it) } ?: def
    val thumbIdx = getColumnIndex(CrowDbHelper.COL_THUMB)
    val thumb = if (thumbIdx >= 0 && !isNull(thumbIdx)) getBlob(thumbIdx) else null
    return VideoEntity(
        id                     = lng(CrowDbHelper.COL_ID),
        uriString              = str(CrowDbHelper.COL_URI) ?: "",
        title                  = str(CrowDbHelper.COL_TITLE) ?: "",
        folderGroup            = str(CrowDbHelper.COL_FOLDER) ?: "",
        durationMs             = lng(CrowDbHelper.COL_DURATION_MS),
        sizeBytes              = lng(CrowDbHelper.COL_SIZE_BYTES),
        thumbnail              = thumb,
        positionMs             = lng(CrowDbHelper.COL_POSITION_MS),
        pitchSemitones         = int(CrowDbHelper.COL_PITCH_SEMITONES),
        trimStartMs            = lng(CrowDbHelper.COL_TRIM_START_MS),
        trimEndMs              = lng(CrowDbHelper.COL_TRIM_END_MS),
        favorite               = int(CrowDbHelper.COL_FAVORITE) == 1,
        seekJumpSec            = int(CrowDbHelper.COL_SEEK_JUMP_SEC, 10),
        autoPlayNext           = int(CrowDbHelper.COL_AUTO_NEXT) == 1,
        loopPlayback           = int(CrowDbHelper.COL_LOOP) == 1,
        enhancement            = EnhancementMode.fromKey(str(CrowDbHelper.COL_ENHANCEMENT)),
        lastPlayedAt           = lng(CrowDbHelper.COL_LAST_PLAYED),
        playbackSpeed          = flt(CrowDbHelper.COL_SPEED, 1f),
        volumeLevel            = flt(CrowDbHelper.COL_VOLUME, 1f),
        brightness             = flt(CrowDbHelper.COL_BRIGHTNESS, 0f),
        contrast               = flt(CrowDbHelper.COL_CONTRAST, 1f),
        saturation             = flt(CrowDbHelper.COL_SATURATION, 1f),
        hue                    = flt(CrowDbHelper.COL_HUE, 0f),
        sharpness              = flt(CrowDbHelper.COL_SHARPNESS, 0f),
        zoomLevel              = flt(CrowDbHelper.COL_ZOOM, 1f),
        cropMode               = CropMode.fromKey(str(CrowDbHelper.COL_CROP)),
        audioBoost             = flt(CrowDbHelper.COL_AUDIO_BOOST, 1f),
        eqPreset               = EqPreset.fromKey(str(CrowDbHelper.COL_EQ_PRESET)),
        subtitleTrackIndex     = int(CrowDbHelper.COL_SUB_TRACK, -1),
        subtitleOffsetMs       = lng(CrowDbHelper.COL_SUB_OFFSET),
        subtitleSizeSp         = flt(CrowDbHelper.COL_SUB_SIZE, 16f),
        subtitleBold           = int(CrowDbHelper.COL_SUB_BOLD) == 1,
        subtitleBackgroundAlpha = int(CrowDbHelper.COL_SUB_BG_ALPHA, 128),
        preferredOrientation   = int(CrowDbHelper.COL_ORIENTATION, -1),
        activeProfileId        = lng(CrowDbHelper.COL_ACTIVE_PROFILE),
    )
}

// ── Extension: PlaybackProfile ↔ ContentValues ──────────────────────────────

private fun PlaybackProfile.toContentValues(): ContentValues = ContentValues().apply {
    put(CrowDbHelper.PRO_VIDEO_ID,    videoId)
    put(CrowDbHelper.PRO_NAME,        name)
    put(CrowDbHelper.PRO_IS_DEFAULT,  if (isDefault) 1 else 0)
    put(CrowDbHelper.PRO_CREATED_AT,  createdAt)
    put(CrowDbHelper.PRO_SPEED,       playbackSpeed)
    put(CrowDbHelper.PRO_VOLUME,      volumeLevel)
    put(CrowDbHelper.PRO_AUDIO_BOOST, audioBoost)
    put(CrowDbHelper.PRO_EQ_PRESET,   eqPreset.storageKey)
    put(CrowDbHelper.PRO_LOOP,        if (loopPlayback) 1 else 0)
    put(CrowDbHelper.PRO_AUTO_NEXT,   if (autoPlayNext) 1 else 0)
    put(CrowDbHelper.PRO_PITCH,       pitchSemitones)
    put(CrowDbHelper.PRO_TRIM_START,  trimStartMs)
    put(CrowDbHelper.PRO_TRIM_END,    trimEndMs)
    put(CrowDbHelper.PRO_ENHANCEMENT, enhancement.storageKey)
    put(CrowDbHelper.PRO_BRIGHTNESS,  brightness)
    put(CrowDbHelper.PRO_CONTRAST,    contrast)
    put(CrowDbHelper.PRO_SATURATION,  saturation)
    put(CrowDbHelper.PRO_HUE,         hue)
    put(CrowDbHelper.PRO_SHARPNESS,   sharpness)
    put(CrowDbHelper.PRO_ZOOM,        zoomLevel)
    put(CrowDbHelper.PRO_CROP,        cropMode.storageKey)
    put(CrowDbHelper.PRO_SUB_TRACK,   subtitleTrackIndex)
    put(CrowDbHelper.PRO_SUB_OFFSET,  subtitleOffsetMs)
    put(CrowDbHelper.PRO_SUB_SIZE,    subtitleSizeSp)
    put(CrowDbHelper.PRO_SUB_BOLD,    if (subtitleBold) 1 else 0)
    put(CrowDbHelper.PRO_SUB_BG_ALPHA, subtitleBackgroundAlpha)
}

private fun android.database.Cursor.toProfile(): PlaybackProfile {
    fun str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 }?.let { getString(it) }
    fun lng(col: String, def: Long = 0L): Long = getColumnIndex(col).takeIf { it >= 0 }?.let { getLong(it) } ?: def
    fun int(col: String, def: Int = 0): Int = getColumnIndex(col).takeIf { it >= 0 }?.let { getInt(it) } ?: def
    fun flt(col: String, def: Float = 0f): Float = getColumnIndex(col).takeIf { it >= 0 }?.let { getFloat(it) } ?: def
    return PlaybackProfile(
        id                     = lng(CrowDbHelper.PRO_ID),
        videoId                = lng(CrowDbHelper.PRO_VIDEO_ID),
        name                   = str(CrowDbHelper.PRO_NAME) ?: "Default",
        isDefault              = int(CrowDbHelper.PRO_IS_DEFAULT) == 1,
        createdAt              = lng(CrowDbHelper.PRO_CREATED_AT),
        playbackSpeed          = flt(CrowDbHelper.PRO_SPEED, 1f),
        volumeLevel            = flt(CrowDbHelper.PRO_VOLUME, 1f),
        audioBoost             = flt(CrowDbHelper.PRO_AUDIO_BOOST, 1f),
        eqPreset               = EqPreset.fromKey(str(CrowDbHelper.PRO_EQ_PRESET)),
        loopPlayback           = int(CrowDbHelper.PRO_LOOP) == 1,
        autoPlayNext           = int(CrowDbHelper.PRO_AUTO_NEXT) == 1,
        pitchSemitones         = int(CrowDbHelper.PRO_PITCH),
        trimStartMs            = lng(CrowDbHelper.PRO_TRIM_START),
        trimEndMs              = lng(CrowDbHelper.PRO_TRIM_END),
        enhancement            = EnhancementMode.fromKey(str(CrowDbHelper.PRO_ENHANCEMENT)),
        brightness             = flt(CrowDbHelper.PRO_BRIGHTNESS),
        contrast               = flt(CrowDbHelper.PRO_CONTRAST, 1f),
        saturation             = flt(CrowDbHelper.PRO_SATURATION, 1f),
        hue                    = flt(CrowDbHelper.PRO_HUE),
        sharpness              = flt(CrowDbHelper.PRO_SHARPNESS),
        zoomLevel              = flt(CrowDbHelper.PRO_ZOOM, 1f),
        cropMode               = CropMode.fromKey(str(CrowDbHelper.PRO_CROP)),
        subtitleTrackIndex     = int(CrowDbHelper.PRO_SUB_TRACK, -1),
        subtitleOffsetMs       = lng(CrowDbHelper.PRO_SUB_OFFSET),
        subtitleSizeSp         = flt(CrowDbHelper.PRO_SUB_SIZE, 16f),
        subtitleBold           = int(CrowDbHelper.PRO_SUB_BOLD) == 1,
        subtitleBackgroundAlpha = int(CrowDbHelper.PRO_SUB_BG_ALPHA, 128),
    )
}

// ── Extension: ChapterMarker ↔ ContentValues ─────────────────────────────────

private fun ChapterMarker.toContentValues(): ContentValues = ContentValues().apply {
    put(CrowDbHelper.CHA_VIDEO_ID,    videoId)
    put(CrowDbHelper.CHA_POSITION_MS, positionMs)
    put(CrowDbHelper.CHA_LABEL,       label)
    put(CrowDbHelper.CHA_AUTO,        if (isAutoDetected) 1 else 0)
    put(CrowDbHelper.CHA_CREATED_AT,  createdAt)
}

private fun android.database.Cursor.toChapter(): ChapterMarker {
    fun str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 }?.let { getString(it) }
    fun lng(col: String): Long = getColumnIndex(col).takeIf { it >= 0 }?.let { getLong(it) } ?: 0L
    fun int(col: String): Int = getColumnIndex(col).takeIf { it >= 0 }?.let { getInt(it) } ?: 0
    return ChapterMarker(
        id              = lng(CrowDbHelper.CHA_ID),
        videoId         = lng(CrowDbHelper.CHA_VIDEO_ID),
        positionMs      = lng(CrowDbHelper.CHA_POSITION_MS),
        label           = str(CrowDbHelper.CHA_LABEL) ?: "",
        isAutoDetected  = int(CrowDbHelper.CHA_AUTO) == 1,
        createdAt       = lng(CrowDbHelper.CHA_CREATED_AT),
    )
}
