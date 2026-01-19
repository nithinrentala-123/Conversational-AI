package com.niquewrld.conversationalai

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashScreen : AppCompatActivity() {
    
    private val SPLASH_DELAY = 2000L // 2 seconds
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Lock to portrait orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        setContentView(R.layout.activity_splash)
        
        // Optional: Add fade-in animation to logo
        val logo = findViewById<ImageView>(R.id.splash_logo)
        val title = findViewById<TextView>(R.id.splash_title)
        val subtitle = findViewById<TextView>(R.id.splash_subtitle)
        
        // Fade in animation
        logo.alpha = 0f
        title.alpha = 0f
        subtitle.alpha = 0f
        
        logo.animate().alpha(1f).setDuration(500).start()
        title.animate().alpha(1f).setDuration(500).setStartDelay(200).start()
        subtitle.animate().alpha(1f).setDuration(500).setStartDelay(400).start()
        
        // Navigate to MainActivity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_DELAY)
    }
}