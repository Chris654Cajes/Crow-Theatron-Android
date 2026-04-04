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

        val sec = prefs.defaultSeekJumpSec.coerceIn(5, 300)
        binding.seekBarInterval.progress = sec - 5
        binding.seekValue.text = sec.toString()

        binding.seekBarInterval.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress + 5
                    binding.seekValue.text = v.toString()
                    if (fromUser) prefs.defaultSeekJumpSec = v
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
    }
}
