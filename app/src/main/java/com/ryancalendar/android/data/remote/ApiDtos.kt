package com.ryancalendar.android.data.remote

import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.model.CalendarEvent
import com.ryancalendar.android.domain.model.HolidayEntry
import com.ryancalendar.android.domain.model.LedgerMeta
import com.ryancalendar.android.domain.model.UserProfile
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class UserDto(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val role: String = "user",
    val isActive: Boolean = true,
    val icsToken: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastLoginAt: String? = null
) {
    fun toDomain() = UserProfile(
        id = id,
        username = username,
        displayName = displayName,
        role = role,
        isActive = isActive,
        icsToken = icsToken,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastLoginAt = lastLoginAt
    )
}

@Serializable
data class MeResponse(
    val user: UserDto? = null
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val user: UserDto
)

@Serializable
data class OkResponse(
    val ok: Boolean = true
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class ResetIcsResponse(
    val icsToken: String
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val color: String
) {
    fun toDomain(index: Int = 0) = CalendarCategory(
        id = id,
        name = name,
        color = color,
        sortOrder = index
    )
}

@Serializable
data class LedgerMetaDto(
    val projectName: String = "",
    val stage: String = "",
    val level: String = "",
    val role: String = ""
) {
    fun toDomain() = LedgerMeta(
        projectName = projectName,
        stage = stage,
        level = level,
        role = role
    )
}

@Serializable
data class EventDto(
    val id: String,
    val title: String,
    val categoryId: String = "",
    val startDate: String,
    val endDate: String = "",
    val allDay: Boolean = true,
    val startTime: String = "",
    val endTime: String = "",
    val notes: String = "",
    val source: String = "manual",
    val ledgerKey: String = "",
    val ledgerMeta: LedgerMetaDto? = null,
    val evalProjectId: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun toDomain() = CalendarEvent(
        id = id,
        title = title,
        categoryId = categoryId,
        startDate = startDate,
        endDate = if (endDate > startDate) endDate else "",
        allDay = allDay,
        startTime = if (allDay) "" else startTime.ifBlank { "09:00" },
        endTime = if (allDay) "" else endTime.ifBlank { "18:00" },
        notes = notes,
        source = source,
        ledgerKey = ledgerKey,
        ledgerMeta = ledgerMeta?.toDomain(),
        evalProjectId = evalProjectId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@Serializable
data class StateResponse(
    val version: Int = 1,
    val categories: List<CategoryDto> = emptyList(),
    val events: List<EventDto> = emptyList(),
    val updatedAt: String = ""
)

@Serializable
data class HolidayEntryDto(
    val date: String,
    val year: Int = 0,
    val type: String,
    val name: String = ""
) {
    fun toDomain(): HolidayEntry {
        val resolvedYear = if (year > 0) year else date.take(4).toIntOrNull() ?: 0
        return HolidayEntry(
            date = date,
            year = resolvedYear,
            type = type,
            name = name
        )
    }
}

@Serializable
data class HolidaysResponse(
    val years: List<Int> = emptyList(),
    val entries: List<HolidayEntryDto> = emptyList()
)

@Serializable
data class CategoryCreateRequest(
    val name: String,
    val color: String
)

@Serializable
data class CategoryPatchRequest(
    val name: String? = null,
    val color: String? = null
)

@Serializable
data class CategoryMutationResponse(
    val category: CategoryDto? = null,
    val categories: List<CategoryDto> = emptyList()
)

@Serializable
data class ReorderCategoriesRequest(
    val orderedIds: List<String>
)

@Serializable
data class CategoriesListResponse(
    val categories: List<CategoryDto> = emptyList()
)

@Serializable
data class EventUpsertRequest(
    val title: String,
    val categoryId: String,
    val startDate: String,
    val endDate: String,
    val allDay: Boolean,
    val startTime: String,
    val endTime: String,
    val notes: String
)

@Serializable
data class EventResponse(
    val event: EventDto,
    val evalProjectUpdated: Boolean = false
)

@Serializable
data class DeleteEventResponse(
    val ok: Boolean = true,
    val deletedEvalProjectId: String = ""
)
