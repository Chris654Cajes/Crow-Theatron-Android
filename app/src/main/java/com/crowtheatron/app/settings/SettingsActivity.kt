package com.crowtheatron.app.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.crowtheatron.app.data.AppPrefs
import com.crowtheatron.app.databinding.ActivitySettingsBinding
import com.crowtheatron.app.ui.setContentWithCrowInsets

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { AppPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        prefs.defaultSeekJumpSec = 10
        val sec = prefs.defaultSeekJumpSec
        binding.seekBarInterval.progress = 0
        binding.seekValue.text = sec.toString()
        binding.seekBarInterval.isEnabled = false
    }
}
