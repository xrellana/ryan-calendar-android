package com.ryancalendar.android.data.repository

import com.ryancalendar.android.data.local.EvalDao
import com.ryancalendar.android.data.local.SettingsStore
import com.ryancalendar.android.data.local.toEntity
import com.ryancalendar.android.data.remote.EvalApiService
import com.ryancalendar.android.data.remote.EvalPreferencesRequest
import com.ryancalendar.android.data.remote.EvalStateResponse
import com.ryancalendar.android.data.remote.StateResponse
import com.ryancalendar.android.domain.eval.EvalProject
import com.ryancalendar.android.domain.eval.EvalSnapshot
import com.ryancalendar.android.domain.eval.EvalTravel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate

/** Full form payload of an evaluation project. */
data class ProjectInput(
    val name: String,
    val level: String,
    val role: String,
    val stage: String,
    val start: String,
    val end: String,
    val status: String,
    val settle: String,
    val note: String,
    val feeManual: Double?
)

data class TravelInput(
    val date: String,
    val name: String,
    val stage: String,
    val level: String,
    val planStart: String,
    val planEnd: String,
    val planText: String,
    val cat: String,
    val catCustom: String,
    val amount: Double,
    val detail: String,
    val reimb: String
)

/**
 * Remote-first evaluation data access. Every successful write applies the returned
 * evalState (and calendarState when present) to the local cache, so the calendar and the
 * evaluation screens always reflect the same server transaction.
 */
class EvalRepository(
    private val api: EvalApiService,
    private val dao: EvalDao,
    private val settingsStore: SettingsStore,
    private val calendarRepository: CalendarRepository
) {
    val snapshotFlow: Flow<EvalSnapshot> = combine(
        dao.observeProjects(),
        dao.observeTravels(),
        settingsStore.evalMetaFlow
    ) { projects, travels, meta ->
        EvalSnapshot(
            projects = projects.map { it.toDomain() },
            travels = travels.map { it.toDomain() },
            settings = meta.settings,
            mergeNames = meta.mergeNames,
            loaded = meta.loaded
        )
    }

    val travelMergeFlow: Flow<Boolean> = settingsStore.evalTravelMergeFlow

    /** Device-local date; the server uses it to auto-promote "待开始" projects to "进行中". */
    private fun today(): String = LocalDate.now().toString()

    private suspend fun applyEvalState(state: EvalStateResponse?) {
        if (state == null) return
        dao.replaceAll(
            projects = state.projects.map { it.toDomain().toEntity() },
            travels = state.travels.map { it.toDomain().toEntity() }
        )
        settingsStore.saveEvalMeta(state.settings, state.mergeNames)
    }

    private suspend fun applyCalendarState(state: StateResponse?) {
        if (state != null) calendarRepository.applyCalendarState(state)
    }

    /**
     * Must run before the calendar sync: the auto-promotion done by GET /api/eval/state also
     * rewrites linked event notes but the response does not include the calendar state.
     */
    suspend fun refresh(): ApiResult<Unit> = calendarRepository.safeCall {
        applyEvalState(api.getState(today()))
    }

    // -------------------------------------------------------------------------
    // Projects
    // -------------------------------------------------------------------------
    private fun projectBody(input: ProjectInput, allowUnscheduled: Boolean): JsonObject = buildJsonObject {
        put("name", input.name.trim().take(120))
        put("level", input.level)
        put("role", input.role)
        put("stage", input.stage)
        put("start", input.start)
        put("end", input.end)
        put("status", input.status)
        put("settle", input.settle)
        put("note", input.note.take(1000))
        put("feeManual", input.feeManual?.let { JsonPrimitive(it) } ?: JsonNull)
        if (allowUnscheduled) put("allowUnscheduled", true)
        put("today", today())
    }

    suspend fun createProject(input: ProjectInput): ApiResult<EvalProject?> = calendarRepository.safeCall {
        val res = api.createProject(projectBody(input, allowUnscheduled = false))
        applyEvalState(res.evalState)
        applyCalendarState(res.calendarState)
        res.project?.toDomain()
    }

    suspend fun updateProject(id: String, input: ProjectInput, allowUnscheduled: Boolean): ApiResult<EvalProject?> =
        calendarRepository.safeCall {
            val res = api.updateProject(id, projectBody(input, allowUnscheduled))
            applyEvalState(res.evalState)
            applyCalendarState(res.calendarState)
            res.project?.toDomain()
        }

    /** Inline status change; [syncGroup] applies it to every record sharing the project name. */
    suspend fun updateProjectStatus(id: String, status: String, syncGroup: Boolean): ApiResult<Int> =
        calendarRepository.safeCall {
            val res = api.updateProject(id, buildJsonObject {
                put("status", status)
                if (syncGroup) put("syncGroupStatus", true)
                put("today", today())
            })
            applyEvalState(res.evalState)
            applyCalendarState(res.calendarState)
            if (syncGroup) res.affectedCount else 1
        }

    suspend fun updateProjectSettle(id: String, settle: String): ApiResult<Unit> = calendarRepository.safeCall {
        val res = api.updateProject(id, buildJsonObject {
            put("settle", settle)
            put("today", today())
        })
        applyEvalState(res.evalState)
        applyCalendarState(res.calendarState)
    }

    suspend fun batchProjects(ids: List<String>, status: String?, settle: String?): ApiResult<Int> =
        calendarRepository.safeCall {
            val res = api.batchProjects(buildJsonObject {
                putJsonArray("ids") { ids.forEach { add(JsonPrimitive(it)) } }
                if (status != null) put("status", status)
                if (settle != null) put("settle", settle)
                put("today", today())
            })
            applyEvalState(res.evalState)
            applyCalendarState(res.calendarState)
            res.updatedCount
        }

    suspend fun deleteProject(id: String): ApiResult<Unit> = calendarRepository.safeCall {
        val res = api.deleteProject(id, today())
        applyEvalState(res.evalState)
        applyCalendarState(res.calendarState)
    }

    /** Called after DELETE /api/events removed a linked project on the server. */
    suspend fun removeProjectLocally(id: String) {
        dao.deleteProject(id)
    }

    suspend fun setMergeNames(flag: Boolean): ApiResult<Boolean> = calendarRepository.safeCall {
        val res = api.updatePreferences(EvalPreferencesRequest(flag))
        settingsStore.saveEvalMergeNames(res.mergeNames)
        res.mergeNames
    }

    // -------------------------------------------------------------------------
    // Travels
    // -------------------------------------------------------------------------
    private fun travelBody(input: TravelInput): JsonObject = buildJsonObject {
        put("date", input.date)
        put("name", input.name.trim().take(120))
        put("stage", input.stage)
        put("level", input.level)
        put("planStart", input.planStart)
        put("planEnd", input.planEnd)
        put("planText", input.planText)
        put("cat", input.cat)
        put("catCustom", input.catCustom.trim().take(40))
        put("amount", input.amount)
        put("detail", input.detail.trim().take(500))
        put("reimb", input.reimb)
        put("today", today())
    }

    suspend fun createTravel(input: TravelInput): ApiResult<EvalTravel?> = calendarRepository.safeCall {
        val res = api.createTravel(travelBody(input))
        applyEvalState(res.evalState)
        res.travel?.toDomain()
    }

    suspend fun updateTravel(id: String, input: TravelInput): ApiResult<EvalTravel?> = calendarRepository.safeCall {
        val res = api.updateTravel(id, travelBody(input))
        applyEvalState(res.evalState)
        res.travel?.toDomain()
    }

    suspend fun updateTravelReimb(id: String, reimb: String): ApiResult<Unit> = calendarRepository.safeCall {
        val res = api.updateTravel(id, buildJsonObject {
            put("reimb", reimb)
            put("today", today())
        })
        applyEvalState(res.evalState)
    }

    suspend fun batchTravels(ids: List<String>, reimb: String): ApiResult<Int> = calendarRepository.safeCall {
        val res = api.batchTravels(buildJsonObject {
            putJsonArray("ids") { ids.forEach { add(JsonPrimitive(it)) } }
            put("reimb", reimb)
            put("today", today())
        })
        applyEvalState(res.evalState)
        res.updatedCount
    }

    suspend fun deleteTravel(id: String): ApiResult<Unit> = calendarRepository.safeCall {
        val res = api.deleteTravel(id, today())
        applyEvalState(res.evalState)
    }

    suspend fun setTravelMerge(flag: Boolean) {
        settingsStore.saveEvalTravelMerge(flag)
    }
}
