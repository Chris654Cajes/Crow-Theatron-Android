package com.crowtheatron.app.util

import android.graphics.ColorMatrix
import com.crowtheatron.app.data.EnhancementMode

/**
 * Client-side color-matrix presets to simulate richer / cinematic presentation (no re-encode).
 */
object VideoEnhancement {
    fun matrixFor(mode: EnhancementMode): ColorMatrix = when (mode) {
        EnhancementMode.NONE -> ColorMatrix()
        EnhancementMode.VIVID_HD -> {
            ColorMatrix().apply {
                setSaturation(1.35f)
                val contrast = 1.12f
                setScale(contrast, contrast, contrast, 1f)
            }
        }
        EnhancementMode.CINEMA_CONTRAST -> {
            ColorMatrix(
                floatArrayOf(
                    1.25f, 0f, 0f, 0f, -18f,
                    0f, 1.2f, 0f, 0f, -18f,
                    0f, 0f, 1.15f, 0f, -12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        EnhancementMode.WARM_FILM -> {
            val m = ColorMatrix()
            m.setSaturation(1.08f)
            val warm = ColorMatrix(
                floatArrayOf(
                    1.08f, 0f, 0f, 0f, 8f,
                    0f, 1.02f, 0f, 0f, 4f,
                    0f, 0f, 0.95f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            m.postConcat(warm)
            m
        }
        EnhancementMode.COOL_HDR_SIM -> {
            val m = ColorMatrix()
            m.setSaturation(1.1f)
            val cool = ColorMatrix(
                floatArrayOf(
                    0.98f, 0f, 0f, 0f, 0f,
                    0f, 1.02f, 0f, 0f, 6f,
                    0f, 0f, 1.12f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            m.postConcat(cool)
            m
        }
    }
}
