package com.example.qqnewtoy.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface ToyDayDao {
    @Query("SELECT * FROM toy_days WHERE date BETWEEN :startDate AND :endDate")
    fun getDaysInRange(startDate: Date, endDate: Date): Flow<List<ToyDay>>

    @Query("SELECT * FROM toy_days")
    suspend fun getAllToyDays(): List<ToyDay>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(toyDay: ToyDay)

    @Delete
    suspend fun delete(toyDay: ToyDay)

    @Query("SELECT * FROM toy_days WHERE date = :date")
    suspend fun getDay(date: Date): ToyDay?
} 