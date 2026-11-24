package com.ryancalendar.android.ui.eval

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.eval.EvalCore
import com.ryancalendar.android.domain.eval.EvalEnums
import com.ryancalendar.android.domain.eval.ProjectFilter
import com.ryancalendar.android.domain.eval.ProjectGroup
import com.ryancalendar.android.domain.eval.ProjectRow
import com.ryancalendar.android.domain.eval.ProjectSort
import com.ryancalendar.android.domain.eval.ProjectSortField
import com.ryancalendar.android.domain.eval.ProjectTimeType
import com.ryancalendar.android.ui.theme.AppColors
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectsPage(
    viewModel: EvalViewModel,
    onOpenProject: (String) -> Unit,
    onNewProject: (ProjectPrefill?) -> Unit
) {
    val ui by viewModel.projectListUi.collectAsState()
    val filter by viewModel.projectFilter.collectAsState()
    val sort by viewModel.projectSort.collectAsState()
    val selected by viewModel.selectedProjectIds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    val today by viewModel.today.collectAsState()
    val todayYear = EvalCore.parseDate(today)?.year ?: 0

    var showFilter by rememberSaveable { mutableStateOf(false) }
    var showSort by rememberSaveable { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        SearchFilterBar(
            query = filter.query,
            onQueryChange = { q -> viewModel.updateProjectFilter { it.copy(query = q) } },
            placeholder = "搜索项目名称、备注",
            filterCount = filter.activeCount,
            onOpenFilter = { showFilter = true }
        ) {
            IconButton(onClick = { showSort = true }) {
                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "排序与显示")
            }
        }
        ActiveFilterRow(filters = activeProjectFilters(filter, viewModel), onClearAll = viewModel::resetProjectFilter)

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh(showToast = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "kpi") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ui.isSelectionScope) {
                            ScopePill(
                                text = "统计范围：已选 ${ui.selectedProjectCount} 个项目 · ${ui.selectedRecordCount} 条",
                                onClear = viewModel::clearProjectSelection
                            )
                        }
                        val k = ui.kpis
                        KpiGrid(
                            highlighted = ui.isSelectionScope,
                            columns = 3,
                            items = listOf(
                                KpiItem("项目数", "${k.projectCount}", "${k.recordCount} 条记录"),
                                KpiItem("进行中", "${k.inProgress}", "状态为进行中", EvalTones.BlueText),
                                KpiItem("评估天数", "${k.totalDays} 天", "所有列示项目"),
                                KpiItem(
                                    "费用合计",
                                    money(k.totalFee),
                                    if (k.pendingRateCount > 0) "${k.pendingRateCount} 条费率待设置" else "按当前费率"
                                ),
                                KpiItem("已结", money(k.settledFee), "费用已结算", EvalTones.GreenText),
                                KpiItem("未结", money(k.unsettledFee), "费用待结算", EvalTones.OrangeText)
                            )
                        )
                    }
                }

                when {
                    !ui.loaded && ui.totalRecordCount == 0 -> item(key = "loading") {
                        EmptyState("正在加载评估项目…", "首次打开需要从云端同步数据")
                    }

                    ui.totalRecordCount == 0 -> item(key = "empty") {
                        EmptyState(
                            title = "还没有评估项目",
                            message = "新建项目后会按起止时间自动同步到工作日程。",
                            action = { OutlinedButton(onClick = { onNewProject(null) }) { Text("新建项目") } }
                        )
                    }

                    ui.groups.isEmpty() && ui.rows.isEmpty() -> item(key = "nomatch") {
                        EmptyState(
                            title = "没有符合条件的项目",
                            message = "调整筛选条件，或清空后查看全部。",
                            action = { OutlinedButton(onClick = viewModel::resetProjectFilter) { Text("清空筛选") } }
                        )
                    }

                    ui.merged -> items(ui.groups, key = { "g:" + it.name }) { group ->
                        ProjectGroupCard(
                            group = group,
                            selected = selected,
                            selecting = selecting,
                            enabled = !isMutating,
                            todayYear = todayYear,
                            onToggleSelect = { viewModel.toggleProjectSelection(group.allRecordIds) },
                            onOpen = onOpenProject,
                            onStatus = { id, st -> viewModel.setProjectStatus(id, st, syncGroup = true) },
                            onSettle = viewModel::setProjectSettle,
                            onAddStage = { stage ->
                                val first = group.rows.first().project
                                onNewProject(ProjectPrefill(group.name, first.level, first.role, stage))
                            }
                        )
                    }

                    else -> items(ui.rows, key = { "r:" + it.project.id }) { row ->
                        ProjectFlatCard(
                            row = row,
                            selected = row.project.id in selected,
                            selecting = selecting,
                            enabled = !isMutating,
                            todayYear = todayYear,
                            onToggleSelect = { viewModel.toggleProjectSelection(listOf(row.project.id)) },
                            onOpen = { onOpenProject(row.project.id) },
                            onStatus = { st -> viewModel.setProjectStatus(row.project.id, st, syncGroup = false) },
                            onSettle = { se -> viewModel.setProjectSettle(row.project.id, se) }
                        )
                    }
                }

                if (ui.groups.isNotEmpty() || ui.rows.isNotEmpty()) {
                    item(key = "footer") {
                        val t = ui.filteredTotals
                        Text(
                            text = buildString {
                                append(if (ui.isSelectionScope) "全部 " else "合计 ")
                                append("${t.projectCount} 个项目 / ${t.recordCount} 条记录 · ${t.totalDays} 天 · ${money(t.totalFee)}")
                                if (t.pendingRateCount > 0) append(" · ${t.pendingRateCount} 条费率待设置")
                            },
                            fontSize = 12.sp,
                            color = AppColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        if (!selecting) {
                            Text(
                                "长按卡片可多选，批量修改状态",
                                fontSize = 11.sp,
                                color = AppColors.TextMuted,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        ProjectFilterSheet(
            filter = filter,
            years = ui.years,
            onChange = { f -> viewModel.updateProjectFilter { f } },
            onReset = viewModel::resetProjectFilter,
            onDismiss = { showFilter = false }
        )
    }
    if (showSort) {
        ProjectSortSheet(
            sort = sort,
            merged = ui.merged,
            onSort = viewModel::setProjectSort,
            onMerged = viewModel::setMergeNames,
            onDismiss = { showSort = false }
        )
    }
}

private fun activeProjectFilters(f: ProjectFilter, vm: EvalViewModel): List<ActiveFilter> = buildList {
    if (f.levels.isNotEmpty()) add(ActiveFilter(selectionLabel("等级", f.levels)) { vm.updateProjectFilter { it.copy(levels = emptySet()) } })
    if (f.roles.isNotEmpty()) add(ActiveFilter(selectionLabel("角色", f.roles)) { vm.updateProjectFilter { it.copy(roles = emptySet()) } })
    if (f.stages.isNotEmpty()) add(ActiveFilter(selectionLabel("阶段", f.stages)) { vm.updateProjectFilter { it.copy(stages = emptySet()) } })
    if (f.statuses.isNotEmpty()) add(ActiveFilter(selectionLabel("状态", f.statuses)) { vm.updateProjectFilter { it.copy(statuses = emptySet()) } })
    if (f.settles.isNotEmpty()) add(ActiveFilter(selectionLabel("费用", f.settles)) { vm.updateProjectFilter { it.copy(settles = emptySet()) } })
    if (f.timeType != ProjectTimeType.ALL) add(ActiveFilter(f.timeLabel()) { vm.updateProjectFilter { it.copy(timeType = ProjectTimeType.ALL) } })
}

private fun dateRangeText(p: com.ryancalendar.android.domain.eval.EvalProject, todayYear: Int): String {
    if (!EvalCore.isValidDate(p.start)) return "未排期"
    val s = EvalCore.formatDateShort(p.start, todayYear)
    if (!EvalCore.isValidDate(p.end) || p.end == p.start) return s
    return s + " – " + EvalCore.formatDateShort(p.end, todayYear)
}

@Composable
private fun SelectMark(selected: Boolean, partial: Boolean = false) {
    Icon(
        imageVector = if (selected || partial) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
        contentDescription = if (selected) "已选择" else "未选择",
        tint = when {
            selected -> AppColors.Primary
            partial -> AppColors.Primary.copy(alpha = 0.45f)
            else -> AppColors.BorderStrong
        },
        modifier = Modifier.padding(end = 10.dp).size(22.dp)
    )
}

@Composable
private fun FeeText(row: ProjectRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (row.fee.pendingRate) {
            SmallBadge("待设置", EvalTones.Gray)
        } else {
            Text(money(row.fee.fee), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextMain)
            if (row.fee.isManual) SmallBadge("改")
        }
    }
}

/** One stage line inside a card: stage, dates, days, fee and an inline settle switch. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StageLine(
    row: ProjectRow,
    showLevelRole: Boolean,
    showStage: Boolean,
    enabled: Boolean,
    todayYear: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSettle: (String) -> Unit
) {
    val p = row.project
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showStage) {
                    Text(p.stage, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    dateRangeText(p, todayYear),
                    fontSize = 13.sp,
                    color = if (EvalCore.isValidDate(p.start)) AppColors.TextMain else AppColors.TextMuted
                )
                if (row.endManual) SmallBadge("改", EvalTones.Gray)
                Spacer(Modifier.width(6.dp))
                Text("${row.fee.days}天", fontSize = 12.sp, color = AppColors.TextMuted)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FeeText(row)
                if (showLevelRole) {
                    Spacer(Modifier.width(8.dp))
                    Text("${p.level} · ${p.role}", fontSize = 12.sp, color = AppColors.TextMuted)
                }
            }
            if (p.note.isNotBlank()) {
                Text(p.note, fontSize = 12.sp, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        StatusMenuChip(
            value = p.settle,
            options = EvalEnums.SETTLE_STATUSES,
            tone = EvalTones::forSettle,
            enabled = enabled,
            onSelect = onSettle
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectGroupCard(
    group: ProjectGroup,
    selected: Set<String>,
    selecting: Boolean,
    enabled: Boolean,
    todayYear: Int,
    onToggleSelect: () -> Unit,
    onOpen: (String) -> Unit,
    onStatus: (String, String) -> Unit,
    onSettle: (String, String) -> Unit,
    onAddStage: (String) -> Unit
) {
    val selCount = group.allRecordIds.count { it in selected }
    val allSel = selCount == group.allRecordIds.size && selCount > 0
    val first = group.rows.first().project

    EvalCard(highlighted = allSel) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (selecting) onToggleSelect() else onOpen(first.id) },
                        onLongClick = onToggleSelect
                    )
                    .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selecting) SelectMark(allSel, partial = selCount > 0 && !allSel)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextMain,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = buildList {
                        if (group.sameLevel) add(first.level)
                        if (group.sameRole) add(first.role)
                        group.meta.totalFee?.let { add("合计 ${money(it)}") }
                    }.joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = AppColors.TextMuted)
                }
                StatusMenuChip(
                    value = group.overallStatus,
                    options = EvalEnums.PROJECT_STATUSES,
                    tone = EvalTones::forStatus,
                    enabled = enabled && !selecting,
                    onSelect = { onStatus(first.id, it) }
                )
            }
            HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
            group.rows.forEach { row ->
                StageLine(
                    row = row,
                    showLevelRole = !group.sameLevel || !group.sameRole,
                    showStage = true,
                    enabled = enabled && !selecting,
                    todayYear = todayYear,
                    onClick = { if (selecting) onToggleSelect() else onOpen(row.project.id) },
                    onLongClick = onToggleSelect,
                    onSettle = { onSettle(row.project.id, it) }
                )
            }
            if (group.missingStage != null && !selecting) {
                TextButton(
                    onClick = { onAddStage(group.missingStage) },
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加${group.missingStage}", fontSize = 13.sp)
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectFlatCard(
    row: ProjectRow,
    selected: Boolean,
    selecting: Boolean,
    enabled: Boolean,
    todayYear: Int,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onStatus: (String) -> Unit,
    onSettle: (String) -> Unit
) {
    val p = row.project
    EvalCard(highlighted = selected) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { if (selecting) onToggleSelect() else onOpen() }, onLongClick = onToggleSelect)
                    .padding(start = 14.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selecting) SelectMark(selected)
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${p.stage} · ${p.level} · ${p.role}", fontSize = 12.sp, color = AppColors.TextMuted)
                }
                StatusMenuChip(
                    value = p.status,
                    options = EvalEnums.PROJECT_STATUSES,
                    tone = EvalTones::forStatus,
                    enabled = enabled && !selecting,
                    onSelect = onStatus
                )
            }
            StageLine(
                row = row,
                showLevelRole = false,
                showStage = false,
                enabled = enabled && !selecting,
                todayYear = todayYear,
                onClick = { if (selecting) onToggleSelect() else onOpen() },
                onLongClick = onToggleSelect,
                onSettle = onSettle
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProjectFilterSheet(
    filter: ProjectFilter,
    years: List<Int>,
    onChange: (ProjectFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Surface) {
        SheetHeader("筛选项目", onReset = onReset, onDone = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("时间（区间有交集即命中）", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectTimeType.entries.forEach { t ->
                    FilterChip(
                        selected = filter.timeType == t,
                        onClick = { onChange(filter.copy(timeType = t)) },
                        label = { Text(t.label, fontSize = 13.sp) }
                    )
                }
            }
            when (filter.timeType) {
                ProjectTimeType.YEAR, ProjectTimeType.QUARTER, ProjectTimeType.MONTH -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val selYear = if (filter.timeType == ProjectTimeType.MONTH) filter.month.year else filter.year
                        years.forEach { y ->
                            FilterChip(
                                selected = selYear == y,
                                onClick = {
                                    onChange(
                                        if (filter.timeType == ProjectTimeType.MONTH) filter.copy(month = YearMonth.of(y, filter.month.monthValue))
                                        else filter.copy(year = y)
                                    )
                                },
                                label = { Text("${y}年", fontSize = 13.sp) }
                            )
                        }
                    }
                    if (filter.timeType == ProjectTimeType.QUARTER) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..4).forEach { q ->
                                FilterChip(
                                    selected = filter.quarter == q,
                                    onClick = { onChange(filter.copy(quarter = q)) },
                                    label = { Text("第${q}季度", fontSize = 13.sp) }
                                )
                            }
                        }
                    }
                    if (filter.timeType == ProjectTimeType.MONTH) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            (1..12).forEach { m ->
                                FilterChip(
                                    selected = filter.month.monthValue == m,
                                    onClick = { onChange(filter.copy(month = YearMonth.of(filter.month.year, m))) },
                                    label = { Text("${m}月", fontSize = 13.sp) }
                                )
                            }
                        }
                    }
                }

                ProjectTimeType.CUSTOM -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField("开始", EvalCore.formatDateSlash(filter.customStart).ifEmpty { "不限" }, Modifier.weight(1f)) { pickStart = true }
                    PickerField("结束", EvalCore.formatDateSlash(filter.customEnd).ifEmpty { "不限" }, Modifier.weight(1f)) { pickEnd = true }
                }

                ProjectTimeType.ALL -> Unit
            }
            HorizontalDivider(color = AppColors.BorderSubtle)
            MultiChipGroup("项目状态", EvalEnums.PROJECT_STATUSES, filter.statuses) { onChange(filter.copy(statuses = it)) }
            MultiChipGroup("费用状态", EvalEnums.SETTLE_STATUSES, filter.settles) { onChange(filter.copy(settles = it)) }
            MultiChipGroup("评估阶段", EvalEnums.STAGES, filter.stages) { onChange(filter.copy(stages = it)) }
            MultiChipGroup("等级", EvalEnums.LEVELS, filter.levels) { onChange(filter.copy(levels = it)) }
            MultiChipGroup("角色", EvalEnums.ROLES, filter.roles) { onChange(filter.copy(roles = it)) }
        }
    }

    if (pickStart) {
        EvalDatePickerDialog(
            initial = filter.customStart,
            onDismiss = { pickStart = false },
            onClear = { onChange(filter.copy(customStart = "")) }
        ) { onChange(filter.copy(customStart = it)) }
    }
    if (pickEnd) {
        EvalDatePickerDialog(
            initial = filter.customEnd.ifEmpty { filter.customStart },
            onDismiss = { pickEnd = false },
            onClear = { onChange(filter.copy(customEnd = "")) }
        ) { onChange(filter.copy(customEnd = it)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSortSheet(
    sort: ProjectSort,
    merged: Boolean,
    onSort: (ProjectSort) -> Unit,
    onMerged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Surface) {
        SheetHeader("排序与显示", onReset = null, onDone = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMerged(!merged) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("合并同名项目", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("预评估与正式评估合在一张卡片里，与网页端同步", fontSize = 12.sp, color = AppColors.TextMuted)
                }
                Switch(checked = merged, onCheckedChange = onMerged)
            }
            HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !sort.ascending,
                        onClick = { onSort(sort.copy(ascending = false)) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("降序") }
                    SegmentedButton(
                        selected = sort.ascending,
                        onClick = { onSort(sort.copy(ascending = true)) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("升序") }
                }
            }
            ProjectSortField.entries.forEach { field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(0.dp))
                        .clickable { onSort(sort.copy(field = field)) }
                        .background(if (sort.field == field) AppColors.PrimarySoft else AppColors.Surface)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = sort.field == field, onClick = { onSort(sort.copy(field = field)) })
                    Text(field.label, fontSize = 15.sp)
                }
            }
        }
    }
}
