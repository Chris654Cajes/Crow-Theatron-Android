package com.crowtheatron.app.data

enum class EnhancementMode(val storageKey: String) {
    NONE("NONE"),
    VIVID_HD("VIVID_HD"),
    CINEMA_CONTRAST("CINEMA_CONTRAST"),
    WARM_FILM("WARM_FILM"),
    COOL_HDR_SIM("COOL_HDR_SIM");

    companion object {
        fun fromKey(key: String?): EnhancementMode =
            entries.find { it.storageKey == key } ?: NONE
    }
}
