package com.crowtheatron.app.data

enum class EnhancementMode(val storageKey: String, val displayName: String) {
    NONE("NONE", "None"),
    VIVID_HD("VIVID_HD", "Vivid HD"),
    CINEMA_CONTRAST("CINEMA_CONTRAST", "Cinema Contrast"),
    WARM_FILM("WARM_FILM", "Warm Film"),
    COOL_HDR_SIM("COOL_HDR_SIM", "Cool HDR"),
    AMOLED("AMOLED", "AMOLED"),
    NIGHT_MODE("NIGHT_MODE", "Night Mode"),
    ANIME("ANIME", "Anime"),
    EYE_COMFORT("EYE_COMFORT", "Eye Comfort"),
    VIVID_OUTDOOR("VIVID_OUTDOOR", "Vivid Outdoor"),
    CINEMATIC_DARK("CINEMATIC_DARK", "Cinematic Dark");

    companion object {
        fun fromKey(key: String?): EnhancementMode =
            entries.find { it.storageKey == key } ?: NONE
    }
}
