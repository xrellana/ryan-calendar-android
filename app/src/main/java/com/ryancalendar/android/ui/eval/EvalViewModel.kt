package com.ryancalendar.android.ui.eval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ryancalendar.android.data.repository.ApiResult
import com.ryancalendar.android.data.repository.EvalRepository
import com.ryancalendar.android.data.repository.ProjectInput
import com.ryancalendar.android.data.repository.TravelInput
import com.ryancalendar.android.domain.eval.DaysStatsResult
import com.ryancalendar.android.domain.eval.DaysWindow
import com.ryancalendar.android.domain.eval.DaysWindowMode
import com.ryancalendar.android.domain.eval.EvalCore
import com.ryancalendar.android.domain.eval.EvalProject
import com.ryancalendar.android.domain.eval.EvalSnapshot
import com.ryancalendar.android.domain.eval.EvalStats
import com.ryancalendar.android.domain.eval.EvalTravel
import com.ryancalendar.android.domain.eval.FeeFilter
import com.ryancalendar.android.domain.eval.FeeGroupBy
import com.ryancalendar.android.domain.eval.FeeStatsResult
import com.ryancalendar.android.domain.eval.ProjectFilter
import com.ryancalendar.android.domain.eval.ProjectGroup
import com.ryancalendar.android.domain.eval.ProjectKpis
import com.ryancalendar.android.domain.eval.ProjectRow
import com.ryancalendar.android.domain.eval.ProjectSort
import com.ryancalendar.android.domain.eval.RangePreset
import com.ryancalendar.android.domain.eval.TravelCatRow
import com.ryancalendar.android.domain.eval.TravelFilter
import com.ryancalendar.android.domain.eval.TravelGroup
import com.ryancalendar.android.domain.eval.TravelGroupBy
import com.ryancalendar.android.domain.eval.TravelSummaryRow
import com.ryancalendar.android.domain.eval.TravelTotals
import com.ryancalendar.android.domain.eval.presetRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProjectListUi(
    val loaded: Boolean = false,
    val totalRecordCount: Int = 0,
    val merged: Boolean = true,
    /** KPIs follow the selection when anything is selected, like the web client. */
    val kpis: ProjectKpis = EMPTY_KPIS,
    val filteredTotals: ProjectKpis = EMPTY_KPIS,
    val isSelectionScope: Boolean = false,
    val selectedProjectCount: Int = 0,
    val selectedRecordCount: Int = 0,
    val groups: List<ProjectGroup> = emptyList(),
    val rows: List<ProjectRow> = emptyList(),
    /** Ids a "select all" action targets: whole groups in merged mode, visible rows otherwise. */
    val selectableIds: List<String> = emptyList(),
    val years: List<Int> = emptyList()
)

data class TravelListUi(
    val loaded: Boolean = false,
    val totalCount: Int = 0,
    val filteredCount: Int = 0,
    val totals: TravelTotals = EMPTY_TRAVEL_TOTALS,
    val isSelectionScope: Boolean = false,
    val groups: List<TravelGroup> = emptyList(),
    val categories: List<TravelCatRow> = emptyList(),
    val summary: List<TravelSummaryRow> = emptyList(),
    val nameOptions: List<String> = emptyList(),
    val catOptions: List<String> = emptyList(),
    val filteredIds: List<String> = emptyList()
)

val EMPTY_KPIS = ProjectKpis(0, 0, 0, 0, 0.0, 0.0, 0.0, 0)
val EMPTY_TRAVEL_TOTALS = TravelTotals(0.0, 0, 0, 0.0, 0, 0.0, 0, 0.0, 0)

class EvalViewModel(private val repo: EvalRepository) : ViewModel() {

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _today = MutableStateFlow(LocalDate.now().toString())
    val today: StateFlow<String> = _today.asStateFlow()

    val snapshot: StateFlow<EvalSnapshot> = repo.snapshotFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, EvalSnapshot.Empty)

    val travelMerge: StateFlow<Boolean> = repo.travelMergeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ---------------------------------------------------------------- projects
    private val _projectFilter = MutableStateFlow(ProjectFilter())
    val projectFilter: StateFlow<ProjectFilter> = _projectFilter.asStateFlow()

    private val _projectSort = MutableStateFlow(ProjectSort())
    val projectSort: StateFlow<ProjectSort> = _projectSort.asStateFlow()

    private val _selectedProjectIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedProjectIds: StateFlow<Set<String>> = _selectedProjectIds.asStateFlow()

    val projectListUi: StateFlow<ProjectListUi> = combine(
        snapshot, _projectFilter, _projectSort, _selectedProjectIds, _today
    ) { snap, filter, sort, selected, today ->
        buildProjectListUi(snap, filter, sort, selected, today)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, ProjectListUi())

    // ---------------------------------------------------------------- fee stats
    private val _feeFilter = MutableStateFlow(FeeFilter())
    val feeFilter: StateFlow<FeeFilter> = _feeFilter.asStateFlow()

    val feeStats: StateFlow<FeeStatsResult?> = combine(snapshot, _feeFilter) { snap, f ->
        EvalStats.feeStats(snap.projects, snap.settings, f)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---------------------------------------------------------------- days stats
    private val _daysWindow = MutableStateFlow(DaysWindow())
    val daysWindow: StateFlow<DaysWindow> = _daysWindow.asStateFlow()

    val daysStats: StateFlow<DaysStatsResult?> = combine(snapshot, _daysWindow, _today) { snap, w, today ->
        EvalStats.daysStats(snap.projects, today, w)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---------------------------------------------------------------- travels
    private val _travelFilter = MutableStateFlow(TravelFilter())
    val travelFilter: StateFlow<TravelFilter> = _travelFilter.asStateFlow()

    private val _selectedTravelIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTravelIds: StateFlow<Set<String>> = _selectedTravelIds.asStateFlow()

    val travelListUi: StateFlow<TravelListUi> = combine(
        snapshot, _travelFilter, _selectedTravelIds, travelMerge
    ) { snap, f, selected, merge ->
        buildTravelListUi(snap, f, selected, merge)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, TravelListUi())

    init {
        // Drop selections whose records disappeared after a refresh or delete.
        viewModelScope.launch {
            snapshot.collect { snap ->
                val pIds = snap.projects.mapTo(HashSet()) { it.id }
                _selectedProjectIds.update { sel -> sel.filterTo(HashSet()) { it in pIds } }
                val tIds = snap.travels.mapTo(HashSet()) { it.id }
                _selectedTravelIds.update { sel -> sel.filterTo(HashSet()) { it in tIds } }
            }
        }
    }

    private fun buildProjectListUi(
        snap: EvalSnapshot,
        filter: ProjectFilter,
        sort: ProjectSort,
        selected: Set<String>,
        today: String
    ): ProjectListUi {
        val all = snap.projects
        val filtered = EvalStats.filterProjects(all, filter)
        val selectedRecords = if (selected.isEmpty()) emptyList() else all.filter { it.id in selected }
        val merged = snap.mergeNames
        val groups = if (merged) EvalStats.buildGroups(filtered, all, snap.settings, sort) else emptyList()
        val rows = if (merged) emptyList() else EvalStats.sortFlat(filtered, snap.settings, sort)
        val selectable = if (merged) groups.flatMap { it.allRecordIds } else rows.map { it.project.id }
        val todayYear = EvalCore.parseDate(today)?.year ?: LocalDate.now().year
        return ProjectListUi(
            loaded = snap.loaded,
            totalRecordCount = all.size,
            merged = merged,
            kpis = EvalStats.projectKpis(if (selected.isEmpty()) filtered else selectedRecords, snap.settings),
            filteredTotals = EvalStats.projectKpis(filtered, snap.settings),
            isSelectionScope = selected.isNotEmpty(),
            selectedProjectCount = selectedRecords.mapTo(HashSet()) { it.name }.size,
            selectedRecordCount = selectedRecords.size,
            groups = groups,
            rows = rows,
            selectableIds = selectable,
            years = EvalStats.projectYears(all, todayYear)
        )
    }

    private fun buildTravelListUi(
        snap: EvalSnapshot,
        f: TravelFilter,
        selected: Set<String>,
        merge: Boolean
    ): TravelListUi {
        val all = snap.travels
        val filtered = EvalStats.filterTravels(all, f)
        val scope = if (selected.isEmpty()) filtered else all.filter { it.id in selected }
        return TravelListUi(
            loaded = snap.loaded,
            totalCount = all.size,
            filteredCount = filtered.size,
            totals = EvalStats.travelTotals(scope),
            isSelectionScope = selected.isNotEmpty(),
            groups = EvalStats.travelGroups(filtered, snap.projects, merge),
            categories = EvalStats.travelCategories(scope),
            summary = EvalStats.travelSummary(scope, f.groupBy),
            nameOptions = all.map { it.name }.filter { it.isNotBlank() }.distinct().sorted(),
            catOptions = all.map { EvalCore.travelDisplayCat(it) }.distinct(),
            filteredIds = filtered.map { it.id }
        )
    }

    // ---------------------------------------------------------------- lookups
    fun findProject(id: String): EvalProject? = snapshot.value.projects.find { it.id == id }
    fun findTravel(id: String): EvalTravel? = snapshot.value.travels.find { it.id == id }

    // ---------------------------------------------------------------- refresh
    fun onResume() {
        _today.value = LocalDate.now().toString()
    }

    fun refresh(showToast: Boolean = false) {
        if (_isRefreshing.value) return
        _today.value = LocalDate.now().toString()
        viewModelScope.launch {
            _isRefreshing.value = true
            when (val res = repo.refresh()) {
                is ApiResult.Success -> if (showToast) toast("已同步最新评估数据")
                is ApiResult.Error -> if (res.httpStatus != 401) toast("同步失败：${res.message}")
            }
            _isRefreshing.value = false
        }
    }

    // ---------------------------------------------------------------- filters
    fun updateProjectFilter(transform: (ProjectFilter) -> ProjectFilter) = _projectFilter.update(transform)
    fun resetProjectFilter() { _projectFilter.value = ProjectFilter() }
    fun setProjectSort(sort: ProjectSort) { _projectSort.value = sort }

    fun setFeePreset(preset: RangePreset) {
        val today = EvalCore.parseDate(_today.value) ?: LocalDate.now()
        val (s, e) = presetRange(preset, today)
        _feeFilter.update { it.copy(preset = preset, start = s, end = e) }
    }

    fun setFeeCustomRange(start: String, end: String) {
        _feeFilter.update { it.copy(preset = RangePreset.CUSTOM, start = start, end = end) }
    }

    fun updateFeeFilter(transform: (FeeFilter) -> FeeFilter) = _feeFilter.update(transform)
    fun setFeeGroupBy(g: FeeGroupBy) = _feeFilter.update { it.copy(groupBy = g) }

    fun setDaysWindow(mode: DaysWindowMode) = _daysWindow.update { it.copy(mode = mode) }
    fun setDaysCustomRange(start: String, end: String) {
        _daysWindow.value = DaysWindow(DaysWindowMode.CUSTOM, start, end)
    }

    fun setTravelPreset(preset: RangePreset) {
        val today = EvalCore.parseDate(_today.value) ?: LocalDate.now()
        val (s, e) = presetRange(preset, today)
        _travelFilter.update { it.copy(preset = preset, start = s, end = e) }
    }

    fun setTravelCustomRange(start: String, end: String) {
        _travelFilter.update { it.copy(preset = RangePreset.CUSTOM, start = start, end = end) }
    }

    fun updateTravelFilter(transform: (TravelFilter) -> TravelFilter) = _travelFilter.update(transform)
    fun resetTravelFilter() { _travelFilter.value = TravelFilter(groupBy = _travelFilter.value.groupBy) }
    fun setTravelGroupBy(g: TravelGroupBy) = _travelFilter.update { it.copy(groupBy = g) }

    /** Tapping a reimbursement KPI toggles a single-status filter. */
    fun toggleTravelReimbShortcut(reimb: String) = _travelFilter.update {
        it.copy(reimbs = if (it.reimbs == setOf(reimb)) emptySet() else setOf(reimb))
    }

    fun setTravelMerge(flag: Boolean) {
        viewModelScope.launch { repo.setTravelMerge(flag) }
    }

    // ---------------------------------------------------------------- selection
    fun toggleProjectSelection(ids: Collection<String>) = _selectedProjectIds.update { cur ->
        if (ids.all { it in cur }) cur - ids.toSet() else cur + ids
    }

    fun selectProjects(ids: Collection<String>) = _selectedProjectIds.update { it + ids }
    fun clearProjectSelection() { _selectedProjectIds.value = emptySet() }

    fun toggleTravelSelection(id: String) = _selectedTravelIds.update { if (id in it) it - id else it + id }
    fun selectTravels(ids: Collection<String>) = _selectedTravelIds.update { it + ids }
    fun clearTravelSelection() { _selectedTravelIds.value = emptySet() }

    // ---------------------------------------------------------------- mutations
    private fun toast(msg: String) {
        _toasts.tryEmit(msg)
    }

    private fun <T> mutate(
        block: suspend () -> ApiResult<T>,
        onSuccess: (T) -> Unit
    ) {
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val res = block()
            _isMutating.value = false
            when (res) {
                is ApiResult.Success -> onSuccess(res.data)
                is ApiResult.Error -> if (res.httpStatus != 401) toast(res.message)
            }
        }
    }

    fun saveProject(
        editingId: String?,
        input: ProjectInput,
        allowUnscheduled: Boolean,
        onDone: (EvalProject?) -> Unit
    ) = mutate(
        block = {
            if (editingId == null) repo.createProject(input)
            else repo.updateProject(editingId, input, allowUnscheduled)
        },
        onSuccess = {
            toast(if (editingId == null) "已新建项目并同步至工作日程" else "项目与工作日程已同步更新")
            onDone(it)
        }
    )

    fun setProjectStatus(id: String, status: String, syncGroup: Boolean) = mutate(
        block = { repo.updateProjectStatus(id, status, syncGroup) },
        onSuccess = { n -> toast(if (syncGroup && n > 1) "已将整组 $n 条记录设为「$status」" else "项目状态已改为「$status」") }
    )

    fun setProjectSettle(id: String, settle: String) = mutate(
        block = { repo.updateProjectSettle(id, settle) },
        onSuccess = { toast("费用状态已改为「$settle」") }
    )

    fun batchSetProjects(status: String?, settle: String?) {
        val ids = _selectedProjectIds.value.toList()
        if (ids.isEmpty()) return
        mutate(
            block = { repo.batchProjects(ids, status, settle) },
            onSuccess = { n ->
                toast("已批量更新 $n 条记录")
                clearProjectSelection()
            }
        )
    }

    fun deleteProject(id: String, onDone: () -> Unit) = mutate(
        block = { repo.deleteProject(id) },
        onSuccess = {
            toast("项目已删除，关联日程已同步移除")
            onDone()
        }
    )

    fun setMergeNames(flag: Boolean) = mutate(
        block = { repo.setMergeNames(flag) },
        onSuccess = { clearProjectSelection() }
    )

    fun saveTravel(editingId: String?, input: TravelInput, onDone: (EvalTravel?) -> Unit) = mutate(
        block = {
            if (editingId == null) repo.createTravel(input) else repo.updateTravel(editingId, input)
        },
        onSuccess = {
            toast(if (editingId == null) "已新增差旅记录" else "差旅记录已更新")
            onDone(it)
        }
    )

    fun setTravelReimb(id: String, reimb: String) = mutate(
        block = { repo.updateTravelReimb(id, reimb) },
        onSuccess = { toast("报销状态已改为「$reimb」") }
    )

    fun batchSetTravelReimb(reimb: String) {
        val ids = _selectedTravelIds.value.toList()
        if (ids.isEmpty()) return
        mutate(
            block = { repo.batchTravels(ids, reimb) },
            onSuccess = { n ->
                toast("已将 $n 笔差旅设为「$reimb」")
                clearTravelSelection()
            }
        )
    }

    fun deleteTravel(id: String, onDone: () -> Unit) = mutate(
        block = { repo.deleteTravel(id) },
        onSuccess = {
            toast("差旅记录已删除")
            onDone()
        }
    )

    companion object {
        fun factory(repo: EvalRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = EvalViewModel(repo) as T
            }
    }
}
