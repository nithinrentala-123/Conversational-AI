package com.niquewrld.conversationalai.Room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM ChatSession ORDER BY timestamp DESC")
    suspend fun getAllSessions(): List<ChatSession>

    @Insert
    suspend fun insertSession(session: ChatSession): Long

    @Delete
    suspend fun deleteSession(session: ChatSession)

    @Query("DELETE FROM ChatSession WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM ChatSession")
    suspend fun deleteAllSessions()

    @Query("UPDATE ChatSession SET title = :newTitle WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: Long, newTitle: String)
}
