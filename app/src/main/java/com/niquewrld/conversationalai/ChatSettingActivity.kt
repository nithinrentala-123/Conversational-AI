package com.niquewrld.conversationalai

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.niquewrld.conversationalai.Room.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChatSettingActivity : AppCompatActivity() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var systemPromptInput: TextInputEditText
    private lateinit var modelInfoText: TextView
    private lateinit var versionText: TextView
    
    private val prefs by lazy { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_settings)

        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        setupViews()
        loadSettings()
    }
    
    private fun setupViews() {
        // Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        
        // System Prompt
        systemPromptInput = findViewById(R.id.system_prompt_input)
        
        // Model info
        modelInfoText = findViewById(R.id.model_info_text)
        val selectedModel = prefs.getString("selected_model", null)
        modelInfoText.text = if (selectedModel != null) "Current model: $selectedModel" else "No model loaded"
        
        // Version
        versionText = findViewById(R.id.version_text)
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            versionText.text = "Version $versionName"
        } catch (e: Exception) {
            versionText.text = "Version 1.0.0"
        }
        
        // Clear History Button
        findViewById<LinearLayout>(R.id.clear_history_button).setOnClickListener {
            showClearHistoryDialog()
        }
        
        // Website Link
        findViewById<TextView>(R.id.website_link).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://niquewrld.com"))
            startActivity(intent)
        }
    }
    
    private fun loadSettings() {
        val systemPrompt = prefs.getString("system_prompt", "You are Jarvis.")
        systemPromptInput.setText(systemPrompt)
    }
    
    private fun saveSettings() {
        val systemPrompt = systemPromptInput.text.toString().trim()
        if (systemPrompt.isNotEmpty()) {
            prefs.edit().putString("system_prompt", systemPrompt).apply()
        }
    }
    
    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Clear Chat History")
            .setMessage("Are you sure you want to delete all conversations? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                clearHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(this@ChatSettingActivity)
            db.messageDao().deleteAllMessages()
            db.sessionDao().deleteAllSessions()
            
            // Clear saved session
            prefs.edit().remove("current_session_id").apply()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ChatSettingActivity, "Chat history cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        saveSettings()
    }
}
