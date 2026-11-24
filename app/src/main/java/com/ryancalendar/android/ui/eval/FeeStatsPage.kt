package com.ryancalendar.android.ui.eval

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.eval.EvalCore
import com.ryancalendar.android.domain.eval.EvalEnums
import com.ryancalendar.android.domain.eval.FeeFilter
import com.ryancalendar.android.domain.eval.FeeGroupBy
import com.ryancalendar.android.domain.eval.FeeGroupRow
import com.ryancalendar.android.domain.eval.RangePreset
import com.ryancalendar.android.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeeStatsPage(viewModel: EvalViewModel, onOpenProject: (String) -> Unit) {
    val stats by viewModel.feeStats.collectAsState()
    val filter by viewModel.feeFilter.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var showFilter by rememberSaveable { mutableStateOf(false) }
    var showRange by rememberSaveable { mutableStateOf(false) }
    var selectedMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var drillKey by rememberSaveable { mutableStateOf<String?>(null) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh(showToast = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "range") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChoiceChipRow(
                            options = RangePreset.entries,
                            selected = filter.preset,
                            label = { it.label },
                            onSelect = { p -> if (p == RangePreset.CUSTOM) showRange = true else viewModel.setFeePreset(p) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showFilter = true }) {
                            BadgedBox(badge = { if (filter.activeCount > 0) Badge { Text("${filter.activeCount}") } }) {
                                Icon(Icons.Outlined.FilterList, contentDescription = "筛选")
                            }
                        }
                    }
                    Text(
                        text = "按项目开始时间：" + rangeLabel(filter.start, filter.end),
                        fontSize = 12.sp,
                        color = AppColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    ActiveFilterRow(filters = activeFeeFilters(filter, viewModel), onClearAll = {
                        viewModel.updateFeeFilter { it.copy(levels = emptySet(), roles = emptySet(), settles = emptySet(), statuses = emptySet()) }
                    })
                }
            }

            val s = stats
            if (s == null) {
                item(key = "loading") { EmptyState("正在计算…", "") }
                return@LazyColumn
            }

            item(key = "kpi") {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    KpiGrid(
                        columns = 3,
                        items = listOf(
                            KpiItem("项目数", "${s.projectCount}", "${s.recordCount} 条记录"),
                            KpiItem("评估天数", "${s.totalDays} 天"),
                            KpiItem(
                                "费用合计",
                                money(s.totalFee),
                                if (s.pendingRateProjectCount > 0) "${s.pendingRateProjectCount} 个待设置" else "已剔除待设置"
                            ),
                            KpiItem("已结金额", money(s.settledFee), "占比 " + pctText(s.settledFee, s.totalFee), EvalTones.GreenText),
                            KpiItem("未结金额", money(s.unsettledFee), "占比 " + pctText(s.unsettledFee, s.totalFee), EvalTones.OrangeText),
                            KpiItem("未结项目", "${s.unsettledProjectCount}", "含未结阶段", EvalTones.OrangeText)
                        )
                    )
                }
            }

            item(key = "chart") {
                EvalCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        SectionTitle("月度评估费用分布", "按项目开始时间归属月份 · 点柱子看明细")
                        if (s.monthly.isEmpty()) {
                            Text(
                                "当前区间内暂无月度费用数据",
                                fontSize = 13.sp,
                                color = AppColors.TextMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        } else {
                            val idx = s.monthly.indexOfFirst { it.month == selectedMonth }.takeIf { it >= 0 }
                            VerticalBarChart(
                                columns = s.monthly.map { m ->
                                    BarColumn(
                                        label = shortMonthLabel(m.month),
                                        topLabel = compactMoney(m.total),
                                        segments = listOf(
                                            BarSegment(m.settled, EvalTones.GreenText),
                                            BarSegment(m.unsettled, EvalTones.OrangeText)
                                        )
                                    )
                                },
                                selectedIndex = idx,
                                onSelect = { i -> selectedMonth = s.monthly[i].month.takeIf { it != selectedMonth } }
                            )
                            Spacer(Modifier.height(8.dp))
                            LegendRow(listOf("已结" to EvalTones.GreenText, "未结" to EvalTones.OrangeText))
                            if (idx != null) {
                                val m = s.monthly[idx]
                                Text(
                                    "${m.month}：合计 ${money(m.total)} · 已结 ${money(m.settled)} · 未结 ${money(m.unsettled)}",
                                    fontSize = 13.sp,
                                    color = AppColors.TextMain,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "groupHead") {
                Column {
                    Text(
                        "分组统计",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    ChoiceChipRow(
                        options = FeeGroupBy.entries,
                        selected = filter.groupBy,
                        label = { "按" + it.label },
                        onSelect = viewModel::setFeeGroupBy
                    )
                }
            }

            if (s.groups.isEmpty()) {
                item(key = "noGroups") { EmptyState("暂无费用记录", "调整区间或筛选条件后再看") }
            } else {
                item(key = "groups") {
                    EvalCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Column {
                            s.groups.forEachIndexed { i, g ->
                                if (i > 0) HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                                ShareBarRow(
                                    title = g.key,
                                    value = money(g.fee),
                                    detail = "${g.projectCount} 个项目 · ${g.days} 天 · 已结 ${money(g.settled)} · 未结 ${money(g.unsettled)}",
                                    fraction = if (s.totalFee > 0) (g.fee / s.totalFee).toFloat() else 0f,
                                    pct = pctText(g.fee, s.totalFee),
                                    onClick = { drillKey = g.key }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val drillGroup = stats?.groups?.find { it.key == drillKey }
    if (drillGroup != null) {
        FeeGroupDrillSheet(
            group = drillGroup,
            groupLabel = filter.groupBy.label,
            viewModel = viewModel,
            snapshotProjects = snapshot.projects,
            onOpenProject = {
                drillKey = null
                onOpenProject(it)
            },
            onDismiss = { drillKey = null }
        )
    }

    if (showFilter) {
        FeeFilterSheet(filter, onChange = { f -> viewModel.updateFeeFilter { f } }, onDismiss = { showFilter = false })
    }
    if (showRange) {
        EvalDateRangePickerDialog(
            initialStart = filter.start,
            initialEnd = filter.end,
            onDismiss = { showRange = false },
            onConfirm = viewModel::setFeeCustomRange
        )
    }
}

fun rangeLabel(start: String, end: String): String = when {
    start.isEmpty() && end.isEmpty() -> "全部时间"
    else -> EvalCore.formatDateSlash(start).ifEmpty { "不限" } + " ~ " + EvalCore.formatDateSlash(end).ifEmpty { "不限" }
}

/** Short labels on top of bars: 12345 -> "1.2万", 980 -> "980". */
fun compactMoney(n: Double): String = when {
    n >= 10000 -> "%.1f万".format(n / 10000)
    n > 0 -> "%.0f".format(n)
    else -> ""
}

private fun activeFeeFilters(f: FeeFilter, vm: EvalViewModel): List<ActiveFilter> = buildList {
    if (f.levels.isNotEmpty()) add(ActiveFilter(selectionLabel("等级", f.levels)) { vm.updateFeeFilter { it.copy(levels = emptySet()) } })
    if (f.roles.isNotEmpty()) add(ActiveFilter(selectionLabel("角色", f.roles)) { vm.updateFeeFilter { it.copy(roles = emptySet()) } })
    if (f.settles.isNotEmpty()) add(ActiveFilter(selectionLabel("费用", f.settles)) { vm.updateFeeFilter { it.copy(settles = emptySet()) } })
    if (f.statuses.isNotEmpty()) add(ActiveFilter(selectionLabel("状态", f.statuses)) { vm.updateFeeFilter { it.copy(statuses = emptySet()) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeeFilterSheet(filter: FeeFilter, onChange: (FeeFilter) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Surface) {
        SheetHeader(
            "筛选费用统计",
            onReset = { onChange(filter.copy(levels = emptySet(), roles = emptySet(), settles = emptySet(), statuses = emptySet())) },
            onDone = onDismiss
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MultiChipGroup("费用状态", EvalEnums.SETTLE_STATUSES, filter.settles) { onChange(filter.copy(settles = it)) }
            MultiChipGroup("项目状态", EvalEnums.PROJECT_STATUSES, filter.statuses) { onChange(filter.copy(statuses = it)) }
            MultiChipGroup("等级", EvalEnums.LEVELS, filter.levels) { onChange(filter.copy(levels = it)) }
            MultiChipGroup("角色", EvalEnums.ROLES, filter.roles) { onChange(filter.copy(roles = it)) }
        }
    }
}

/** Lists the records behind one group row so a number can be traced back to projects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeeGroupDrillSheet(
    group: FeeGroupRow,
    groupLabel: String,
    viewModel: EvalViewModel,
    snapshotProjects: List<com.ryancalendar.android.domain.eval.EvalProject>,
    onOpenProject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val settings = viewModel.snapshot.value.settings
    val ids = group.projectIds.toHashSet()
    val records = snapshotProjects.filter { it.id in ids }.sortedByDescending { it.start }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppColors.Surface) {
        SheetHeader("$groupLabel：${group.key}", onReset = null, onDone = onDismiss)
        Text(
            "${group.projectCount} 个项目 · ${records.size} 条记录 · ${money(group.fee)}",
            fontSize = 12.sp,
            color = AppColors.TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(records, key = { it.id }) { p ->
                val fee = EvalCore.effectiveFee(p, settings)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(p.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p.stage} · ${EvalCore.formatDateSlash(p.start).ifEmpty { "未排期" }} · ${fee.days}天",
                            fontSize = 12.sp,
                            color = AppColors.TextMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (fee.pendingRate) "待设置" else money(fee.fee), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        TonePill(p.settle, EvalTones.forSettle(p.settle))
                    }
                }
                HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
