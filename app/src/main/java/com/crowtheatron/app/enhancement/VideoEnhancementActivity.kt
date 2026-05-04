package com.crowtheatron.app.enhancement

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.crowtheatron.app.data.AppPrefs
import com.crowtheatron.app.databinding.ActivityVideoEnhancementBinding
import com.crowtheatron.app.ui.setContentWithCrowInsets

class VideoEnhancementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoEnhancementBinding
    private val prefs by lazy { AppPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEnhancementBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = EnhancementListAdapter { mode ->
            prefs.defaultEnhancement = mode
            Toast.makeText(this, "Default enhancement: ${mode.displayName}", Toast.LENGTH_SHORT).show()
        }
    }
}
