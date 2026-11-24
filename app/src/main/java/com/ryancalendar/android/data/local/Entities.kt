package com.ryancalendar.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.model.CalendarEvent
import com.ryancalendar.android.domain.model.HolidayEntry
import com.ryancalendar.android.domain.model.LedgerMeta

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val sortOrder: Int
) {
    fun toDomain() = CalendarCategory(
        id = id,
        name = name,
        color = color,
        sortOrder = sortOrder
    )
}

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val categoryId: String,
    val startDate: String,
    val endDate: String,
    val allDay: Boolean,
    val startTime: String,
    val endTime: String,
    val notes: String,
    val source: String,
    val ledgerKey: String,
    val ledgerProjectName: String,
    val ledgerStage: String,
    val ledgerLevel: String,
    val ledgerRole: String,
    val evalProjectId: String,
    val createdAt: String,
    val updatedAt: String
) {
    fun toDomain(): CalendarEvent {
        val meta = LedgerMeta(
            projectName = ledgerProjectName,
            stage = ledgerStage,
            level = ledgerLevel,
            role = ledgerRole
        )
        return CalendarEvent(
            id = id,
            title = title,
            categoryId = categoryId,
            startDate = startDate,
            endDate = endDate,
            allDay = allDay,
            startTime = startTime,
            endTime = endTime,
            notes = notes,
            source = source,
            ledgerKey = ledgerKey,
            ledgerMeta = if ((source == "ledger" || source == "eval") && meta.isNotEmpty) meta else null,
            evalProjectId = evalProjectId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey val date: String,
    val year: Int,
    val type: String,
    val name: String
) {
    fun toDomain() = HolidayEntry(
        date = date,
        year = year,
        type = type,
        name = name
    )
}

fun CalendarCategory.toEntity(order: Int = sortOrder) = CategoryEntity(
    id = id,
    name = name,
    color = color,
    sortOrder = order
)

fun CalendarEvent.toEntity() = EventEntity(
    id = id,
    title = title,
    categoryId = categoryId,
    startDate = startDate,
    endDate = endDate,
    allDay = allDay,
    startTime = startTime,
    endTime = endTime,
    notes = notes,
    source = source,
    ledgerKey = ledgerKey,
    ledgerProjectName = ledgerMeta?.projectName.orEmpty(),
    ledgerStage = ledgerMeta?.stage.orEmpty(),
    ledgerLevel = ledgerMeta?.level.orEmpty(),
    ledgerRole = ledgerMeta?.role.orEmpty(),
    evalProjectId = evalProjectId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun HolidayEntry.toEntity() = HolidayEntity(
    date = date,
    year = year,
    type = type,
    name = name
)
