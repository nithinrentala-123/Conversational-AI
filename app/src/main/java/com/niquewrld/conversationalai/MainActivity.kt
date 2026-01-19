package com.niquewrld.conversationalai

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.niquewrld.conversationalai.Room.ChatMessage
import com.niquewrld.conversationalai.service.ModelDownloadService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var emptyState: LinearLayout
    private lateinit var modelSelector: LinearLayout
    private lateinit var selectedModelText: android.widget.TextView
    
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var cancelReceiver: CancelDownloadReceiver
    
    // Image picker views
    private lateinit var imageButton: ImageButton
    private lateinit var imagePreviewContainer: View
    private lateinit var imagePreview: android.widget.ImageView
    private lateinit var removeImageButton: ImageButton
    
    // Pending model download (waiting for storage permission)
    private var pendingModelDownload: Model? = null
    
    // Permission launcher for storage
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingModelDownload?.let { startModelDownload(it) }
        } else {
            Toast.makeText(this, "Storage permission required for downloads", Toast.LENGTH_LONG).show()
        }
        pendingModelDownload = null
    }
    
    // Permission launcher for notifications (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notifications disabled - you won't see download progress", Toast.LENGTH_LONG).show()
        }
    }
    
    // Launcher for MANAGE_EXTERNAL_STORAGE setting
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            pendingModelDownload?.let { startModelDownload(it) }
        } else {
            Toast.makeText(this, "Storage access required for downloads", Toast.LENGTH_LONG).show()
        }
        pendingModelDownload = null
    }
    
    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            mainViewModel.setSelectedImage(uri)
            showImagePreview(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check storage space first
        checkStorageSpace()

        // Initialize ViewModel
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        mainViewModel.initialize()

        // Initialize views
        initViews()
        setupToolbar()
        setupDrawer()
        setupChat()
        setupModelSelector()
        setupObservers()
        
        // Check if we're opening a specific session from chat history
        val sessionId = intent.getLongExtra("session_id", -1L)
        if (sessionId != -1L) {
            mainViewModel.loadSession(sessionId)
        }
        
        // Request permissions on start
        requestNotificationPermission()
        requestStoragePermission()
        
        // Resume any incomplete downloads
        resumeIncompleteDownloads()
        
        // Register cancel download receiver
        cancelReceiver = CancelDownloadReceiver()
        val intentFilter = IntentFilter().apply {
            addAction("CANCEL_DOWNLOAD")
            addAction("PAUSE_DOWNLOAD")
            addAction("RESUME_DOWNLOAD")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(cancelReceiver, intentFilter)
        }
    }
    
    private fun checkStorageSpace() {
        try {
            val stat = android.os.StatFs(filesDir.path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val availableMB = availableBytes / (1024 * 1024)
            
            if (availableMB < 100) { // Less than 100MB free
                AlertDialog.Builder(this)
                    .setTitle("Low Storage")
                    .setMessage("Your device is running low on storage space (${availableMB}MB free). The app may not function properly. Please free up some space.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        } catch (e: Exception) {
            // Ignore errors in storage check
        }
    }
    
    private fun resumeIncompleteDownloads() {
        // Check if we have storage permission first
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasPermission) return
        
        // Check for .part files (incomplete downloads)
        val modelsDir = ModelDownloadService.getModelsDir(this)
        if (!modelsDir.exists()) return
        
        val partFiles = modelsDir.listFiles { file -> file.name.endsWith(".part") }
        if (partFiles.isNullOrEmpty()) return
        
        // Find the model info for the incomplete download
        val models = mainViewModel.modelsList
        for (partFile in partFiles) {
            val modelFilename = partFile.name.removeSuffix(".part")
            val model = models.find { it.filename == modelFilename }
            
            if (model != null) {
                // Resume download
                Toast.makeText(this, "Resuming download: ${model.name}", Toast.LENGTH_SHORT).show()
                startModelDownload(model)
                break // Only resume one at a time
            }
        }
    }
    
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - need MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 - need WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    
    private fun requestNotificationPermission() {
        // Android 13+ (API 33+) requires runtime permission for notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)
        chatRecyclerView = findViewById(R.id.chat_recycler_view)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)
        emptyState = findViewById(R.id.empty_state)
        modelSelector = findViewById(R.id.model_selector)
        selectedModelText = findViewById(R.id.selected_model_text)
        
        // Image picker views
        imageButton = findViewById(R.id.image_button)
        imagePreviewContainer = findViewById(R.id.image_preview_container)
        imagePreview = findViewById(R.id.image_preview)
        removeImageButton = findViewById(R.id.remove_image_button)
        
        // Set drawer status bar background to surface color
        drawerLayout.setStatusBarBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.surface))
        
        // Setup image picker button click
        imageButton.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
        
        // Setup remove image button click
        removeImageButton.setOnClickListener {
            hideImagePreview()
            mainViewModel.clearSelectedImage()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupModelSelector() {
        modelSelector.setOnClickListener {
            showModelSelectionDialog()
        }
    }

    private fun isModelDownloaded(model: Model): Boolean {
        // Check external storage location (persists after app uninstall)
        return ModelDownloadService.isModelDownloaded(this, model.filename)
    }

    private fun showModelSelectionDialog() {
        val models = mainViewModel.modelsList
        val currentSelectedModel = mainViewModel.selectedModel.value
        
        if (models.isEmpty()) {
            Toast.makeText(this, "No models available", Toast.LENGTH_SHORT).show()
            return
        }

        // Create display names with download status
        val modelDisplayNames = models.map { model ->
            if (isModelDownloaded(model)) {
                "${model.name} ✓"
            } else {
                "${model.name} (${model.downloadSize})"
            }
        }.toTypedArray()
        
        val currentIndex = models.indexOfFirst { it.name == currentSelectedModel?.name }

        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_ConversationalAI_Dialog)
            .setTitle("Select Model")
            .setSingleChoiceItems(modelDisplayNames, currentIndex) { dialog, which ->
                val selected = models[which]
                
                if (isModelDownloaded(selected)) {
                    // Model is downloaded, select it
                    mainViewModel.selectModel(selected)
                    selectedModelText.text = selected.name
                    dialog.dismiss()
                } else {
                    // Model needs to be downloaded
                    dialog.dismiss()
                    showDownloadConfirmDialog(selected)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDownloadConfirmDialog(model: Model) {
        AlertDialog.Builder(this, R.style.Theme_ConversationalAI_Dialog)
            .setTitle("Download Model")
            .setMessage("${model.name}\n\nSize: ${model.downloadSize}\n\n${model.description}\n\nModels are stored in NiqueWrld/models and can be shared with other apps.\n\nDownload this model?")
            .setPositiveButton("Download") { _, _ ->
                checkStoragePermissionAndDownload(model)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun checkStoragePermissionAndDownload(model: Model) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - need MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                startModelDownload(model)
            } else {
                pendingModelDownload = model
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 - need WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startModelDownload(model)
            } else {
                pendingModelDownload = model
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            // Older Android - no runtime permission needed
            startModelDownload(model)
        }
    }

    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.app_name, R.string.app_name
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_new_chat -> {
                    mainViewModel.createNewSession("New Chat")
                    Toast.makeText(this, "New chat created", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, ChatHistoryActivity::class.java))
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, ChatSettingActivity::class.java))
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupChat() {
        chatAdapter = ChatAdapter()
        chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }

        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                mainViewModel.updateInputText(message)
                mainViewModel.sendMessage()
                messageInput.text.clear()
            }
        }
    }

    private fun setupObservers() {
        // Observe selected model
        lifecycleScope.launch {
            mainViewModel.selectedModel.collectLatest { model ->
                selectedModelText.text = model?.name ?: "Select Model"
                
                // Show/hide image button based on vision model
                if (model?.isVision == true) {
                    imageButton.visibility = View.VISIBLE
                } else {
                    imageButton.visibility = View.GONE
                    // Clear any selected image if switching away from vision model
                    hideImagePreview()
                    mainViewModel.clearSelectedImage()
                }
            }
        }
        
        // Observe model loaded state - enable/disable input
        lifecycleScope.launch {
            mainViewModel.isModelLoaded.collectLatest { isLoaded ->
                messageInput.isEnabled = isLoaded
                sendButton.isEnabled = isLoaded
                imageButton.isEnabled = isLoaded
                messageInput.hint = if (isLoaded) {
                    if (mainViewModel.isVisionModel()) "Type a message or add an image..." else "Type a message..."
                } else if (mainViewModel.isLoading.value) {
                    "Loading model..."
                } else {
                    "Select a model first"
                }
                sendButton.alpha = if (isLoaded) 1.0f else 0.5f
                imageButton.alpha = if (isLoaded) 1.0f else 0.5f
            }
        }
        
        // Observe loading state for hint updates
        lifecycleScope.launch {
            mainViewModel.isLoading.collectLatest { isLoading ->
                if (!mainViewModel.isModelLoaded.value) {
                    messageInput.hint = if (isLoading) {
                        "Loading model..."
                    } else {
                        "Select a model first"
                    }
                }
            }
        }
        
        // Observe messages
        lifecycleScope.launch {
            mainViewModel.messages.collectLatest { messages ->
                chatAdapter.submitList(messages) {
                    // Scroll to bottom after list is updated
                    if (messages.isNotEmpty()) {
                        chatRecyclerView.post {
                            chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                        }
                    }
                }
                
                // Show/hide empty state
                if (messages.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    chatRecyclerView.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    chatRecyclerView.visibility = View.VISIBLE
                }
            }
        }
        
        // Observe errors
        lifecycleScope.launch {
            mainViewModel.errorMessage.collectLatest { error ->
                error?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    mainViewModel.clearError()
                }
            }
        }
    }

    private fun startModelDownload(model: Model) {
        // Store download info for resume capability
        DownloadManager.storeDownloadInfo(model.name, model.filename, model.link)
        
        val intent = Intent(this, ModelDownloadService::class.java).apply {
            putExtra("modelName", model.name)
            putExtra("modelUrl", model.link)
            putExtra("modelFilename", model.filename)
        }
        startForegroundService(intent)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(cancelReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
    
    private fun showImagePreview(uri: Uri) {
        imagePreviewContainer.visibility = View.VISIBLE
        imagePreview.setImageURI(uri)
    }
    
    private fun hideImagePreview() {
        imagePreviewContainer.visibility = View.GONE
        imagePreview.setImageDrawable(null)
    }
}

// Chat Adapter
class ChatAdapter : androidx.recyclerview.widget.ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
    }
) {
    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).sender == "You") VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_USER) R.layout.item_message_user else R.layout.item_message_ai
        val view = android.view.LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: android.widget.TextView = itemView.findViewById(R.id.message_text)
        
        fun bind(message: ChatMessage) {
            messageText.text = message.message
        }
    }
}
