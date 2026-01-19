package com.niquewrld.conversationalai.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.niquewrld.conversationalai.R
import com.niquewrld.conversationalai.Room.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_USER) {
            R.layout.item_message_ai
        } else {
            R.layout.item_message_user
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).sender == "user") VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.message_text)
        private val messageTime: TextView = itemView.findViewById(R.id.message_time)

        fun bind(message: ChatMessage) {
            messageText.text = message.message
            
            // Format timestamp
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            messageTime.text = dateFormat.format(Date(message.timestamp))
            
            // Adjust layout gravity based on sender
            val layoutParams = itemView.layoutParams as? RecyclerView.LayoutParams
            layoutParams?.let {
                if (message.sender == "user") {
                    it.marginStart = 64.dpToPx()
                    it.marginEnd = 8.dpToPx()
                } else {
                    it.marginStart = 8.dpToPx()
                    it.marginEnd = 64.dpToPx()
                }
                itemView.layoutParams = it
            }
        }
        
        private fun Int.dpToPx(): Int {
            return (this * itemView.context.resources.displayMetrics.density).toInt()
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
    }
}
