package com.ryancalendar.android.data.remote

import com.ryancalendar.android.domain.eval.EvalProject
import com.ryancalendar.android.domain.eval.EvalSettings
import com.ryancalendar.android.domain.eval.EvalTravel
import kotlinx.serialization.Serializable

@Serializable
data class EvalProjectDto(
    val id: String,
    val name: String = "",
    val level: String = "",
    val role: String = "",
    val stage: String = "",
    val start: String = "",
    val end: String = "",
    val status: String = "待开始",
    val settle: String = "未结",
    val note: String = "",
    val feeManual: Double? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun toDomain() = EvalProject(
        id = id,
        name = name,
        level = level,
        role = role,
        stage = stage,
        start = start,
        end = end,
        status = status,
        settle = settle,
        note = note,
        feeManual = feeManual,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@Serializable
data class EvalTravelDto(
    val id: String,
    val date: String = "",
    val name: String = "",
    val stage: String = "",
    val level: String = "",
    val planStart: String = "",
    val planEnd: String = "",
    val planText: String = "",
    val cat: String = "打车",
    val catCustom: String = "",
    val amount: Double = 0.0,
    val detail: String = "",
    val reimb: String = "未报",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun toDomain() = EvalTravel(
        id = id,
        date = date,
        name = name,
        stage = stage,
        level = level,
        planStart = planStart,
        planEnd = planEnd,
        planText = planText,
        cat = cat,
        catCustom = catCustom,
        amount = amount,
        detail = detail,
        reimb = reimb,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@Serializable
data class EvalSettingsDto(
    val daysRules: Map<String, Map<String, Int?>> = emptyMap(),
    val feeRates: Map<String, Map<String, Double?>> = emptyMap()
) {
    fun toDomain(): EvalSettings {
        if (daysRules.isEmpty() && feeRates.isEmpty()) return EvalSettings.Default
        return EvalSettings(
            daysRules = daysRules.ifEmpty { EvalSettings.Default.daysRules },
            feeRates = feeRates.ifEmpty { EvalSettings.Default.feeRates }
        )
    }

    companion object {
        fun from(s: EvalSettings) = EvalSettingsDto(daysRules = s.daysRules, feeRates = s.feeRates)
    }
}

@Serializable
data class EvalStateResponse(
    val projects: List<EvalProjectDto> = emptyList(),
    val travels: List<EvalTravelDto> = emptyList(),
    val settings: EvalSettingsDto = EvalSettingsDto(),
    val mergeNames: Boolean = true,
    val updatedAt: String = ""
)

@Serializable
data class EvalProjectMutationResponse(
    val project: EvalProjectDto? = null,
    val affectedCount: Int = 0,
    val evalState: EvalStateResponse? = null,
    val calendarState: StateResponse? = null
)

@Serializable
data class EvalProjectBatchResponse(
    val updatedCount: Int = 0,
    val projectCount: Int = 0,
    val evalState: EvalStateResponse? = null,
    val calendarState: StateResponse? = null
)

@Serializable
data class EvalProjectDeleteResponse(
    val ok: Boolean = true,
    val evalState: EvalStateResponse? = null,
    val calendarState: StateResponse? = null
)

@Serializable
data class EvalTravelMutationResponse(
    val travel: EvalTravelDto? = null,
    val updatedCount: Int = 0,
    val ok: Boolean = true,
    val evalState: EvalStateResponse? = null
)

@Serializable
data class EvalPreferencesRequest(val mergeNames: Boolean)

@Serializable
data class EvalPreferencesResponse(val mergeNames: Boolean = true)
