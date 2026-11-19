package com.ryancalendar.android.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ryancalendar.android.data.repository.ApiResult
import com.ryancalendar.android.data.repository.CalendarRepository
import com.ryancalendar.android.domain.calendar.HolidayIndex
import com.ryancalendar.android.domain.calendar.WeekLayoutEngine
import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.model.CalendarEvent
import com.ryancalendar.android.domain.model.ReminderSettings
import com.ryancalendar.android.domain.model.UserProfile
import com.ryancalendar.android.notification.NotificationHelper
import com.ryancalendar.android.notification.ReminderScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.YearMonth

enum class AuthState {
    Checking,
    Unauthenticated,
    Authenticated
}

enum class CalendarViewMode(val label: String) {
    MONTH("月"),
    WEEK("周"),
    YEAR("年")
}

class AppViewModel(
    private val appContext: Context,
    private val repository: CalendarRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState.Checking)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _loginLoading = MutableStateFlow(false)
    val loginLoading: StateFlow<Boolean> = _loginLoading.asStateFlow()

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _selectedFilterCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFilterCategoryIds: StateFlow<Set<String>> = _selectedFilterCategoryIds.asStateFlow()

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

    val serverUrl: String = repository.serverUrl

    val currentUser: StateFlow<UserProfile?> = repository.currentUserFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lastSyncMs: StateFlow<Long> = repository.lastSyncMsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val reminderSettings: StateFlow<ReminderSettings> = repository.reminderSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSettings())

    val categories: StateFlow<List<CalendarCategory>> = repository.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarCategory.Defaults)

    val allEvents: StateFlow<List<CalendarEvent>> = repository.eventsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val holidays: StateFlow<HolidayIndex> = repository.holidayIndexFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, HolidayIndex.Builtin)

    val filteredEvents: StateFlow<List<CalendarEvent>> = combine(
        allEvents,
        selectedFilterCategoryIds
    ) { events, filterIds ->
        if (filterIds.isEmpty()) events
        else events.filter { it.categoryId in filterIds }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var lastForegroundSyncMs: Long = 0L

    init {
        observeUnauthorized()
        bootstrap()
    }

    private fun observeUnauthorized() {
        viewModelScope.launch {
            repository.unauthorizedEvents.collect { msg ->
                _authState.value = AuthState.Unauthenticated
                _selectedFilterCategoryIds.value = emptySet()
                emitToast(msg)
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val cachedUser = repository.getCachedUser()

            // 离线优先：有缓存用户时直接进入主界面，并在后台验证会话与同步最新日程
            if (cachedUser != null) {
                _authState.value = AuthState.Authenticated
                val reminder = repository.getReminderSettings()
                if (reminder.enabled) {
                    ReminderScheduler.scheduleNext(appContext, reminder)
                }
                silentRefresh(force = true, includeHolidays = true)
            } else if (!repository.hasStoredSession()) {
                // 首次安装/已退出：本地没有会话 Cookie，服务端必然返回未登录，直接展示登录页，
                // 避免在弱网或服务器不可达时卡在加载页等待网络超时（最长可达数十秒）
                _authState.value = AuthState.Unauthenticated
            } else {
                // 有 Cookie 但无缓存用户（罕见）：校验会话，但限制等待时长，超时按未登录处理
                val check = withTimeoutOrNull(SESSION_CHECK_TIMEOUT_MS) { repository.verifySession() }
                when (check) {
                    null -> {
                        _authState.value = AuthState.Unauthenticated
                    }
                    is ApiResult.Success -> {
                        if (check.data != null) {
                            _authState.value = AuthState.Authenticated
                            silentRefresh(force = true, includeHolidays = true)
                        } else {
                            _authState.value = AuthState.Unauthenticated
                        }
                    }
                    is ApiResult.Error -> {
                        _authState.value = AuthState.Unauthenticated
                    }
                }
            }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _loginError.value = "请输入账号 ID 和密码"
            return
        }
        viewModelScope.launch {
            _loginLoading.value = true
            _loginError.value = null
            when (val res = repository.login(username, password)) {
                is ApiResult.Success -> {
                    _authState.value = AuthState.Authenticated
                    _loginLoading.value = false
                    emitToast("登录成功，正在同步云端日程...")
                    silentRefresh(force = true, includeHolidays = true)
                }
                is ApiResult.Error -> {
                    _loginLoading.value = false
                    _loginError.value = res.message
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            ReminderScheduler.cancel(appContext)
            _selectedFilterCategoryIds.value = emptySet()
            _authState.value = AuthState.Unauthenticated
            emitToast("已退出登录")
        }
    }

    fun onAppResumed() {
        if (_authState.value != AuthState.Authenticated) return
        silentRefresh(force = false, includeHolidays = false)
    }

    fun manualSync() {
        if (_authState.value != AuthState.Authenticated) return
        viewModelScope.launch {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true
            when (val res = repository.syncFromServer(includeHolidays = true)) {
                is ApiResult.Success -> {
                    lastForegroundSyncMs = System.currentTimeMillis()
                    emitToast(if (res.data) "已同步最新云端日程" else "当前已是最新数据")
                }
                is ApiResult.Error -> {
                    if (res.httpStatus != 401) {
                        emitToast("同步失败：${res.message}")
                    }
                }
            }
            _isSyncing.value = false
        }
    }

    private fun silentRefresh(force: Boolean, includeHolidays: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastForegroundSyncMs < 15_000L) return
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            when (val res = repository.syncFromServer(includeHolidays = includeHolidays)) {
                is ApiResult.Success -> {
                    lastForegroundSyncMs = System.currentTimeMillis()
                    if (!force && res.data) {
                        emitToast("已自动同步最新日程")
                    }
                }
                is ApiResult.Error -> {
                    // 静默同步不打扰用户（401 已由 repository 统一处理）
                }
            }
            _isSyncing.value = false
        }
    }

    // -------------------------------------------------------------------------
    // 日历导航与筛选状态
    // -------------------------------------------------------------------------
    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
    }

    fun setVisibleMonth(ym: YearMonth) {
        _visibleMonth.value = ym
        val curSelected = _selectedDate.value
        if (YearMonth.from(curSelected) != ym) {
            val today = LocalDate.now()
            _selectedDate.value = if (YearMonth.from(today) == ym) {
                today
            } else {
                ym.atDay(1)
            }
        }
    }

    fun jumpToday() {
        val today = LocalDate.now()
        _selectedDate.value = today
        _visibleMonth.value = YearMonth.from(today)
    }

    fun toggleFilterCategory(categoryId: String) {
        val cur = _selectedFilterCategoryIds.value.toMutableSet()
        if (!cur.add(categoryId)) cur.remove(categoryId)
        _selectedFilterCategoryIds.value = cur
    }

    fun clearCategoryFilters() {
        _selectedFilterCategoryIds.value = emptySet()
    }

    fun getCategory(id: String): CalendarCategory =
        categories.value.find { it.id == id } ?: CalendarCategory.Fallback

    fun getPriorityMap(): Map<String, Int> =
        categories.value.mapIndexed { idx, cat -> cat.id to idx }.toMap()

    fun getEventsOnDate(date: LocalDate, ignoreFilter: Boolean = false): List<CalendarEvent> {
        val dateStr = date.toString()
        val source = if (ignoreFilter) allEvents.value else filteredEvents.value
        val pMap = getPriorityMap()
        return source
            .filter { it.startDate <= dateStr && it.effectiveEndDate >= dateStr }
            .sortedWith { a, b -> WeekLayoutEngine.compareEvents(a, b, pMap) }
    }

    fun searchEvents(query: String, categoryIds: Set<String>): List<CalendarEvent> {
        val clean = query.trim().lowercase()
        val terms = if (clean.isEmpty()) emptyList() else clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val pMap = getPriorityMap()
        return allEvents.value
            .filter { ev ->
                if (categoryIds.isNotEmpty() && ev.categoryId !in categoryIds) return@filter false
                if (terms.isEmpty()) return@filter true
                val catName = getCategory(ev.categoryId).name
                val haystack = buildString {
                    append(ev.title).append(' ')
                    append(ev.notes).append(' ')
                    append(ev.startDate).append(' ')
                    append(ev.endDate).append(' ')
                    append(catName).append(' ')
                    ev.ledgerMeta?.let {
                        append(it.projectName).append(' ')
                        append(it.stage).append(' ')
                        append(it.level).append(' ')
                        append(it.role)
                    }
                }.lowercase()
                terms.all { haystack.contains(it) }
            }
            .sortedWith { a, b ->
                if (a.startDate != b.startDate) b.startDate.compareTo(a.startDate)
                else WeekLayoutEngine.compareEvents(a, b, pMap)
            }
    }

    // -------------------------------------------------------------------------
    // 日程 CRUD
    // -------------------------------------------------------------------------
    fun saveEvent(
        editingEventId: String?,
        title: String,
        categoryId: String,
        startDate: String,
        endDate: String,
        allDay: Boolean,
        startTime: String,
        endTime: String,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = if (editingEventId.isNullOrBlank()) {
                repository.createEvent(title, categoryId, startDate, endDate, allDay, startTime, endTime, notes)
            } else {
                repository.updateEvent(editingEventId, title, categoryId, startDate, endDate, allDay, startTime, endTime, notes)
            }
            _isMutating.value = false
            when (res) {
                is ApiResult.Success -> {
                    emitToast(if (editingEventId.isNullOrBlank()) "已新建日程" else "日程已更新")
                    onSuccess()
                }
                is ApiResult.Error -> {
                    if (res.httpStatus != 401) emitToast(res.message)
                }
            }
        }
    }

    fun deleteEvent(eventId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.deleteEvent(eventId)
            _isMutating.value = false
            when (res) {
                is ApiResult.Success -> {
                    emitToast("日程已删除")
                    onSuccess()
                }
                is ApiResult.Error -> {
                    if (res.httpStatus != 401) emitToast(res.message)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 类别管理
    // -------------------------------------------------------------------------
    fun addCategory(name: String, color: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.addCategory(name, color)
            _isMutating.value = false
            when (res) {
                is ApiResult.Success -> {
                    emitToast("已添加类别")
                    onSuccess()
                }
                is ApiResult.Error -> if (res.httpStatus != 401) emitToast(res.message)
            }
        }
    }

    fun updateCategory(id: String, name: String, color: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.updateCategory(id, name, color)
            _isMutating.value = false
            when (res) {
                is ApiResult.Success -> {
                    emitToast("类别已更新")
                    onSuccess()
                }
                is ApiResult.Error -> if (res.httpStatus != 401) emitToast(res.message)
            }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.deleteCategory(id)
            _isMutating.value = false
            when (res) {
                is ApiResult.Success -> {
                    _selectedFilterCategoryIds.value = _selectedFilterCategoryIds.value - id
                    emitToast("类别已删除")
                }
                is ApiResult.Error -> if (res.httpStatus != 401) emitToast(res.message)
            }
        }
    }

    fun moveCategory(id: String, moveUp: Boolean) {
        val list = categories.value.map { it.id }.toMutableList()
        val idx = list.indexOf(id)
        if (idx < 0) return
        val target = if (moveUp) idx - 1 else idx + 1
        if (target !in list.indices) return
        val tmp = list[idx]
        list[idx] = list[target]
        list[target] = tmp
        viewModelScope.launch {
            when (val res = repository.reorderCategories(list)) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> if (res.httpStatus != 401) emitToast(res.message)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 个人账号与 ICS
    // -------------------------------------------------------------------------
    fun updateDisplayName(newName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.updateDisplayName(newName)
            _isMutating.value = false
            when (res) {
                is ApiResult.Success -> {
                    emitToast("显示名称已更新")
                    onSuccess()
                }
                is ApiResult.Error -> if (res.httpStatus != 401) emitToast(res.message)
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.changePassword(currentPassword, newPassword)
            _isMutating.value = false
            when (res) {
                is ApiResult.Success -> {
                    emitToast("密码已修改，其他设备将需要重新登录")
                    onSuccess()
                }
                is ApiResult.Error -> if (res.httpStatus != 401) emitToast(res.message)
            }
        }
    }

    fun resetIcsToken() {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.resetIcsToken()
            _isMutating.value = false
            when (res) {
                is ApiResult.Success -> emitToast("订阅链接已重置，旧链接已失效")
                is ApiResult.Error -> if (res.httpStatus != 401) emitToast(res.message)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 本地每日定时通知
    // -------------------------------------------------------------------------
    fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        val next = ReminderSettings(enabled = enabled, hour = hour, minute = minute)
        viewModelScope.launch {
            repository.saveReminderSettings(next)
            ReminderScheduler.applySettings(appContext, next)
            emitToast(
                if (enabled) "已开启每日 ${next.formattedTime} 日程提醒"
                else "已关闭每日定时提醒"
            )
        }
    }

    fun sendTestNotification() {
        val today = LocalDate.now()
        val todayEvents = getEventsOnDate(today, ignoreFilter = true)
        val catMap = categories.value.associateBy { it.id }
        val sent = NotificationHelper.showTodayAgendaNotification(
            context = appContext,
            todayEvents = todayEvents,
            categoryMap = catMap,
            isTest = true
        )
        emitToast(if (sent) "已发送测试通知，请查看通知栏" else "缺少通知权限，请在系统设置中开启通知")
    }

    fun emitToast(message: String) {
        _toastMessages.tryEmit(message)
    }

    companion object {
        private const val SESSION_CHECK_TIMEOUT_MS = 5_000L

        fun factory(appContext: Context, repository: CalendarRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(appContext.applicationContext, repository) as T
                }
            }
    }
}
