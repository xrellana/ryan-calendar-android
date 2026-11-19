package com.ryancalendar.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.calendar.WeekLayoutEngine
import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.util.DateUtils
import com.ryancalendar.android.ui.AppViewModel
import com.ryancalendar.android.ui.components.CategoryFilterBottomSheet
import com.ryancalendar.android.ui.components.EventListCard
import com.ryancalendar.android.ui.theme.AppColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(
    viewModel: AppViewModel,
    onOpenEvent: (eventId: String) -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val filteredEvents by viewModel.filteredEvents.collectAsState()
    val filterIds by viewModel.selectedFilterCategoryIds.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var rangeMode by rememberSaveable { mutableStateOf("7d") } // "7d" | "30d"
    var showFilterSheet by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val todayStr = today.toString()
    val rangeEndDate = remember(rangeMode, today) {
        if (rangeMode == "30d") today.plusMonths(1) else today.plusDays(6)
    }
    val rangeEndStr = rangeEndDate.toString()

    val priorityMap = remember(categories) {
        categories.mapIndexed { idx, cat -> cat.id to idx }.toMap()
    }
    val categoryMap = remember(categories) {
        categories.associateBy { it.id }
    }

    val upcomingEvents = remember(filteredEvents, todayStr, rangeEndStr, priorityMap) {
        filteredEvents
            .filter { it.effectiveEndDate >= todayStr && it.startDate <= rangeEndStr }
            .sortedWith { a, b ->
                if (a.startDate != b.startDate) a.startDate.compareTo(b.startDate)
                else WeekLayoutEngine.compareEvents(a, b, priorityMap)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CanvasBg)
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
            title = {
                Text("近期提醒", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            actions = {
                IconButton(onClick = { showFilterSheet = true }) {
                    BadgedBox(
                        badge = {
                            if (filterIds.isNotEmpty()) {
                                Badge(containerColor = AppColors.Primary) {
                                    Text("${filterIds.size}", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = "按类别筛选",
                            tint = if (filterIds.isNotEmpty()) AppColors.Primary else AppColors.TextMain
                        )
                    }
                }
            }
        )

        Surface(color = AppColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rangeMode == "7d",
                        onClick = { rangeMode = "7d" },
                        label = { Text("今起 7 天") }
                    )
                    FilterChip(
                        selected = rangeMode == "30d",
                        onClick = { rangeMode = "30d" },
                        label = { Text("今起 1 个月") }
                    )
                }
                Text(
                    text = "共 ${upcomingEvents.size} 条",
                    fontSize = 12.sp,
                    color = AppColors.TextMuted
                )
            }
        }
        HorizontalDivider(color = AppColors.BorderSubtle)

        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.manualSync() },
            modifier = Modifier.weight(1f)
        ) {
            if (upcomingEvents.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (rangeMode == "30d") "未来一个月内暂无待办日程" else "未来 7 天内暂无待办日程",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "范围：${DateUtils.formatShortMD(todayStr)} - ${DateUtils.formatShortMD(rangeEndStr)}",
                        fontSize = 12.sp,
                        color = AppColors.TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "range_meta") {
                        Text(
                            text = "统计范围：${DateUtils.formatShortMD(todayStr)} - ${DateUtils.formatShortMD(rangeEndStr)}",
                            fontSize = 12.sp,
                            color = AppColors.TextMuted,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    items(upcomingEvents, key = { it.id }) { ev ->
                        val cat = categoryMap[ev.categoryId] ?: CalendarCategory.Fallback
                        EventListCard(
                            event = ev,
                            category = cat,
                            showDateRange = true,
                            relativeTag = DateUtils.relativeTag(ev.startDate, ev.endDate, today),
                            onClick = { onOpenEvent(ev.id) }
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        CategoryFilterBottomSheet(
            categories = categories,
            selectedIds = filterIds,
            onToggle = { viewModel.toggleFilterCategory(it) },
            onClear = { viewModel.clearCategoryFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }
}
