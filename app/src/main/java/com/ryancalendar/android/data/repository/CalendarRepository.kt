package com.ryancalendar.android.data.repository

import com.ryancalendar.android.data.local.CalendarDao
import com.ryancalendar.android.data.local.CategoryEntity
import com.ryancalendar.android.data.local.EventEntity
import com.ryancalendar.android.data.local.SettingsStore
import com.ryancalendar.android.data.local.toEntity
import com.ryancalendar.android.data.remote.CalendarApiService
import com.ryancalendar.android.data.remote.CategoryCreateRequest
import com.ryancalendar.android.data.remote.CategoryPatchRequest
import com.ryancalendar.android.data.remote.ChangePasswordRequest
import com.ryancalendar.android.data.remote.EventUpsertRequest
import com.ryancalendar.android.data.remote.LoginRequest
import com.ryancalendar.android.data.remote.NetworkClientFactory
import com.ryancalendar.android.data.remote.PersistentCookieJar
import com.ryancalendar.android.data.remote.ReorderCategoriesRequest
import com.ryancalendar.android.data.remote.StateResponse
import com.ryancalendar.android.data.remote.UpdateProfileRequest
import com.ryancalendar.android.domain.calendar.HolidayIndex
import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.model.CalendarEvent
import com.ryancalendar.android.domain.model.ReminderSettings
import com.ryancalendar.android.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val httpStatus: Int? = null) : ApiResult<Nothing>()
}

class CalendarRepository(
    private val api: CalendarApiService,
    private val dao: CalendarDao,
    private val settingsStore: SettingsStore,
    private val cookieJar: PersistentCookieJar,
    private val json: Json,
    /** 不挂 CookieJar 的独立客户端：后台注销时服务端的 Set-Cookie 不会影响之后的新登录态。 */
    private val sessionlessApi: CalendarApiService,
    /** 应用级协程作用域：后台注销请求不随 ViewModel 或页面销毁而取消。 */
    private val appScope: CoroutineScope
) {
    private val _unauthorizedEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val unauthorizedEvents: SharedFlow<String> = _unauthorizedEvents.asSharedFlow()

    val serverUrl: String = NetworkClientFactory.SERVER_BASE_URL
    val currentUserFlow: Flow<UserProfile?> = settingsStore.cachedUserFlow
    val lastSyncMsFlow: Flow<Long> = settingsStore.lastSyncMsFlow
    val reminderSettingsFlow: Flow<ReminderSettings> = settingsStore.reminderSettingsFlow

    val categoriesFlow: Flow<List<CalendarCategory>> = dao.observeCategories().map { list ->
        if (list.isEmpty()) CalendarCategory.Defaults else list.map { it.toDomain() }
    }

    val eventsFlow: Flow<List<CalendarEvent>> = dao.observeEvents().map { list ->
        list.map { it.toDomain() }
    }

    val holidayIndexFlow: Flow<HolidayIndex> = dao.observeHolidays().map { list ->
        if (list.isEmpty()) HolidayIndex.Builtin
        else HolidayIndex.fromServerEntries(list.map { it.toDomain() })
    }

    suspend fun getCachedUser(): UserProfile? = settingsStore.getCachedUser()

    /** 本地是否保存了会话 Cookie；没有时无需请求服务端即可判定为未登录。 */
    fun hasStoredSession(): Boolean = cookieJar.hasValidCookies()

    suspend fun getEventsOnce(): List<CalendarEvent> = dao.getEventsOnce().map { it.toDomain() }

    suspend fun getCategoriesOnce(): List<CalendarCategory> {
        val list = dao.getCategoriesOnce().map { it.toDomain() }
        return list.ifEmpty { CalendarCategory.Defaults }
    }

    suspend fun getEventById(id: String): CalendarEvent? = dao.getEventById(id)?.toDomain()

    internal suspend fun <T> safeCall(
        notifyUnauthorized: Boolean = true,
        block: suspend () -> T
    ): ApiResult<T> {
        return try {
            ApiResult.Success(block())
        } catch (t: Throwable) {
            val (code, message) = NetworkClientFactory.parseErrorMessage(t, json)
            if (code == 401 && notifyUnauthorized) {
                clearLocalSession()
                _unauthorizedEvents.tryEmit("登录态已失效，请重新登录")
            }
            ApiResult.Error(message = message, httpStatus = code)
        }
    }

    suspend fun verifySession(): ApiResult<UserProfile?> = safeCall(notifyUnauthorized = false) {
        val res = api.getMe()
        val user = res.user?.toDomain()
        if (user != null) {
            settingsStore.saveUser(user)
        } else {
            clearLocalSession()
        }
        user
    }

    suspend fun login(username: String, password: String): ApiResult<UserProfile> {
        return safeCall(notifyUnauthorized = false) {
            val res = api.login(LoginRequest(username = username.trim(), password = password))
            val user = res.user.toDomain()
            settingsStore.saveUser(user)
            user
        }
    }

    suspend fun logout(): ApiResult<Unit> {
        // 先记下会话 Cookie，再立即清理本地登录态，界面无需等待网络即可回到登录页；
        // 随后在后台尽力通知服务端销毁会话，断网或超时均忽略
        val sessionCookie = cookieJar.cookieHeaderFor(NetworkClientFactory.serverHttpUrl)
        clearLocalSession()
        if (sessionCookie != null) {
            appScope.launch {
                try {
                    sessionlessApi.logout(sessionCookie)
                } catch (_: Exception) {
                    // ignore network error on logout
                }
            }
        }
        return ApiResult.Success(Unit)
    }

    private suspend fun clearLocalSession() {
        cookieJar.clearAll()
        settingsStore.clearSessionMeta()
        dao.clearAllUserData()
    }

    /**
     * 从云端全量拉取日历状态与节假日数据并更新 Room 缓存。
     * @return Boolean 表示本次同步数据相比上次是否有变化
     */
    suspend fun syncFromServer(includeHolidays: Boolean = true): ApiResult<Boolean> = safeCall {
        val prevUpdatedAt = settingsStore.getServerUpdatedAt()
        val prevEventsCount = dao.getEventsOnce().size
        val prevCatCount = dao.getCategoriesOnce().size

        val state = api.getState()
        val (catEntities, eventEntities) = writeCalendarState(state)

        if (includeHolidays) {
            try {
                val holidayPayload = api.getHolidays()
                val hEntities = holidayPayload.entries
                    .map { it.toDomain().toEntity() }
                if (hEntities.isNotEmpty()) {
                    dao.replaceHolidays(hEntities)
                }
            } catch (_: Exception) {
                // 节假日接口失败不影响主日程同步，继续使用本地缓存/内置兜底
            }
        }

        state.updatedAt != prevUpdatedAt ||
            eventEntities.size != prevEventsCount ||
            catEntities.size != prevCatCount
    }

    private suspend fun writeCalendarState(state: StateResponse): Pair<List<CategoryEntity>, List<EventEntity>> {
        val catEntities = state.categories.mapIndexed { index, dto ->
            dto.toDomain(index).toEntity(index)
        }
        val eventEntities = state.events
            .map { it.toDomain() }
            .filter { it.title.isNotBlank() && it.startDate.isNotBlank() }
            .map { it.toEntity() }
        dao.replaceState(catEntities, eventEntities)
        settingsStore.saveSyncMeta(System.currentTimeMillis(), state.updatedAt)
        return catEntities to eventEntities
    }

    /** 评估模块的写接口会返回完整日程状态，直接落库以保证两个模块数据一致。 */
    internal suspend fun applyCalendarState(state: StateResponse) {
        writeCalendarState(state)
    }

    // -------------------------------------------------------------------------
    // 个人账号设置
    // -------------------------------------------------------------------------
    suspend fun updateDisplayName(displayName: String): ApiResult<UserProfile> = safeCall {
        val res = api.updateProfile(UpdateProfileRequest(displayName.trim()))
        val updated = res.user.toDomain()
        settingsStore.saveUser(updated)
        updated
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> = safeCall {
        api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
    }

    suspend fun resetIcsToken(): ApiResult<String> = safeCall {
        val res = api.resetIcsToken()
        val cur = settingsStore.getCachedUser()
        if (cur != null) {
            settingsStore.saveUser(cur.copy(icsToken = res.icsToken))
        }
        res.icsToken
    }

    // -------------------------------------------------------------------------
    // 日程 CRUD（云端优先，成功后即时更新本地 Room 缓存）
    // -------------------------------------------------------------------------
    suspend fun createEvent(
        title: String,
        categoryId: String,
        startDate: String,
        endDate: String,
        allDay: Boolean,
        startTime: String,
        endTime: String,
        notes: String
    ): ApiResult<CalendarEvent> = safeCall {
        val normalizedEnd = if (endDate.isNotBlank() && endDate > startDate) endDate else ""
        val req = EventUpsertRequest(
            title = title.trim().take(60),
            categoryId = categoryId,
            startDate = startDate,
            endDate = normalizedEnd,
            allDay = allDay,
            startTime = if (allDay) "" else startTime.ifBlank { "09:00" },
            endTime = if (allDay) "" else endTime.ifBlank { "18:00" },
            notes = notes.take(500)
        )
        val created = api.addEvent(req).event.toDomain()
        dao.upsertEvent(created.toEntity())
        settingsStore.saveSyncMeta(System.currentTimeMillis(), created.updatedAt)
        created
    }

    suspend fun updateEvent(
        eventId: String,
        title: String,
        categoryId: String,
        startDate: String,
        endDate: String,
        allDay: Boolean,
        startTime: String,
        endTime: String,
        notes: String
    ): ApiResult<CalendarEvent> = safeCall {
        val normalizedEnd = if (endDate.isNotBlank() && endDate > startDate) endDate else ""
        val req = EventUpsertRequest(
            title = title.trim().take(60),
            categoryId = categoryId,
            startDate = startDate,
            endDate = normalizedEnd,
            allDay = allDay,
            startTime = if (allDay) "" else startTime.ifBlank { "09:00" },
            endTime = if (allDay) "" else endTime.ifBlank { "18:00" },
            notes = notes.take(500)
        )
        val updated = api.updateEvent(eventId, req).event.toDomain()
        dao.upsertEvent(updated.toEntity())
        settingsStore.saveSyncMeta(System.currentTimeMillis(), updated.updatedAt)
        updated
    }

    /** @return 随日程一并删除的评估项目 id，普通日程返回空字符串。 */
    suspend fun deleteEvent(eventId: String): ApiResult<String> = safeCall {
        val res = api.deleteEvent(eventId)
        dao.deleteEventById(eventId)
        settingsStore.saveSyncMeta(System.currentTimeMillis(), "")
        res.deletedEvalProjectId
    }

    // -------------------------------------------------------------------------
    // 类别 CRUD 与排序
    // -------------------------------------------------------------------------
    suspend fun addCategory(name: String, color: String): ApiResult<List<CalendarCategory>> = safeCall {
        val res = api.addCategory(CategoryCreateRequest(name.trim().take(20), color))
        val list = res.categories.mapIndexed { idx, dto -> dto.toDomain(idx) }
        dao.replaceCategories(list.mapIndexed { idx, c -> c.toEntity(idx) })
        list
    }

    suspend fun updateCategory(
        id: String,
        name: String,
        color: String
    ): ApiResult<List<CalendarCategory>> = safeCall {
        val res = api.updateCategory(id, CategoryPatchRequest(name = name.trim().take(20), color = color))
        val list = res.categories.mapIndexed { idx, dto -> dto.toDomain(idx) }
        dao.replaceCategories(list.mapIndexed { idx, c -> c.toEntity(idx) })
        list
    }

    suspend fun deleteCategory(id: String): ApiResult<List<CalendarCategory>> = safeCall {
        val res = api.deleteCategory(id)
        val list = res.categories.mapIndexed { idx, dto -> dto.toDomain(idx) }
        dao.replaceCategories(list.mapIndexed { idx, c -> c.toEntity(idx) })
        list
    }

    suspend fun reorderCategories(orderedIds: List<String>): ApiResult<List<CalendarCategory>> = safeCall {
        val res = api.reorderCategories(ReorderCategoriesRequest(orderedIds))
        val list = res.categories.mapIndexed { idx, dto -> dto.toDomain(idx) }
        dao.replaceCategories(list.mapIndexed { idx, c -> c.toEntity(idx) })
        list
    }

    // -------------------------------------------------------------------------
    // 本地每日通知设置
    // -------------------------------------------------------------------------
    suspend fun saveReminderSettings(settings: ReminderSettings) {
        settingsStore.saveReminderSettings(settings)
    }

    suspend fun getReminderSettings(): ReminderSettings = settingsStore.getReminderSettings()
}
