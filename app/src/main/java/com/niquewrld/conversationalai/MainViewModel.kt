package com.niquewrld.conversationalai

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.niquewrld.conversationalai.Room.AppDatabase
import com.niquewrld.conversationalai.Room.ChatMessage
import com.niquewrld.conversationalai.Room.ChatSession
import com.niquewrld.conversationalai.service.ModelDownloadService
import com.niquewrld.conversationalai.tools.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MainViewModel - recreated based on decompiled F2/I.smali
 * 
 * Fields from F2/I.smali:
 * - g: LlmInference
 * - v: LlmInferenceSession
 * - b: ChatSessionDao
 * - c: ChatMessageDao
 * - d: SnapshotStateList (models list)
 * - e: MutableStateFlow<Boolean> (isLoading)
 * - y: Job (current generation job)
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    // Database (like F2/I fields b, c)
    private val db = AppDatabase.getInstance(application)
    private val chatSessionDao = db.sessionDao()
    private val chatMessageDao = db.messageDao()
    
    // Tool executor for function calling
    private val toolExecutor = ToolExecutor(application)
    
    // SharedPreferences
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    // LLM Inference - directly like F2/I.smali fields g and v
    var llmInference: LlmInference? = null
        private set
    var llmSession: LlmInferenceSession? = null
        private set
    
    // Current generation job (like F2/I field y)
    private var currentJob: Job? = null
    
    // Models list (like F2/I field d - SnapshotStateList)
    val modelsList = mutableStateListOf<Model>()
    
    // State flows (like F2/I fields e, f, h, i, j, k, etc.)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()
    
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()
    
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    
    private val _selectedModel = MutableStateFlow<Model?>(null)
    val selectedModel: StateFlow<Model?> = _selectedModel.asStateFlow()
    
    // Mutable states (like F2/I fields m, w, x, A)
    val showSettings = mutableStateOf(false)
    
    // Chat sessions and messages
    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()
    
    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    
    // Selected image for vision models
    private val _selectedImageUri = MutableStateFlow<android.net.Uri?>(null)
    val selectedImageUri: StateFlow<android.net.Uri?> = _selectedImageUri.asStateFlow()
    
    // Event flow for streaming responses (like F2/I field H - MutableSharedFlow)
    val responseEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 5)
    
    // System prompt - loaded from settings, default "You are Jarvis."
    private val systemPrompt: String
        get() = prefs.getString("system_prompt", "You are Jarvis.") ?: "You are Jarvis."
    
    // Full prompt including tools if applicable
    private fun getFullSystemPrompt(): String {
        val base = systemPrompt
        val isToolsModel = _selectedModel.value?.isTools == true
        return if (isToolsModel) {
            "$base\n\n${ToolExecutor.TOOLS_PROMPT}"
        } else {
            base
        }
    }
    
    /**
     * Check if current model supports tools/function calling
     */
    fun isToolsModel(): Boolean {
        return _selectedModel.value?.isTools == true
    }
    
    /**
     * Initialize - load models and sessions
     * Like F2/I static method l()
     */
    fun initialize() {
        viewModelScope.launch {
            loadModels()
            loadChatSessions()
            loadSavedModel()
            loadSavedSession()
        }
    }
    
    /**
     * Load models from assets
     */
    private suspend fun loadModels() {
        try {
            val context = getApplication<Application>()
            val jsonString = context.assets.open("models.json").bufferedReader().use { it.readText() }
            val gson = com.google.gson.Gson()
            val modelsArray = gson.fromJson(jsonString, Array<Model>::class.java)
            modelsList.clear()
            modelsList.addAll(modelsArray.toList())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load models: ${e.message}")
        }
    }
    
    /**
     * Load chat sessions from database
     */
    private suspend fun loadChatSessions() {
        val sessions = chatSessionDao.getAllSessions()
        _chatSessions.value = sessions
    }
    
    private fun loadSavedSession() {
        viewModelScope.launch {
            val savedSessionId = prefs.getLong("current_session_id", -1L)
            if (savedSessionId != -1L) {
                loadSession(savedSessionId)
            }
        }
    }
    
    private fun saveCurrentSession(sessionId: Long) {
        prefs.edit().putLong("current_session_id", sessionId).apply()
    }
    
    private fun loadSavedModel() {
        viewModelScope.launch {
            val savedModelName = prefs.getString("selected_model", null)
            Log.d(TAG, "loadSavedModel: savedModelName=$savedModelName, modelsList.size=${modelsList.size}")
            if (savedModelName != null) {
                val savedModel = modelsList.find { it.name == savedModelName }
                Log.d(TAG, "loadSavedModel: found model=$savedModel")
                if (savedModel != null) {
                    selectModel(savedModel)
                }
            }
        }
    }
    
    /**
     * Select and load model
     * Like F2/I method h() which calls F2/C coroutine
     */
    fun selectModel(model: Model) {
        Log.d(TAG, "selectModel: ${model.name}")
        _selectedModel.value = model
        prefs.edit().putString("selected_model", model.name).apply()
        
        viewModelScope.launch {
            val context = getApplication<Application>()
            val isDownloaded = ModelDownloadService.isModelDownloaded(context, model.filename)
            Log.d(TAG, "selectModel: isDownloaded=$isDownloaded for ${model.filename}")
            
            if (isDownloaded) {
                // Show loading while model loads
                _isLoading.value = true
                
                // Get the full path to the model file
                val modelsDir = ModelDownloadService.getModelsDir(context)
                val modelFile = java.io.File(modelsDir, model.filename)
                Log.d(TAG, "selectModel: loading from ${modelFile.absolutePath}")
                loadModel(context, modelFile.absolutePath)
                
                _isLoading.value = false
            } else {
                Log.w(TAG, "Model not downloaded")
            }
        }
    }
    
    /**
     * Load LLM model - recreated from F2/k.smali invokeSuspend()
     * 
     * Key settings from decompiled:
     * - Backend: CPU
     * - MaxNumImages: 2 (for multimodal support)
     * - MaxTokens: 4096 (0x1000)
     * - MaxTopK: 60 (0x3c)
     */
    suspend fun loadModel(context: Context, modelPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Close existing inference (like F2/k.smali lines 151-158)
                try {
                    llmInference?.close()
                } catch (e: Exception) {
                    // Ignore
                }
                
                // Set loading state
                _isModelLoaded.value = false
                
                // KEEP xnnpack cache - it speeds up subsequent loads significantly!
                // The cache stores optimized kernels for the device's CPU
                val cacheDir = File(context.cacheDir, "llm_cache")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                
                Log.d(TAG, "Creating LlmInferenceOptions with CPU backend and cache...")
                
                // Build options - CPU is more compatible across devices
                // OPTIMIZATION: Reduced maxTokens for faster responses
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setPreferredBackend(LlmInference.Backend.CPU)  // CPU for compatibility
                    .setMaxNumImages(2)      // v5 = 0x2
                    .setMaxTokens(1024)      // Reduced from 4096 for faster responses
                    .setMaxTopK(40)          // Reduced from 60
                    .build()
                
                Log.d(TAG, "Creating LlmInference...")
                
                // Create inference (like F2/k.smali lines 247-253)
                llmInference = LlmInference.createFromOptions(context, options)
                
                _isModelLoaded.value = true
                Log.d(TAG, "Model loaded successfully")
                true
                
            } catch (e: Exception) {
                // Like F2/k.smali catch block lines 298-312
                Log.e(TAG, "Failed to create LlmInference: ${e.message}", e)
                _isModelLoaded.value = false
                false
            }
        }
    }
    
    /**
     * Create new chat session
     */
    fun createNewSession(title: String) {
        viewModelScope.launch {
            // Close existing LLM session
            llmSession?.close()
            llmSession = null
            
            val session = ChatSession(
                title = title,
                timestamp = System.currentTimeMillis()
            )
            val sessionId = chatSessionDao.insertSession(session)
            val newSession = session.copy(id = sessionId)
            _currentSession.value = newSession
            saveCurrentSession(newSession.id)
            loadMessagesForSession(newSession.id)
            loadChatSessions()
        }
    }
    
    fun selectSession(session: ChatSession) {
        llmSession?.close()
        llmSession = null
        
        _currentSession.value = session
        saveCurrentSession(session.id)
        viewModelScope.launch {
            loadMessagesForSession(session.id)
        }
    }
    
    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            val sessions = chatSessionDao.getAllSessions()
            val session = sessions.find { it.id == sessionId }
            if (session != null) {
                _currentSession.value = session
                saveCurrentSession(sessionId)
                loadMessagesForSession(sessionId)
            }
        }
    }
    
    private suspend fun loadMessagesForSession(sessionId: Long) {
        val messages = chatMessageDao.getMessagesForSession(sessionId)
        _messages.value = messages
    }
    
    fun updateInputText(text: String) {
        _inputText.value = text
    }
    
    fun setSelectedImage(uri: android.net.Uri?) {
        _selectedImageUri.value = uri
    }
    
    fun clearSelectedImage() {
        _selectedImageUri.value = null
    }
    
    /**
     * Check if current model supports vision
     */
    fun isVisionModel(): Boolean {
        return _selectedModel.value?.isVision == true
    }
    
    /**
     * Send message and generate response
     * Like F2/I method m() which uses F2/x.smali for generation
     */
    fun sendMessage() {
        val input = _inputText.value.trim()
        val imageUri = _selectedImageUri.value
        
        // Allow sending with just an image for vision models
        if (input.isEmpty() && imageUri == null) return
        
        // Check if model is loaded BEFORE doing anything
        if (llmInference == null) {
            Log.d(TAG, "sendMessage blocked: model not loaded")
            return
        }
        
        Log.d(TAG, "sendMessage: ${input.take(50)}...")
        
        // Capture the image URI before clearing
        val currentImageUri = _selectedImageUri.value
        
        viewModelScope.launch {
            // Auto-create session if none exists
            var session = _currentSession.value
            if (session == null) {
                val title = if (input.isNotEmpty()) input.take(30) + if (input.length > 30) "..." else "" else "Image Chat"
                val newSession = ChatSession(
                    title = title,
                    timestamp = System.currentTimeMillis()
                )
                val sessionId = chatSessionDao.insertSession(newSession)
                session = newSession.copy(id = sessionId)
                _currentSession.value = session
                saveCurrentSession(session.id)
                loadChatSessions()
            }
            
            // Build message text (include image indicator if present)
            val messageText = if (currentImageUri != null && input.isNotEmpty()) {
                "📷 $input"
            } else if (currentImageUri != null) {
                "📷 [Image]"
            } else {
                input
            }
            
            // Save user message (sender = "You" like decompiled)
            val userMessage = ChatMessage(
                sessionId = session.id,
                sender = "You",
                message = messageText,
                timestamp = System.currentTimeMillis()
            )
            chatMessageDao.insertMessage(userMessage)
            
            // Clear input and image, set loading
            _inputText.value = ""
            _selectedImageUri.value = null
            _isLoading.value = true
            loadMessagesForSession(session.id)
            
            // Create AI placeholder message
            val aiMessage = ChatMessage(
                sessionId = session.id,
                sender = "Assistant",
                message = "...",
                timestamp = System.currentTimeMillis()
            )
            val aiMessageId = chatMessageDao.insertMessage(aiMessage)
            loadMessagesForSession(session.id)
            
            // Generate response (pass image URI for vision models)
            generateResponse(input.ifEmpty { "What is in this image?" }, session.id, aiMessageId, aiMessage, currentImageUri)
        }
    }
    
    /**
     * Generate response using LlmInferenceSession
     * Recreated from F2/x.smali - the main chat generation logic
     * 
     * Session options from F2/x.smali lines 622-654:
     * - GraphOptions.setEnableVisionModality(false/true based on model)
     * - TopK: 40 (0x28)
     * - TopP: 0.9f (0x3f666666)
     * 
     * OPTIMIZATION: Limit history to last 6 messages (3 exchanges) for faster inference
     */
    private fun generateResponse(
        userMessage: String,
        sessionId: Long,
        aiMessageId: Long,
        aiMessage: ChatMessage,
        imageUri: android.net.Uri? = null
    ) {
        // Cancel previous job (like F2/I field y handling)
        currentJob?.cancel()
        
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val inference = llmInference ?: throw IllegalStateException("Model not loaded")
                val context = getApplication<Application>()
                
                // Check if this is a vision model and we have an image
                val isVision = _selectedModel.value?.isVision == true && imageUri != null
                
                // Build conversation history from database (like F2/x.smali)
                // OPTIMIZATION: Only use last 6 messages to reduce context size
                val allMessages = chatMessageDao.getMessagesForSession(sessionId)
                val historyMessages = allMessages.takeLast(7) // 6 history + 1 current
                
                Log.d(TAG, "Using ${historyMessages.size - 1} history messages (of ${allMessages.size - 1} total)")
                Log.d(TAG, "Vision mode: $isVision, imageUri: $imageUri")
                
                // Create session options - enable vision modality if we have an image
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(isVision)
                            .build()
                    )
                    .setTopK(20)      // Reduced from 40 for faster sampling
                    .setTopP(0.8f)    // Slightly reduced for faster convergence
                    .build()
                
                // Close old session, create new (like F2/x.smali lines 659-667)
                llmSession?.close()
                llmSession = LlmInferenceSession.createFromOptions(inference, sessionOptions)
                
                val session = llmSession ?: throw IllegalStateException("Failed to create session")
                
                // Add system prompt with tools if applicable
                val fullPrompt = getFullSystemPrompt()
                if (fullPrompt.isNotEmpty()) {
                    session.addQueryChunk("System: $fullPrompt\n")
                }
                
                // Add history to session (like F2/x.smali message iteration)
                for (msg in historyMessages.dropLast(1)) {  // Exclude the current user message
                    val text = msg.message.trim()
                    if (text.isEmpty() || text == "..." || text.startsWith("❌")) continue
                    
                    if (msg.sender == "You") {
                        session.addQueryChunk(text)
                    } else if (msg.sender == "Assistant") {
                        session.addQueryChunk("Assistant: $text")
                    }
                }
                
                // Add current user message
                session.addQueryChunk(userMessage)
                
                // Add image if present (for vision models)
                if (isVision && imageUri != null) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(imageUri)
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        
                        if (bitmap != null) {
                            // Scale down large images to avoid memory issues
                            val maxSize = 1024
                            val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                                val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
                                android.graphics.Bitmap.createScaledBitmap(
                                    bitmap,
                                    (bitmap.width * scale).toInt(),
                                    (bitmap.height * scale).toInt(),
                                    true
                                )
                            } else {
                                bitmap
                            }
                            
                            // Convert bitmap to MPImage
                            val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(scaledBitmap).build()
                            session.addImage(mpImage)
                            Log.d(TAG, "Added image to session: ${scaledBitmap.width}x${scaledBitmap.height}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add image: ${e.message}", e)
                    }
                }
                
                // Generate response with streaming
                val responseBuilder = StringBuilder()
                
                // Check if this is a tools-enabled model
                val isToolsModel = _selectedModel.value?.isTools == true
                
                session.generateResponseAsync { partialResult, done ->
                    responseBuilder.append(partialResult)
                    
                    // Update message in real-time
                    viewModelScope.launch {
                        val updated = aiMessage.copy(id = aiMessageId, message = responseBuilder.toString())
                        chatMessageDao.updateMessage(updated)
                        loadMessagesForSession(sessionId)
                    }
                }.get()  // Wait for completion
                
                // Final update with tool execution
                withContext(Dispatchers.Main) {
                    var finalResponse = responseBuilder.toString().ifEmpty { "No response generated" }
                    Log.d(TAG, "Generation complete: ${finalResponse.take(100)}...")
                    
                    // Check for tool calls if tools-enabled model
                    if (isToolsModel) {
                        val (wasToolCall, result) = toolExecutor.parseAndExecute(finalResponse)
                        if (wasToolCall) {
                            finalResponse = result
                            Log.d(TAG, "Tool executed: $result")
                        }
                    }
                    
                    val finalMessage = aiMessage.copy(id = aiMessageId, message = finalResponse)
                    chatMessageDao.updateMessage(finalMessage)
                    loadMessagesForSession(sessionId)
                    _isLoading.value = false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Generation error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errorMsg = aiMessage.copy(id = aiMessageId, message = "❌ Error: ${e.message}")
                    chatMessageDao.updateMessage(errorMsg)
                    loadMessagesForSession(sessionId)
                    _isLoading.value = false
                }
            }
        }
    }
    
    /**
     * Cancel current generation
     * Like F2/I method m() cancellation logic
     */
    fun cancelGeneration(session: LlmInferenceSession?) {
        _isLoading.value = false
        currentJob?.cancel()
        currentJob = null
        
        viewModelScope.launch {
            session?.close()
        }
    }
    
    /**
     * Delete chat session
     */
    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            chatSessionDao.deleteSession(session)
            if (_currentSession.value?.id == session.id) {
                _currentSession.value = null
                _messages.value = emptyList()
            }
            loadChatSessions()
        }
    }
    
    /**
     * Word count utility - like F2/I static method b()
     * Uses regex "\\s+" to split text
     */
    fun countWords(text: String): Int {
        val pattern = "\\s+".toRegex()
        return if (pattern.containsMatchIn(text)) {
            text.split(pattern).size
        } else {
            if (text.isEmpty()) 0 else 1
        }
    }
    
    // Download state setters
    fun setDownloadProgress(progress: Int) {
        _downloadProgress.value = progress
    }
    
    fun setDownloading(downloading: Boolean) {
        _isDownloading.value = downloading
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun handleDownloadCancelled() {
        _isDownloading.value = false
        _downloadProgress.value = 0
    }
    
    fun cleanup(modelType: ModelType) {
        try {
            llmSession?.close()
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error: ${e.message}")
        }
        llmSession = null
        llmInference = null
    }
    
    fun setLoaded(loaded: Boolean) {
        _isModelLoaded.value = loaded
    }
    
    /**
     * onCleared - cleanup resources
     * Like F2/I.smali onCleared() method lines 906-932
     */
    override fun onCleared() {
        super.onCleared()
        try {
            // Close session first (like F2/I lines 911-918)
            llmSession?.close()
            llmSession = null
            
            // Close inference (like F2/I lines 920-926)
            llmInference?.close()
            llmInference = null
        } catch (e: Exception) {
            // Ignore exceptions during cleanup
        }
    }
}
