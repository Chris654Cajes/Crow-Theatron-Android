package com.crowtheatron.app.data

import android.content.Context

class AppPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var defaultSeekJumpSec: Int
        get() = 10
        set(v) = p.edit().putInt(KEY_SEEK_JUMP, 10).apply()

    var defaultEnhancement: EnhancementMode
        get() = EnhancementMode.fromKey(p.getString(KEY_ENHANCEMENT, null))
        set(v) = p.edit().putString(KEY_ENHANCEMENT, v.storageKey).apply()

    companion object {
        private const val PREFS = "crow_theatron_prefs"
        private const val KEY_SEEK_JUMP = "default_seek_jump_sec"
        private const val KEY_ENHANCEMENT = "default_enhancement"
    }
}
