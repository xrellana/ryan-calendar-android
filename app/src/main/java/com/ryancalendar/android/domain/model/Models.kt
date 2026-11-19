package com.ryancalendar.android.domain.model

data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String,
    val isActive: Boolean = true,
    val icsToken: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastLoginAt: String? = null
) {
    val shownName: String
        get() = displayName.ifBlank { username }
}

data class CalendarCategory(
    val id: String,
    val name: String,
    val color: String,
    val sortOrder: Int = 0,
    val isFallback: Boolean = false
) {
    companion object {
        val Fallback = CalendarCategory(
            id = "__fallback__",
            name = "未归类（原类别已删除）",
            color = "#64748B",
            sortOrder = 9999,
            isFallback = true
        )

        val Defaults = listOf(
            CalendarCategory("cat-deadline", "提交截止", "#D93A3A", 0),
            CalendarCategory("cat-assessment", "外出评估", "#D97A12", 1),
            CalendarCategory("cat-consulting", "咨询出差", "#2B6CD4", 2),
            CalendarCategory("cat-rest", "休息", "#2E9E5B", 3)
        )
    }
}

data class LedgerMeta(
    val projectName: String = "",
    val stage: String = "",
    val level: String = "",
    val role: String = ""
) {
    val isNotEmpty: Boolean
        get() = projectName.isNotBlank() || stage.isNotBlank() || level.isNotBlank() || role.isNotBlank()
}

data class CalendarEvent(
    val id: String,
    val title: String,
    val categoryId: String,
    val startDate: String, // YYYY-MM-DD
    val endDate: String = "", // YYYY-MM-DD or empty
    val allDay: Boolean = true,
    val startTime: String = "", // HH:mm
    val endTime: String = "", // HH:mm
    val notes: String = "",
    val source: String = "manual", // manual | ledger
    val ledgerKey: String = "",
    val ledgerMeta: LedgerMeta? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val effectiveEndDate: String
        get() = if (endDate.isNotBlank() && endDate >= startDate) endDate else startDate

    val isMultiDay: Boolean
        get() = endDate.isNotBlank() && endDate > startDate
}

data class HolidayEntry(
    val date: String, // YYYY-MM-DD
    val year: Int,
    val type: String, // holiday | workday
    val name: String
)

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 30
) {
    val formattedTime: String
        get() = "%02d:%02d".format(hour, minute)
}
