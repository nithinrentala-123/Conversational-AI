package com.niquewrld.conversationalai.Room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)
