package com.niquewrld.conversationalai.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * ToolExecutor - Handles function calling for tools-enabled models
 * 
 * Parses model output for tool calls and executes corresponding Android actions
 */
class ToolExecutor(private val context: Context) {
    
    companion object {
        private const val TAG = "ToolExecutor"
        
        // Tool definitions for the model prompt
        val TOOLS_PROMPT = """
You have access to the following tools. When the user asks you to perform an action, respond with a JSON tool call in this exact format:
{"tool": "tool_name", "params": {"param1": "value1"}}

Available tools:

1. open_app - Opens an app on the device
   params: {"app_name": "WhatsApp"} or {"package": "com.whatsapp"}
   Example: {"tool": "open_app", "params": {"app_name": "WhatsApp"}}

2. open_website - Opens a URL in the browser
   params: {"url": "https://google.com"}
   Example: {"tool": "open_website", "params": {"url": "https://www.google.com"}}

3. search_web - Searches the web for something
   params: {"query": "weather today"}
   Example: {"tool": "search_web", "params": {"query": "best restaurants nearby"}}

4. make_call - Opens the dialer with a phone number
   params: {"number": "1234567890"}
   Example: {"tool": "make_call", "params": {"number": "911"}}

5. send_sms - Opens SMS app to send a message
   params: {"number": "1234567890", "message": "Hello!"}
   Example: {"tool": "send_sms", "params": {"number": "1234567890", "message": "Hi there!"}}

6. set_alarm - Sets an alarm
   params: {"hour": 7, "minute": 30, "message": "Wake up"}
   Example: {"tool": "set_alarm", "params": {"hour": 7, "minute": 0, "message": "Morning alarm"}}

7. open_settings - Opens device settings
   params: {"setting": "wifi"} (options: wifi, bluetooth, display, sound, battery, apps, location)
   Example: {"tool": "open_settings", "params": {"setting": "wifi"}}

8. take_photo - Opens the camera to take a photo
   params: {} (no params needed)
   Example: {"tool": "take_photo", "params": {}}

9. play_music - Opens music player or plays music
   params: {"query": "song name"} (optional)
   Example: {"tool": "play_music", "params": {"query": "happy birthday"}}

10. set_timer - Sets a countdown timer
    params: {"seconds": 300, "message": "Timer done"}
    Example: {"tool": "set_timer", "params": {"seconds": 180, "message": "Eggs ready"}}

If the user's request doesn't require a tool, just respond normally with text.
If you use a tool, output ONLY the JSON, nothing else.
""".trimIndent()
        
        // Common app package names
        val APP_PACKAGES = mapOf(
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "youtube" to "com.google.android.youtube",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "tiktok" to "com.zhiliaoapp.musically",
            "snapchat" to "com.snapchat.android",
            "telegram" to "org.telegram.messenger",
            "messenger" to "com.facebook.orca",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "chrome" to "com.android.chrome",
            "camera" to "com.android.camera",
            "calculator" to "com.android.calculator2",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.android.deskclock",
            "contacts" to "com.android.contacts",
            "photos" to "com.google.android.apps.photos",
            "google photos" to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "settings" to "com.android.settings",
            "files" to "com.google.android.apps.nbu.files",
            "drive" to "com.google.android.apps.docs",
            "google drive" to "com.google.android.apps.docs"
        )
    }
    
    /**
     * Check if response contains a tool call and execute it
     * Returns: Pair<Boolean, String> - (wasToolCall, resultMessage)
     */
    fun parseAndExecute(response: String): Pair<Boolean, String> {
        // Try to find JSON in the response
        val jsonMatch = Regex("""\{[^{}]*"tool"[^{}]*\}""").find(response)
        
        if (jsonMatch == null) {
            return Pair(false, response)
        }
        
        try {
            val json = JSONObject(jsonMatch.value)
            val toolName = json.optString("tool", "")
            val params = json.optJSONObject("params") ?: JSONObject()
            
            Log.d(TAG, "Executing tool: $toolName with params: $params")
            
            val result = when (toolName) {
                "open_app" -> openApp(params)
                "open_website" -> openWebsite(params)
                "search_web" -> searchWeb(params)
                "make_call" -> makeCall(params)
                "send_sms" -> sendSms(params)
                "set_alarm" -> setAlarm(params)
                "open_settings" -> openSettings(params)
                "take_photo" -> takePhoto()
                "play_music" -> playMusic(params)
                "set_timer" -> setTimer(params)
                else -> "Unknown tool: $toolName"
            }
            
            return Pair(true, result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tool call: ${e.message}")
            return Pair(false, response)
        }
    }
    
    private fun openApp(params: JSONObject): String {
        val appName = params.optString("app_name", "").lowercase()
        var packageName = params.optString("package", "")
        
        // Look up package name if not provided
        if (packageName.isEmpty() && appName.isNotEmpty()) {
            packageName = APP_PACKAGES[appName] ?: ""
        }
        
        if (packageName.isEmpty()) {
            // Try to find app by name in installed apps
            packageName = findAppPackage(appName)
        }
        
        if (packageName.isEmpty()) {
            return "❌ Could not find app: $appName"
        }
        
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "✅ Opening ${appName.ifEmpty { packageName }}"
            } else {
                "❌ App not installed: $appName"
            }
        } catch (e: Exception) {
            "❌ Failed to open app: ${e.message}"
        }
    }
    
    private fun findAppPackage(appName: String): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(appName) || appName.contains(label)) {
                return app.packageName
            }
        }
        return ""
    }
    
    private fun openWebsite(params: JSONObject): String {
        var url = params.optString("url", "")
        
        if (url.isEmpty()) {
            return "❌ No URL provided"
        }
        
        // Add https:// if missing
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "✅ Opening $url"
        } catch (e: Exception) {
            "❌ Failed to open website: ${e.message}"
        }
    }
    
    private fun searchWeb(params: JSONObject): String {
        val query = params.optString("query", "")
        
        if (query.isEmpty()) {
            return "❌ No search query provided"
        }
        
        return try {
            val searchUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "✅ Searching for: $query"
        } catch (e: Exception) {
            "❌ Failed to search: ${e.message}"
        }
    }
    
    private fun makeCall(params: JSONObject): String {
        val number = params.optString("number", "")
        
        if (number.isEmpty()) {
            return "❌ No phone number provided"
        }
        
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "✅ Opening dialer for: $number"
        } catch (e: Exception) {
            "❌ Failed to open dialer: ${e.message}"
        }
    }
    
    private fun sendSms(params: JSONObject): String {
        val number = params.optString("number", "")
        val message = params.optString("message", "")
        
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
            intent.putExtra("sms_body", message)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "✅ Opening SMS to: $number"
        } catch (e: Exception) {
            "❌ Failed to open SMS: ${e.message}"
        }
    }
    
    private fun setAlarm(params: JSONObject): String {
        val hour = params.optInt("hour", -1)
        val minute = params.optInt("minute", 0)
        val message = params.optString("message", "Alarm")
        
        if (hour < 0 || hour > 23) {
            return "❌ Invalid hour: $hour"
        }
        
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "✅ Setting alarm for ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        } catch (e: Exception) {
            "❌ Failed to set alarm: ${e.message}"
        }
    }
    
    private fun openSettings(params: JSONObject): String {
        val setting = params.optString("setting", "").lowercase()
        
        val action = when (setting) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "screen" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "audio", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
            "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "date", "time" -> Settings.ACTION_DATE_SETTINGS
            "language" -> Settings.ACTION_LOCALE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "airplane", "flight" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "data", "mobile data" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        
        return try {
            val intent = Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "✅ Opening ${setting.ifEmpty { "device" }} settings"
        } catch (e: Exception) {
            "❌ Failed to open settings: ${e.message}"
        }
    }
    
    private fun takePhoto(): String {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "✅ Opening camera"
        } catch (e: Exception) {
            "❌ Failed to open camera: ${e.message}"
        }
    }
    
    private fun playMusic(params: JSONObject): String {
        val query = params.optString("query", "")
        
        return try {
            if (query.isNotEmpty()) {
                // Search for music
                val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
                    putExtra(MediaStore.EXTRA_MEDIA_TITLE, query)
                    putExtra(android.app.SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "✅ Playing: $query"
            } else {
                // Open default music app
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_APP_MUSIC)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "✅ Opening music player"
            }
        } catch (e: Exception) {
            "❌ Failed to play music: ${e.message}"
        }
    }
    
    private fun setTimer(params: JSONObject): String {
        val seconds = params.optInt("seconds", 0)
        val message = params.optString("message", "Timer")
        
        if (seconds <= 0) {
            return "❌ Invalid timer duration"
        }
        
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val minutes = seconds / 60
            val secs = seconds % 60
            "✅ Setting timer for ${if (minutes > 0) "${minutes}m " else ""}${secs}s"
        } catch (e: Exception) {
            "❌ Failed to set timer: ${e.message}"
        }
    }
}
