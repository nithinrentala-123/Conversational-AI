package com.niquewrld.conversationalai.Room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM ChatMessage WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessage>

    @Query("SELECT * FROM ChatMessage WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageForSession(sessionId: Long): ChatMessage?

    @Insert
    suspend fun insertMessage(message: ChatMessage): Long
    
    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Insert
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Query("DELETE FROM ChatMessage")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM ChatMessage WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)

    @Query("SELECT title FROM ChatSession WHERE id = :sessionId LIMIT 1")
    suspend fun getTitleForSession(sessionId: Long): String?

    @Query("UPDATE ChatSession SET title = :title WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: Long, title: String)
}
