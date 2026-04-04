package com.crowtheatron.app.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.crowtheatron.app.databinding.ActivitySplashBinding
import com.crowtheatron.app.main.MainActivity
import com.crowtheatron.app.ui.setContentWithCrowInsets

class SplashActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val goMain = Runnable {
        if (isFinishing) return@Runnable
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)
        mainHandler.postDelayed(goMain, SPLASH_DELAY_MS)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(goMain)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DELAY_MS = 1600L
    }
}
