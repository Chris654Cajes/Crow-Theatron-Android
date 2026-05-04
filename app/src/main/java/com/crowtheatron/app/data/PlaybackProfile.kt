package com.crowtheatron.app.data

/**
 * A named playback profile tied to a single video.
 * All playback customisations live here; VideoEntity.activeProfileId points to
 * the currently-selected one. A video can have unlimited profiles.
 */
data class PlaybackProfile(
    val id: Long = 0L,
    val videoId: Long,
    val name: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    // ── Playback ─────────────────────────────────────────────────────────────
    val playbackSpeed: Float = 1.0f,
    val volumeLevel: Float = 1.0f,
    val audioBoost: Float = 1.0f,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val loopPlayback: Boolean = false,
    val autoPlayNext: Boolean = false,
    val pitchSemitones: Int = 0,
    // ── Trim ─────────────────────────────────────────────────────────────────
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    // ── Video enhancement ────────────────────────────────────────────────────
    val enhancement: EnhancementMode = EnhancementMode.NONE,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val hue: Float = 0f,
    val sharpness: Float = 0f,
    val zoomLevel: Float = 1f,
    val cropMode: CropMode = CropMode.FIT,
    // ── Subtitle ─────────────────────────────────────────────────────────────
    val subtitleTrackIndex: Int = -1,
    val subtitleOffsetMs: Long = 0L,
    val subtitleSizeSp: Float = 16f,
    val subtitleBold: Boolean = false,
    val subtitleBackgroundAlpha: Int = 128,
)
