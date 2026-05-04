package com.crowtheatron.app.data

/** A named timestamp bookmark stored per video. */
data class ChapterMarker(
    val id: Long = 0L,
    val videoId: Long,
    val positionMs: Long,
    val label: String,
    val isAutoDetected: Boolean = false, // true = from scene detection
    val createdAt: Long = System.currentTimeMillis(),
)
