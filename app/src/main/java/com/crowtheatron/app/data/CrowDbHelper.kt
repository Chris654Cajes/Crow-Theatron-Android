package com.crowtheatron.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CrowDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
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
                $COL_VOLUME REAL NOT NULL DEFAULT 1.0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_videos_folder ON $TABLE_VIDEOS($COL_FOLDER)")
        db.execSQL("CREATE INDEX idx_videos_favorite ON $TABLE_VIDEOS($COL_FAVORITE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Non-destructive migration: add new columns if they don't exist
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_SPEED REAL NOT NULL DEFAULT 1.0") }
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN $COL_VOLUME REAL NOT NULL DEFAULT 1.0") }
        }
    }

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

    fun listPlaybackMemory(limit: Int = 200): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        readableDatabase.query(
            TABLE_VIDEOS, null, "$COL_LAST_PLAYED > 0", null, null, null,
            "$COL_LAST_PLAYED DESC", limit.toString()
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

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

    companion object {
        const val DB_NAME    = "crow_theatron.db"
        const val DB_VERSION = 2          // bumped from 1 → 2 for speed + volume columns
        const val TABLE_VIDEOS = "videos"
        const val COL_ID             = "id"
        const val COL_URI            = "uri"
        const val COL_TITLE          = "title"
        const val COL_FOLDER         = "folder_group"
        const val COL_DURATION_MS    = "duration_ms"
        const val COL_SIZE_BYTES     = "size_bytes"
        const val COL_THUMB          = "thumbnail"
        const val COL_POSITION_MS    = "position_ms"
        const val COL_PITCH_SEMITONES = "pitch_semitones"
        const val COL_TRIM_START_MS  = "trim_start_ms"
        const val COL_TRIM_END_MS    = "trim_end_ms"
        const val COL_FAVORITE       = "favorite"
        const val COL_SEEK_JUMP_SEC  = "seek_jump_sec"
        const val COL_AUTO_NEXT      = "auto_play_next"
        const val COL_LOOP           = "loop_playback"
        const val COL_ENHANCEMENT    = "enhancement"
        const val COL_LAST_PLAYED    = "last_played_at"
        const val COL_SPEED          = "playback_speed"
        const val COL_VOLUME         = "volume_level"
    }
}

private fun VideoEntity.toContentValues(): ContentValues = ContentValues().apply {
    put(CrowDbHelper.COL_URI,             uriString)
    put(CrowDbHelper.COL_TITLE,           title)
    put(CrowDbHelper.COL_FOLDER,          folderGroup)
    put(CrowDbHelper.COL_DURATION_MS,     durationMs)
    put(CrowDbHelper.COL_SIZE_BYTES,      sizeBytes)
    put(CrowDbHelper.COL_THUMB,           thumbnail)
    put(CrowDbHelper.COL_POSITION_MS,     positionMs)
    put(CrowDbHelper.COL_PITCH_SEMITONES, pitchSemitones)
    put(CrowDbHelper.COL_TRIM_START_MS,   trimStartMs)
    put(CrowDbHelper.COL_TRIM_END_MS,     trimEndMs)
    put(CrowDbHelper.COL_FAVORITE,        if (favorite) 1 else 0)
    put(CrowDbHelper.COL_SEEK_JUMP_SEC,   seekJumpSec)
    put(CrowDbHelper.COL_AUTO_NEXT,       if (autoPlayNext) 1 else 0)
    put(CrowDbHelper.COL_LOOP,            if (loopPlayback) 1 else 0)
    put(CrowDbHelper.COL_ENHANCEMENT,     enhancement.storageKey)
    put(CrowDbHelper.COL_LAST_PLAYED,     lastPlayedAt)
    put(CrowDbHelper.COL_SPEED,           playbackSpeed)
    put(CrowDbHelper.COL_VOLUME,          volumeLevel)
}

private fun Cursor.toEntity(): VideoEntity {
    val thumbIdx = getColumnIndex(CrowDbHelper.COL_THUMB)
    val thumb = if (thumbIdx >= 0 && !isNull(thumbIdx)) getBlob(thumbIdx) else null

    fun floatOrDefault(col: String, default: Float): Float {
        val idx = getColumnIndex(col)
        return if (idx >= 0) getFloat(idx) else default
    }

    return VideoEntity(
        id             = getLong(getColumnIndexOrThrow(CrowDbHelper.COL_ID)),
        uriString      = getString(getColumnIndexOrThrow(CrowDbHelper.COL_URI)),
        title          = getString(getColumnIndexOrThrow(CrowDbHelper.COL_TITLE)),
        folderGroup    = getString(getColumnIndexOrThrow(CrowDbHelper.COL_FOLDER)),
        durationMs     = getLong(getColumnIndexOrThrow(CrowDbHelper.COL_DURATION_MS)),
        sizeBytes      = getLong(getColumnIndexOrThrow(CrowDbHelper.COL_SIZE_BYTES)),
        thumbnail      = thumb,
        positionMs     = getLong(getColumnIndexOrThrow(CrowDbHelper.COL_POSITION_MS)),
        pitchSemitones = getInt(getColumnIndexOrThrow(CrowDbHelper.COL_PITCH_SEMITONES)),
        trimStartMs    = getLong(getColumnIndexOrThrow(CrowDbHelper.COL_TRIM_START_MS)),
        trimEndMs      = getLong(getColumnIndexOrThrow(CrowDbHelper.COL_TRIM_END_MS)),
        favorite       = getInt(getColumnIndexOrThrow(CrowDbHelper.COL_FAVORITE)) == 1,
        seekJumpSec    = getInt(getColumnIndexOrThrow(CrowDbHelper.COL_SEEK_JUMP_SEC)),
        autoPlayNext   = getInt(getColumnIndexOrThrow(CrowDbHelper.COL_AUTO_NEXT)) == 1,
        loopPlayback   = getInt(getColumnIndexOrThrow(CrowDbHelper.COL_LOOP)) == 1,
        enhancement    = EnhancementMode.fromKey(getString(getColumnIndexOrThrow(CrowDbHelper.COL_ENHANCEMENT))),
        lastPlayedAt   = getLong(getColumnIndexOrThrow(CrowDbHelper.COL_LAST_PLAYED)),
        playbackSpeed  = floatOrDefault(CrowDbHelper.COL_SPEED, 1.0f),
        volumeLevel    = floatOrDefault(CrowDbHelper.COL_VOLUME, 1.0f),
    )
}
