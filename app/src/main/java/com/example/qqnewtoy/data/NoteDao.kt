package com.example.qqnewtoy.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE date = :date")
    fun getNotesByDate(date: Date): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY date DESC, id DESC")
    suspend fun getAllNotes(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Delete
    suspend fun delete(note: Note)
} 