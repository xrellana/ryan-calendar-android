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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.ryancalendar.android.domain.eval.DaysWindowMode
import com.ryancalendar.android.domain.eval.EvalCore
import com.ryancalendar.android.domain.eval.ProgressRow
import com.ryancalendar.android.ui.theme.AppColors

private const val PROGRESS_PREVIEW_COUNT = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaysStatsPage(viewModel: EvalViewModel, onOpenProject: (String) -> Unit) {
    val stats by viewModel.daysStats.collectAsState()
    val window by viewModel.daysWindow.collectAsState()
    val today by viewModel.today.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val todayYear = EvalCore.parseDate(today)?.year ?: 0

    var showRange by rememberSaveable { mutableStateOf(false) }
    var recentOpen by rememberSaveable { mutableStateOf(true) }
    var chartOpen by rememberSaveable { mutableStateOf(true) }
    var progressOpen by rememberSaveable { mutableStateOf(true) }
    var progressAll by rememberSaveable { mutableStateOf(false) }
    var selectedMonth by rememberSaveable { mutableStateOf<String?>(null) }

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
            val s = stats
            item(key = "window") {
                Column {
                    ChoiceChipRow(
                        options = DaysWindowMode.entries,
                        selected = window.mode,
                        label = { it.label },
                        onSelect = { m -> if (m == DaysWindowMode.CUSTOM) showRange = true else viewModel.setDaysWindow(m) }
                    )
                    if (s != null) {
                        Text(
                            "近期窗口：" + rangeLabel(s.windowStart, s.windowEnd) + " · 今天 " + EvalCore.formatDateSlash(today),
                            fontSize = 12.sp,
                            color = AppColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            if (s == null) {
                item(key = "loading") { EmptyState("正在计算…", "") }
                return@LazyColumn
            }

            item(key = "kpi") {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    KpiGrid(
                        columns = 3,
                        items = listOf(
                            KpiItem("今日在评估", "${s.todayInEval}", "个项目", EvalTones.BlueText),
                            KpiItem("窗口内项目", "${s.windowProjectCount}", window.mode.label),
                            KpiItem("累计已评估", "${s.elapsedTotal} 天", "截至今天", EvalTones.GreenText),
                            KpiItem("本月评估", "${s.thisMonthDays} 天", today.take(7)),
                            KpiItem("未来计划", "${s.futurePlanDays} 天", "尚未开始", EvalTones.BlueText),
                            KpiItem("全部计划", "${s.allPlanDays} 天", "${s.uniqueProjectCount} 个项目")
                        )
                    )
                }
            }

            item(key = "recent") {
                CollapsibleCard(
                    title = "近期评估情况（${s.recent.size}）",
                    subtitle = "按开始时间升序",
                    open = recentOpen,
                    onToggle = { recentOpen = !recentOpen }
                ) {
                    if (s.recent.isEmpty()) {
                        Text("所选时间窗口内暂无评估安排", fontSize = 13.sp, color = AppColors.TextMuted, modifier = Modifier.padding(14.dp))
                    }
                    s.recent.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                        ProgressItem(r, todayYear, showCounts = false) { onOpenProject(r.project.id) }
                    }
                }
            }

            item(key = "chart") {
                CollapsibleCard(
                    title = "月度评估天数分布",
                    subtitle = "跨月项目按实际重叠天数拆分 · 点柱子看明细",
                    open = chartOpen,
                    onToggle = { chartOpen = !chartOpen }
                ) {
                    if (s.monthly.isEmpty()) {
                        Text("暂无已排期项目", fontSize = 13.sp, color = AppColors.TextMuted, modifier = Modifier.padding(14.dp))
                    } else {
                        val idx = s.monthly.indexOfFirst { it.month == selectedMonth }.takeIf { it >= 0 }
                        VerticalBarChart(
                            columns = s.monthly.map { m ->
                                BarColumn(
                                    label = shortMonthLabel(m.month),
                                    topLabel = "${m.days}",
                                    segments = listOf(BarSegment(m.days.toDouble(), AppColors.Primary)),
                                    dimmed = m.isFuture
                                )
                            },
                            selectedIndex = idx,
                            onSelect = { i -> selectedMonth = s.monthly[i].month.takeIf { it != selectedMonth } }
                        )
                        Spacer(Modifier.height(6.dp))
                        LegendRow(listOf("已过月份" to AppColors.Primary, "未来月份" to AppColors.Primary.copy(alpha = 0.45f)))
                        val m = idx?.let { s.monthly[it] }
                        Text(
                            text = if (m != null) {
                                "${m.month}：${m.days} 天 · 涵盖 ${m.projectCount} 个项目 · 已完成 ${m.doneDays} 天 · 进行中 ${m.doingDays} 天"
                            } else {
                                "共 ${s.monthly.size} 个月有评估安排"
                            },
                            fontSize = 13.sp,
                            color = if (m != null) AppColors.TextMain else AppColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            item(key = "progress") {
                CollapsibleCard(
                    title = "执行进度与未来计划（${s.progress.size}）",
                    subtitle = "按开始时间倒序",
                    open = progressOpen,
                    onToggle = { progressOpen = !progressOpen }
                ) {
                    val list = if (progressAll) s.progress else s.progress.take(PROGRESS_PREVIEW_COUNT)
                    if (list.isEmpty()) {
                        Text("暂无已排期项目", fontSize = 13.sp, color = AppColors.TextMuted, modifier = Modifier.padding(14.dp))
                    }
                    list.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                        ProgressItem(r, todayYear, showCounts = true) { onOpenProject(r.project.id) }
                    }
                    if (s.progress.size > PROGRESS_PREVIEW_COUNT) {
                        TextButton(onClick = { progressAll = !progressAll }, modifier = Modifier.padding(start = 4.dp)) {
                            Text(if (progressAll) "收起" else "查看全部 ${s.progress.size} 条")
                        }
                    }
                }
            }
        }
    }

    if (showRange) {
        EvalDateRangePickerDialog(
            initialStart = window.customStart.ifEmpty { today },
            initialEnd = window.customEnd.ifEmpty { EvalCore.addDays(today, 6) },
            onDismiss = { showRange = false },
            onConfirm = viewModel::setDaysCustomRange
        )
    }
}

@Composable
private fun CollapsibleCard(
    title: String,
    subtitle: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    EvalCard(modifier = Modifier.padding(horizontal = 12.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) { SectionTitle(title, subtitle) }
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (open) "收起" else "展开",
                    tint = AppColors.TextMuted,
                    modifier = Modifier.padding(end = 14.dp)
                )
            }
            if (open) {
                content()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ProgressItem(r: ProgressRow, todayYear: Int, showCounts: Boolean, onClick: () -> Unit) {
    val p = r.project
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TonePill(p.status, EvalTones.forStatus(p.status))
        }
        Text(
            "${p.stage} · ${p.level} · ${p.role} · " +
                EvalCore.formatDateShort(p.start, todayYear) +
                (if (r.effEnd != p.start) " – " + EvalCore.formatDateShort(r.effEnd, todayYear) else ""),
            fontSize = 12.sp,
            color = AppColors.TextMuted
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressLine(r.pct, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                if (showCounts) "${r.elapsed}/${r.days} 天 · 剩 ${r.remaining}" else "${r.pct}% · ${r.days} 天",
                fontSize = 11.sp,
                color = AppColors.TextSecondary
            )
        }
    }
}
