package com.crowtheatron.app.data

enum class CropMode(val storageKey: String) {
    FIT("FIT"),
    FILL("FILL"),
    CROP_CENTER("CROP_CENTER"),
    STRETCH("STRETCH"),
    ZOOM_IN("ZOOM_IN");

    companion object {
        fun fromKey(key: String?): CropMode = entries.find { it.storageKey == key } ?: FIT
    }
}
