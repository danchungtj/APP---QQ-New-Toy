package com.example.qqnewtoy.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE date = :date")
    fun getNoteByDate(date: Date): Flow<Note?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Delete
    suspend fun delete(note: Note)
} 