package com.crowtheatron.app.data

enum class EqPreset(val storageKey: String, val displayName: String) {
    FLAT("FLAT", "Flat"),
    BASS_BOOST("BASS_BOOST", "Bass Boost"),
    TREBLE_BOOST("TREBLE_BOOST", "Treble Boost"),
    VOCAL_CLARITY("VOCAL_CLARITY", "Vocal Clarity"),
    CINEMA("CINEMA", "Cinema"),
    NIGHT("NIGHT", "Night Mode"),
    LOUD("LOUD", "Loudness");

    companion object {
        fun fromKey(key: String?): EqPreset = entries.find { it.storageKey == key } ?: FLAT
    }
}
