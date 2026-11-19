package com.ryancalendar.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT * FROM events ORDER BY startDate ASC, allDay DESC, startTime ASC")
    fun observeEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY startDate ASC, allDay DESC, startTime ASC")
    suspend fun getEventsOnce(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): EventEntity?

    @Query("SELECT * FROM holidays ORDER BY date ASC")
    fun observeHolidays(): Flow<List<HolidayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(items: List<EventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(item: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEventById(id: String)

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM events")
    suspend fun clearEvents()

    @Query("DELETE FROM holidays")
    suspend fun clearHolidays()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHolidays(items: List<HolidayEntity>)

    @Transaction
    suspend fun replaceState(categories: List<CategoryEntity>, events: List<EventEntity>) {
        clearCategories()
        clearEvents()
        if (categories.isNotEmpty()) upsertCategories(categories)
        if (events.isNotEmpty()) upsertEvents(events)
    }

    @Transaction
    suspend fun replaceCategories(categories: List<CategoryEntity>) {
        clearCategories()
        if (categories.isNotEmpty()) upsertCategories(categories)
    }

    @Transaction
    suspend fun replaceHolidays(holidays: List<HolidayEntity>) {
        clearHolidays()
        if (holidays.isNotEmpty()) upsertHolidays(holidays)
    }

    @Transaction
    suspend fun clearAllUserData() {
        clearCategories()
        clearEvents()
    }
}
