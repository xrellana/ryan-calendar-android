package com.ryancalendar.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        EventEntity::class,
        HolidayEntity::class,
        EvalProjectEntity::class,
        EvalTravelEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun evalDao(): EvalDao

    companion object {
        fun create(context: Context): CalendarDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CalendarDatabase::class.java,
                "ryan_calendar_cache.db"
            ).fallbackToDestructiveMigration().build()
    }
}
