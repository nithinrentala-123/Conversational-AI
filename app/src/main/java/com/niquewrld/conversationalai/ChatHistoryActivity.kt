package com.niquewrld.conversationalai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.niquewrld.conversationalai.Room.AppDatabase
import com.niquewrld.conversationalai.Room.ChatMessage
import com.niquewrld.conversationalai.Room.ChatSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatHistoryActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: ChatHistoryAdapter
    
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_history)
        
        database = AppDatabase.getInstance(this)

        initViews()
        setupToolbar()
        setupRecyclerView()
        loadChatHistory()
    }
    
    override fun onResume() {
        super.onResume()
        loadChatHistory()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.history_recycler_view)
        emptyState = findViewById(R.id.empty_state)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatHistoryAdapter(
            onItemClick = { session ->
                // Open chat with this session
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("session_id", session.id)
                }
                startActivity(intent)
                finish()
            },
            onDeleteClick = { session ->
                showDeleteConfirmDialog(session)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadChatHistory() {
        lifecycleScope.launch {
            val sessions = database.sessionDao().getAllSessions()
            
            if (sessions.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                
                // Load preview for each session
                val sessionsWithPreview = sessions.map { session ->
                    val lastMessage = database.messageDao().getLastMessageForSession(session.id)
                    SessionWithPreview(session, lastMessage)
                }
                adapter.submitList(sessionsWithPreview)
            }
        }
    }

    private fun showDeleteConfirmDialog(session: ChatSession) {
        AlertDialog.Builder(this, R.style.Theme_ConversationalAI_Dialog)
            .setTitle("Delete Chat")
            .setMessage("Are you sure you want to delete \"${session.title}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSession(session)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSession(session: ChatSession) {
        lifecycleScope.launch {
            // Delete all messages in session
            database.messageDao().deleteMessagesForSession(session.id)
            // Delete session
            database.sessionDao().deleteSession(session)
            // Reload
            loadChatHistory()
        }
    }
}

data class SessionWithPreview(
    val session: ChatSession,
    val lastMessage: ChatMessage?
)

class ChatHistoryAdapter(
    private val onItemClick: (ChatSession) -> Unit,
    private val onDeleteClick: (ChatSession) -> Unit
) : RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder>() {

    private var items = listOf<SessionWithPreview>()

    fun submitList(list: List<SessionWithPreview>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.session_title)
        private val previewText: TextView = itemView.findViewById(R.id.session_preview)
        private val dateText: TextView = itemView.findViewById(R.id.session_date)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)

        fun bind(item: SessionWithPreview) {
            titleText.text = item.session.title
            previewText.text = item.lastMessage?.message ?: "No messages"
            dateText.text = formatDate(item.session.timestamp)

            itemView.setOnClickListener {
                onItemClick(item.session)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(item.session)
            }
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}
