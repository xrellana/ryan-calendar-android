package com.ryancalendar.android.ui.eval

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.eval.EvalCore
import com.ryancalendar.android.domain.eval.EvalEnums
import com.ryancalendar.android.domain.eval.EvalTravel
import com.ryancalendar.android.domain.eval.RangePreset
import com.ryancalendar.android.domain.eval.TravelFilter
import com.ryancalendar.android.domain.eval.TravelGroup
import com.ryancalendar.android.domain.eval.TravelGroupBy
import com.ryancalendar.android.ui.theme.AppColors

fun travelCatIcon(cat: String): ImageVector = when (cat) {
    "打车" -> Icons.Outlined.LocalTaxi
    "高铁" -> Icons.Outlined.Train
    "飞机" -> Icons.Outlined.Flight
    "自驾" -> Icons.Outlined.DirectionsCar
    "住宿" -> Icons.Outlined.Hotel
    "餐饮" -> Icons.Outlined.Restaurant
    else -> Icons.Outlined.Payments
}

private const val NAME_CHIP_PREVIEW = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelsPage(
    viewModel: EvalViewModel,
    onOpenTravel: (String) -> Unit,
    onNewTravel: () -> Unit
) {
    val ui by viewModel.travelListUi.collectAsState()
    val filter by viewModel.travelFilter.collectAsState()
    val selected by viewModel.selectedTravelIds.collectAsState()
    val merge by viewModel.travelMerge.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    val today by viewModel.today.collectAsState()
    val todayYear = EvalCore.parseDate(today)?.year ?: 0

    var showSummary by rememberSaveable { mutableStateOf(false) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        SearchFilterBar(
            query = filter.query,
            onQueryChange = { q -> viewModel.updateTravelFilter { it.copy(query = q) } },
            placeholder = "搜索差旅",
            filterCount = filter.activeCount,
            onOpenFilter = { showFilter = true }
        ) {
            IconButton(onClick = { viewModel.updateTravelFilter { it.copy(ascending = !it.ascending) } }) {
                Icon(
                    if (filter.ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                    contentDescription = if (filter.ascending) "按日期升序" else "按日期降序"
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "更多") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("合并同项目差旅") },
                        trailingIcon = { Switch(checked = merge, onCheckedChange = null) },
                        onClick = {
                            viewModel.setTravelMerge(!merge)
                            menuOpen = false
                        }
                    )
                }
            }
        }
        ActiveFilterRow(filters = activeTravelFilters(filter, viewModel), onClearAll = viewModel::resetTravelFilter)

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
                    val t = ui.totals
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ui.isSelectionScope) {
                            ScopePill("统计范围：已选 ${t.count} 笔 · ${t.projectCount} 个项目", viewModel::clearTravelSelection)
                        }
                        KpiGrid(
                            columns = 2,
                            highlighted = ui.isSelectionScope,
                            items = listOf(
                                KpiItem("差旅总额", money(t.total), "${t.count} 笔 · ${t.projectCount} 个项目"),
                                KpiItem(
                                    "未报", money(t.unrep), "${t.unrepCount} 笔待报销 · 点击筛选", EvalTones.OrangeText,
                                    onClick = { viewModel.toggleTravelReimbShortcut(EvalEnums.REIMB_NONE) },
                                    active = filter.reimbs == setOf(EvalEnums.REIMB_NONE)
                                ),
                                KpiItem(
                                    "已报待结", money(t.rep), "${t.repCount} 笔已提交 · 点击筛选", EvalTones.BlueText,
                                    onClick = { viewModel.toggleTravelReimbShortcut(EvalEnums.REIMB_SUBMITTED) },
                                    active = filter.reimbs == setOf(EvalEnums.REIMB_SUBMITTED)
                                ),
                                KpiItem(
                                    "已结", money(t.done), "${t.doneCount} 笔已结算 · 点击筛选", EvalTones.GreenText,
                                    onClick = { viewModel.toggleTravelReimbShortcut(EvalEnums.REIMB_DONE) },
                                    active = filter.reimbs == setOf(EvalEnums.REIMB_DONE)
                                )
                            )
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !showSummary,
                                onClick = { showSummary = false },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) { Text("明细") }
                            SegmentedButton(
                                selected = showSummary,
                                onClick = { showSummary = true },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) { Text("汇总") }
                        }
                    }
                }

                when {
                    !ui.loaded && ui.totalCount == 0 -> item(key = "loading") {
                        EmptyState("正在加载差旅记录…", "首次打开需要从云端同步数据")
                    }

                    ui.totalCount == 0 -> item(key = "empty") {
                        EmptyState(
                            "还没有差旅记录",
                            "登记交通、住宿、餐饮等支出并跟踪报销状态，选项目时会自动带出等级与评估计划。",
                            action = { OutlinedButton(onClick = onNewTravel) { Text("记一笔") } }
                        )
                    }

                    ui.filteredCount == 0 -> item(key = "nomatch") {
                        EmptyState(
                            "没有符合条件的差旅",
                            "调整筛选条件，或清空后查看全部。",
                            action = { OutlinedButton(onClick = viewModel::resetTravelFilter) { Text("清空筛选") } }
                        )
                    }

                    showSummary -> {
                        item(key = "cats") { TravelCategoryCard(ui.categories, ui.totals.total) }
                        item(key = "sumHead") {
                            Column {
                                Text(
                                    "差旅汇总",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                                ChoiceChipRow(
                                    options = TravelGroupBy.entries,
                                    selected = filter.groupBy,
                                    label = { "按" + it.label },
                                    onSelect = viewModel::setTravelGroupBy,
                                    modifier = Modifier.padding(start = 0.dp)
                                )
                            }
                        }
                        item(key = "sum") {
                            EvalCard {
                                Column {
                                    ui.summary.forEachIndexed { i, r ->
                                        if (i > 0) HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                                        ShareBarRow(
                                            title = r.key,
                                            value = money(r.total),
                                            detail = "${r.count} 笔 · 未报 ${money(r.unrep)} · 已报 ${money(r.rep)} · 已结 ${money(r.done)}",
                                            fraction = if (ui.totals.total > 0) (r.total / ui.totals.total).toFloat() else 0f,
                                            pct = pctText(r.total, ui.totals.total)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> items(ui.groups, key = { it.key }) { g ->
                        TravelGroupCard(
                            group = g,
                            selected = selected,
                            selecting = selecting,
                            enabled = !isMutating,
                            todayYear = todayYear,
                            onToggle = viewModel::toggleTravelSelection,
                            onOpen = onOpenTravel,
                            onReimb = viewModel::setTravelReimb
                        )
                    }
                }

                if (!showSummary && ui.filteredCount > 0 && !selecting) {
                    item(key = "hint") {
                        Text(
                            "长按差旅可多选，批量修改报销状态",
                            fontSize = 11.sp,
                            color = AppColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showFilter) {
        TravelFilterSheet(
            filter = filter,
            nameOptions = ui.nameOptions,
            catOptions = ui.catOptions,
            viewModel = viewModel,
            onDismiss = { showFilter = false }
        )
    }
}

private fun activeTravelFilters(f: TravelFilter, vm: EvalViewModel): List<ActiveFilter> = buildList {
    if (f.preset != RangePreset.ALL) {
        val label = if (f.preset == RangePreset.CUSTOM) rangeLabel(f.start, f.end) else f.preset.label
        add(ActiveFilter(label) { vm.setTravelPreset(RangePreset.ALL) })
    }
    if (f.names.isNotEmpty()) add(ActiveFilter(selectionLabel("项目", f.names)) { vm.updateTravelFilter { it.copy(names = emptySet()) } })
    if (f.cats.isNotEmpty()) add(ActiveFilter(selectionLabel("类别", f.cats)) { vm.updateTravelFilter { it.copy(cats = emptySet()) } })
    if (f.reimbs.isNotEmpty()) add(ActiveFilter(selectionLabel("报销", f.reimbs)) { vm.updateTravelFilter { it.copy(reimbs = emptySet()) } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TravelGroupCard(
    group: TravelGroup,
    selected: Set<String>,
    selecting: Boolean,
    enabled: Boolean,
    todayYear: Int,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onReimb: (String, String) -> Unit
) {
    val anySelected = group.rows.any { it.id in selected }
    EvalCard(highlighted = anySelected && group.rows.all { it.id in selected }) {
        Column {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp)) {
                Text(
                    group.first.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOf(group.first.stage, group.link.level, group.link.planText)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(meta.ifEmpty { "—" }, fontSize = 12.sp, color = AppColors.TextMuted, modifier = Modifier.weight(1f))
                    if (group.rows.size > 1) {
                        Text(
                            "${group.rows.size} 笔 · ${money(group.rows.fold(0.0) { acc, t -> EvalCore.round2(acc + t.amount) })}",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }
            HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
            group.rows.forEach { t ->
                TravelLine(
                    t = t,
                    selected = t.id in selected,
                    selecting = selecting,
                    enabled = enabled && !selecting,
                    todayYear = todayYear,
                    onClick = { if (selecting) onToggle(t.id) else onOpen(t.id) },
                    onLongClick = { onToggle(t.id) },
                    onReimb = { onReimb(t.id, it) }
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TravelLine(
    t: EvalTravel,
    selected: Boolean,
    selecting: Boolean,
    enabled: Boolean,
    todayYear: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onReimb: (String) -> Unit
) {
    val cat = EvalCore.travelDisplayCat(t)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) AppColors.Primary else AppColors.BorderStrong,
                modifier = Modifier.padding(end = 10.dp).size(22.dp)
            )
        }
        Icon(
            travelCatIcon(t.cat),
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(money(t.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("$cat · ${EvalCore.formatDateShort(t.date, todayYear)}", fontSize = 12.sp, color = AppColors.TextMuted)
            }
            Text(
                t.detail.ifBlank { "—" },
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusMenuChip(
            value = t.reimb,
            options = EvalEnums.REIMB_STATUSES,
            tone = EvalTones::forReimb,
            enabled = enabled,
            onSelect = onReimb
        )
    }
}

@Composable
private fun TravelCategoryCard(rows: List<com.ryancalendar.android.domain.eval.TravelCatRow>, total: Double) {
    EvalCard {
        Column(Modifier.padding(bottom = 6.dp)) {
            SectionTitle("差旅费用结构", "按类别金额降序 · 随筛选或勾选范围变化")
            if (rows.isEmpty()) {
                Text("暂无差旅支出数据", fontSize = 13.sp, color = AppColors.TextMuted, modifier = Modifier.padding(14.dp))
            }
            rows.forEach { r ->
                ShareBarRow(
                    title = r.name,
                    value = money(r.amount),
                    detail = "${r.count} 笔",
                    fraction = if (total > 0) (r.amount / total).toFloat() else 0f,
                    pct = pctText(r.amount, total)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TravelFilterSheet(
    filter: TravelFilter,
    nameOptions: List<String>,
    catOptions: List<String>,
    viewModel: EvalViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRange by remember { mutableStateOf(false) }
    var showAllNames by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Surface) {
        SheetHeader("筛选差旅", onReset = viewModel::resetTravelFilter, onDone = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("差旅日期", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangePreset.entries.forEach { p ->
                    FilterChip(
                        selected = filter.preset == p,
                        onClick = { if (p == RangePreset.CUSTOM) showRange = true else viewModel.setTravelPreset(p) },
                        label = {
                            Text(
                                if (p == RangePreset.CUSTOM && filter.preset == p) rangeLabel(filter.start, filter.end) else p.label,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }
            MultiChipGroup("报销状态", EvalEnums.REIMB_STATUSES, filter.reimbs) { v -> viewModel.updateTravelFilter { it.copy(reimbs = v) } }
            MultiChipGroup("差旅类别", catOptions, filter.cats) { v -> viewModel.updateTravelFilter { it.copy(cats = v) } }
            val names = if (showAllNames) nameOptions else nameOptions.take(NAME_CHIP_PREVIEW).let { preview ->
                // Keep already-selected names visible even when collapsed.
                preview + filter.names.filter { it !in preview }
            }
            MultiChipGroup("项目", names, filter.names) { v -> viewModel.updateTravelFilter { it.copy(names = v) } }
            if (nameOptions.size > NAME_CHIP_PREVIEW) {
                TextButton(onClick = { showAllNames = !showAllNames }) {
                    Text(if (showAllNames) "收起" else "显示全部 ${nameOptions.size} 个项目")
                }
            }
        }
    }

    if (showRange) {
        EvalDateRangePickerDialog(
            initialStart = filter.start,
            initialEnd = filter.end,
            onDismiss = { showRange = false },
            onConfirm = viewModel::setTravelCustomRange
        )
    }
}
