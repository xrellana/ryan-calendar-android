package com.ryancalendar.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.calendar.HolidayIndex
import com.ryancalendar.android.domain.calendar.LunarCalendar
import com.ryancalendar.android.domain.calendar.WeekLayoutEngine
import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.model.CalendarEvent
import com.ryancalendar.android.domain.util.ColorUtils
import com.ryancalendar.android.domain.util.DateUtils
import com.ryancalendar.android.ui.AppViewModel
import com.ryancalendar.android.ui.CalendarViewMode
import com.ryancalendar.android.ui.components.CategoryFilterBottomSheet
import com.ryancalendar.android.ui.components.EventListCard
import com.ryancalendar.android.ui.theme.AppColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private val BASE_YEAR_MONTH = YearMonth.of(2000, 1)
private const val TOTAL_MONTH_PAGES = 1212 // 2000-01 .. 2100-12
private val BASE_WEEK_MONDAY = LocalDate.of(2000, 1, 3) // Monday
private const val TOTAL_WEEK_PAGES = 5260

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: AppViewModel,
    onOpenSearch: () -> Unit,
    onOpenEventEditor: (eventId: String?, initialDate: LocalDate) -> Unit
) {
    val viewMode by viewModel.viewMode.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val visibleMonth by viewModel.visibleMonth.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val filteredEvents by viewModel.filteredEvents.collectAsState()
    val filterIds by viewModel.selectedFilterCategoryIds.collectAsState()
    val holidays by viewModel.holidays.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showMonthJumpSheet by remember { mutableStateOf(false) }

    val priorityMap = remember(categories) {
        categories.mapIndexed { idx, cat -> cat.id to idx }.toMap()
    }
    val categoryMap = remember(categories) {
        categories.associateBy { it.id }
    }

    val topTitle = when (viewMode) {
        CalendarViewMode.MONTH -> "${visibleMonth.year}年${visibleMonth.monthValue}月"
        CalendarViewMode.WEEK -> {
            val mon = DateUtils.mondayOfWeek(selectedDate)
            val sun = mon.plusDays(6)
            if (mon.monthValue == sun.monthValue) {
                "${mon.year}年${mon.monthValue}月"
            } else {
                "${mon.monthValue}月-${sun.monthValue}月"
            }
        }
        CalendarViewMode.YEAR -> "${visibleMonth.year}年"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CanvasBg)
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showMonthJumpSheet = true }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = topTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextMain
                    )
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "选择年月",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            actions = {
                TextButton(
                    onClick = { viewModel.jumpToday() },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("今", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "搜索日程",
                        tint = AppColors.TextMain
                    )
                }
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
                            contentDescription = "筛选类别",
                            tint = if (filterIds.isNotEmpty()) AppColors.Primary else AppColors.TextMain
                        )
                    }
                }
            }
        )

        // 视图模式切换条（月 / 周 / 年）+ 激活筛选提示
        Surface(
            color = AppColors.Surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.SurfaceVariant)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CalendarViewMode.entries.forEach { mode ->
                        val active = viewMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AppColors.Surface else Color.Transparent)
                                .clickable { viewModel.setViewMode(mode) }
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (active) AppColors.Primary else AppColors.TextSecondary
                            )
                        }
                    }
                }

                if (filterIds.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearCategoryFilters() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("已筛选 ${filterIds.size} 类 · 清除", fontSize = 12.sp, color = AppColors.Primary)
                    }
                } else if (!holidays.isYearCovered(visibleMonth.year)) {
                    Text(
                        text = "${visibleMonth.year}年暂无法定调休表",
                        fontSize = 11.sp,
                        color = AppColors.TextMuted
                    )
                }
            }
        }
        HorizontalDivider(color = AppColors.BorderSubtle)

        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.manualSync() },
            modifier = Modifier.weight(1f)
        ) {
            when (viewMode) {
                CalendarViewMode.MONTH -> MonthCalendarContent(
                    visibleMonth = visibleMonth,
                    selectedDate = selectedDate,
                    events = filteredEvents,
                    categoryMap = categoryMap,
                    priorityMap = priorityMap,
                    holidays = holidays,
                    onMonthChanged = { viewModel.setVisibleMonth(it) },
                    onSelectDate = { viewModel.selectDate(it) },
                    onOpenEvent = { ev -> onOpenEventEditor(ev.id, selectedDate) },
                    onAddEventForSelected = { onOpenEventEditor(null, selectedDate) }
                )

                CalendarViewMode.WEEK -> WeekCalendarContent(
                    selectedDate = selectedDate,
                    events = filteredEvents,
                    categoryMap = categoryMap,
                    priorityMap = priorityMap,
                    holidays = holidays,
                    onSelectDate = { viewModel.selectDate(it) },
                    onOpenEvent = { ev -> onOpenEventEditor(ev.id, selectedDate) },
                    onAddEventForDate = { d -> onOpenEventEditor(null, d) }
                )

                CalendarViewMode.YEAR -> YearCalendarContent(
                    year = visibleMonth.year,
                    events = filteredEvents,
                    categoryMap = categoryMap,
                    priorityMap = priorityMap,
                    onPrevYear = { viewModel.setVisibleMonth(visibleMonth.minusYears(1)) },
                    onNextYear = { viewModel.setVisibleMonth(visibleMonth.plusYears(1)) },
                    onJumpToMonth = { ym ->
                        viewModel.setVisibleMonth(ym)
                        viewModel.setViewMode(CalendarViewMode.MONTH)
                    }
                )
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

    if (showMonthJumpSheet) {
        YearMonthJumpSheet(
            current = visibleMonth,
            onSelect = { ym ->
                viewModel.setVisibleMonth(ym)
                if (viewMode == CalendarViewMode.YEAR) {
                    viewModel.setViewMode(CalendarViewMode.MONTH)
                }
                showMonthJumpSheet = false
            },
            onDismiss = { showMonthJumpSheet = false }
        )
    }
}

@Composable
private fun MonthCalendarContent(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    categoryMap: Map<String, CalendarCategory>,
    priorityMap: Map<String, Int>,
    holidays: HolidayIndex,
    onMonthChanged: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onAddEventForSelected: () -> Unit
) {
    val targetPage = ChronoUnit.MONTHS.between(BASE_YEAR_MONTH, visibleMonth)
        .toInt()
        .coerceIn(0, TOTAL_MONTH_PAGES - 1)
    val pagerState = rememberPagerState(initialPage = targetPage, pageCount = { TOTAL_MONTH_PAGES })

    LaunchedEffect(targetPage) {
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        val settledYm = BASE_YEAR_MONTH.plusMonths(pagerState.settledPage.toLong())
        if (settledYm != visibleMonth) {
            onMonthChanged(settledYm)
        }
    }

    val selectedDateStr = selectedDate.toString()
    val selectedDayEvents = remember(selectedDateStr, events, priorityMap) {
        events
            .filter { it.startDate <= selectedDateStr && it.effectiveEndDate >= selectedDateStr }
            .sortedWith { a, b -> WeekLayoutEngine.compareEvents(a, b, priorityMap) }
    }
    val selectedLunar = remember(selectedDate) { LunarCalendar.getLunarInfo(selectedDate) }
    val holidayName = holidays.getHolidayName(selectedDateStr)
    val workdayName = holidays.getWorkdayName(selectedDateStr)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        item(key = "weekday_header") {
            WeekdayHeaderRow()
        }

        item(key = "month_pager") {
            Surface(
                color = AppColors.Surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val ym = BASE_YEAR_MONTH.plusMonths(page.toLong())
                    MonthGrid(
                        yearMonth = ym,
                        selectedDate = selectedDate,
                        events = events,
                        categoryMap = categoryMap,
                        priorityMap = priorityMap,
                        holidays = holidays,
                        onSelectDate = onSelectDate
                    )
                }
            }
            HorizontalDivider(color = AppColors.BorderSubtle)
        }

        item(key = "selected_day_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 ${DateUtils.weekdayLabel(selectedDate)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextMain
                        )
                        if (holidayName != null) {
                            Spacer(Modifier.width(8.dp))
                            SmallDayBadge(text = "休 · $holidayName", isHoliday = true)
                        } else if (workdayName != null) {
                            Spacer(Modifier.width(8.dp))
                            SmallDayBadge(text = "班 · 调休", isHoliday = false)
                        }
                    }
                    val sub = buildString {
                        if (selectedLunar.fullDisplay.isNotEmpty()) {
                            append("农历${selectedLunar.fullDisplay}")
                        }
                        if (selectedLunar.solarTerm.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(selectedLunar.solarTerm)
                        }
                        if (selectedLunar.festival.isNotEmpty() && selectedLunar.festival != holidayName) {
                            if (isNotEmpty()) append(" · ")
                            append(selectedLunar.festival)
                        }
                    }
                    if (sub.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(text = sub, fontSize = 12.sp, color = AppColors.TextMuted)
                    }
                }
                Text(
                    text = "${selectedDayEvents.size} 条日程",
                    fontSize = 12.sp,
                    color = AppColors.TextMuted
                )
            }
        }

        if (selectedDayEvents.isEmpty()) {
            item(key = "empty_day") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Surface,
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("当日暂无日程安排", fontSize = 14.sp, color = AppColors.TextMuted)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onAddEventForSelected) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("新建当日日程")
                        }
                    }
                }
            }
        } else {
            items(selectedDayEvents, key = { it.id }) { ev ->
                val cat = categoryMap[ev.categoryId] ?: CalendarCategory.Fallback
                EventListCard(
                    event = ev,
                    category = cat,
                    showDateRange = ev.isMultiDay,
                    onClick = { onOpenEvent(ev) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
            .padding(vertical = 6.dp)
    ) {
        DateUtils.WeekdayHeaders.forEachIndexed { idx, label ->
            val isWeekend = idx >= 5
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isWeekend) AppColors.Danger else AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
    HorizontalDivider(color = AppColors.BorderSubtle)
}

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    categoryMap: Map<String, CalendarCategory>,
    priorityMap: Map<String, Int>,
    holidays: HolidayIndex,
    onSelectDate: (LocalDate) -> Unit
) {
    val weeks = remember(yearMonth) { DateUtils.monthGridWeeks(yearMonth) }
    val today = remember { LocalDate.now() }

    Column(modifier = Modifier.fillMaxWidth()) {
        weeks.forEach { weekDates ->
            val layout = remember(weekDates, events, priorityMap, yearMonth) {
                WeekLayoutEngine.layoutWeek(
                    weekDates = weekDates,
                    allEvents = events,
                    priorityMap = priorityMap,
                    activeYear = yearMonth.year,
                    activeMonth = yearMonth.monthValue
                )
            }
            MonthWeekRow(
                weekDates = weekDates,
                activeMonth = yearMonth,
                selectedDate = selectedDate,
                today = today,
                layout = layout,
                categoryMap = categoryMap,
                holidays = holidays,
                onSelectDate = onSelectDate
            )
            HorizontalDivider(color = AppColors.BorderSubtle, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun MonthWeekRow(
    weekDates: List<LocalDate>,
    activeMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    layout: WeekLayoutEngine.WeekLayout,
    categoryMap: Map<String, CalendarCategory>,
    holidays: HolidayIndex,
    onSelectDate: (LocalDate) -> Unit
) {
    val visibleLanes = layout.laneCount.coerceAtMost(3)
    val laneHeightDp = 16.dp
    val laneGapDp = 2.dp
    val headerHeightDp = 38.dp
    val spanAreaHeightDp = if (visibleLanes > 0) (laneHeightDp + laneGapDp) * visibleLanes + 2.dp else 0.dp
    val rowMinHeightDp = (headerHeightDp + spanAreaHeightDp + 22.dp).coerceAtLeast(66.dp)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowMinHeightDp)
    ) {
        val colWidth = maxWidth / 7

        // 底层：7 天单元格
        Row(modifier = Modifier.fillMaxSize()) {
            weekDates.forEachIndexed { colIdx, date ->
                val dateStr = date.toString()
                val inMonth = date.year == activeMonth.year && date.monthValue == activeMonth.monthValue
                val isToday = date == today
                val isSelected = date == selectedDate
                val isRest = holidays.isRestDay(date, dateStr)
                val isHoliday = holidays.isHoliday(dateStr)
                val isMakeup = holidays.isMakeupWorkday(dateStr)
                val lunar = remember(date) { LunarCalendar.getLunarInfo(date) }

                val cellBg = when {
                    isSelected -> AppColors.PrimarySoft
                    isRest -> AppColors.RestDayBg
                    else -> AppColors.Surface
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(cellBg)
                        .then(
                            if (isSelected) Modifier.border(1.dp, AppColors.Primary)
                            else Modifier
                        )
                        .clickable { onSelectDate(date) }
                        .padding(horizontal = 2.dp, vertical = 3.dp)
                ) {
                    // 休 / 班 角标
                    if (isHoliday || isMakeup) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isHoliday) AppColors.HolidayBadgeBg else AppColors.WorkdayBadgeBg)
                                .padding(horizontal = 3.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = if (isHoliday) "休" else "班",
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHoliday) AppColors.HolidayBadgeText else AppColors.WorkdayBadgeText
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (inMonth) 1f else 0.42f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 日期数字
                        Box(
                            modifier = Modifier
                                .size(21.dp)
                                .clip(CircleShape)
                                .background(if (isToday) AppColors.Primary else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${date.dayOfMonth}",
                                fontSize = 12.sp,
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isToday -> Color.White
                                    isRest -> AppColors.Danger
                                    else -> AppColors.TextMain
                                }
                            )
                        }

                        // 农历 / 节气 / 节日
                        Text(
                            text = lunar.label,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            color = if (lunar.isHighlight) AppColors.Primary else AppColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )

                        // 为跨天泳道预留空间
                        val reservedLanes = layout.dayReservedLanes[colIdx].coerceAtMost(3)
                        if (reservedLanes > 0) {
                            Spacer(Modifier.height((laneHeightDp + laneGapDp) * reservedLanes + 2.dp))
                        } else {
                            Spacer(Modifier.height(2.dp))
                        }

                        // 单日日程展示（空间够则显示微条，否则显示圆点 + 数量）
                        val singleEvents = layout.daySingleEvents[colIdx]
                        if (singleEvents.isNotEmpty()) {
                            val firstEv = singleEvents.first()
                            val cat = categoryMap[firstEv.categoryId] ?: CalendarCategory.Fallback
                            val style = ColorUtils.getEventColorStyle(cat.color)
                            if (reservedLanes <= 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(style.softBg)
                                        .padding(horizontal = 2.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = firstEv.title,
                                        fontSize = 9.sp,
                                        lineHeight = 11.sp,
                                        color = style.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    singleEvents.take(3).forEach { ev ->
                                        val c = categoryMap[ev.categoryId] ?: CalendarCategory.Fallback
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(ColorUtils.parseColor(c.color))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 顶层：跨天日程围合泳道条
        layout.segments
            .filter { it.lane < 3 }
            .forEach { seg ->
                val cat = categoryMap[seg.event.categoryId] ?: CalendarCategory.Fallback
                val style = ColorUtils.getEventColorStyle(cat.color)
                val xOffset = colWidth * seg.startCol + (if (seg.cutLeft) 0.dp else 2.dp)
                val barWidth = colWidth * seg.colSpan -
                    (if (seg.cutLeft) 0.dp else 2.dp) -
                    (if (seg.cutRight) 0.dp else 2.dp)
                val yOffset = headerHeightDp + (laneHeightDp + laneGapDp) * seg.lane
                val shape = RoundedCornerShape(
                    topStart = if (seg.cutLeft) 0.dp else 4.dp,
                    bottomStart = if (seg.cutLeft) 0.dp else 4.dp,
                    topEnd = if (seg.cutRight) 0.dp else 4.dp,
                    bottomEnd = if (seg.cutRight) 0.dp else 4.dp
                )

                Box(
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .width(barWidth.coerceAtLeast(8.dp))
                        .height(laneHeightDp)
                        .alpha(if (seg.isOutOfMonth) 0.45f else 1f)
                        .clip(shape)
                        .background(style.softBg)
                        .border(0.75.dp, style.border, shape)
                        .clickable { onSelectDate(weekDates[seg.startCol]) }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = seg.event.title,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = style.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
    }
}

@Composable
private fun WeekCalendarContent(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    categoryMap: Map<String, CalendarCategory>,
    priorityMap: Map<String, Int>,
    holidays: HolidayIndex,
    onSelectDate: (LocalDate) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onAddEventForDate: (LocalDate) -> Unit
) {
    val selectedMonday = DateUtils.mondayOfWeek(selectedDate)
    val targetPage = ChronoUnit.WEEKS.between(BASE_WEEK_MONDAY, selectedMonday)
        .toInt()
        .coerceIn(0, TOTAL_WEEK_PAGES - 1)
    val pagerState = rememberPagerState(initialPage = targetPage, pageCount = { TOTAL_WEEK_PAGES })

    LaunchedEffect(targetPage) {
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        val settledMon = BASE_WEEK_MONDAY.plusWeeks(pagerState.settledPage.toLong())
        if (settledMon != selectedMonday) {
            val today = LocalDate.now()
            val nextSelected = if (!today.isBefore(settledMon) && !today.isAfter(settledMon.plusDays(6))) {
                today
            } else {
                settledMon
            }
            onSelectDate(nextSelected)
        }
    }

    val today = remember { LocalDate.now() }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val monday = BASE_WEEK_MONDAY.plusWeeks(page.toLong())
        val days = DateUtils.weekDates(monday)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(days, key = { it.toString() }) { date ->
                val dateStr = date.toString()
                val dayEvents = events
                    .filter { it.startDate <= dateStr && it.effectiveEndDate >= dateStr }
                    .sortedWith { a, b -> WeekLayoutEngine.compareEvents(a, b, priorityMap) }
                val lunar = remember(date) { LunarCalendar.getLunarInfo(date) }
                val isToday = date == today
                val isRest = holidays.isRestDay(date, dateStr)
                val holidayName = holidays.getHolidayName(dateStr)
                val workdayName = holidays.getWorkdayName(dateStr)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isToday) AppColors.PrimarySoft else AppColors.Surface,
                    border = BorderStroke(1.dp, if (isToday) AppColors.Primary else AppColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${date.monthValue}月${date.dayOfMonth}日 ${DateUtils.weekdayLabel(date)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isToday -> AppColors.Primary
                                        isRest -> AppColors.Danger
                                        else -> AppColors.TextMain
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = lunar.label,
                                    fontSize = 12.sp,
                                    color = AppColors.TextMuted
                                )
                                if (holidayName != null) {
                                    Spacer(Modifier.width(6.dp))
                                    SmallDayBadge("休", isHoliday = true)
                                } else if (workdayName != null) {
                                    Spacer(Modifier.width(6.dp))
                                    SmallDayBadge("班", isHoliday = false)
                                }
                            }
                            TextButton(
                                onClick = { onAddEventForDate(date) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("+ 添加", fontSize = 12.sp)
                            }
                        }

                        if (dayEvents.isEmpty()) {
                            Text(
                                text = "无日程",
                                fontSize = 12.sp,
                                color = AppColors.TextMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                dayEvents.forEach { ev ->
                                    val cat = categoryMap[ev.categoryId] ?: CalendarCategory.Fallback
                                    EventListCard(
                                        event = ev,
                                        category = cat,
                                        showDateRange = ev.isMultiDay,
                                        onClick = { onOpenEvent(ev) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun YearCalendarContent(
    year: Int,
    events: List<CalendarEvent>,
    categoryMap: Map<String, CalendarCategory>,
    priorityMap: Map<String, Int>,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onJumpToMonth: (YearMonth) -> Unit
) {
    val today = remember { LocalDate.now() }

    // 预计算该年每日最高优先级的类别颜色
    val dayTopColorMap = remember(year, events, priorityMap, categoryMap) {
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)
        val map = mutableMapOf<LocalDate, Color>()
        val sortedEvents = events.sortedWith { a, b -> WeekLayoutEngine.compareEvents(a, b, priorityMap) }
        for (ev in sortedEvents) {
            val s = DateUtils.parseOrNull(ev.startDate) ?: continue
            val e = DateUtils.parseOrNull(ev.effectiveEndDate) ?: s
            if (e.isBefore(yearStart) || s.isAfter(yearEnd)) continue
            val clampedStart = if (s.isBefore(yearStart)) yearStart else s
            val clampedEnd = if (e.isAfter(yearEnd)) yearEnd else e
            val cat = categoryMap[ev.categoryId] ?: CalendarCategory.Fallback
            val color = ColorUtils.parseColor(cat.color)
            var cur = clampedStart
            while (!cur.isAfter(clampedEnd)) {
                map.putIfAbsent(cur, color)
                cur = cur.plusDays(1)
            }
        }
        map
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrevYear) { Text("‹ ${year - 1}年") }
            Text("${year}年全年概览", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            TextButton(onClick = onNextYear) { Text("${year + 1}年 ›") }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(12) { idx ->
                val ym = YearMonth.of(year, idx + 1)
                MiniMonthCard(
                    yearMonth = ym,
                    today = today,
                    dayTopColorMap = dayTopColorMap,
                    onClick = { onJumpToMonth(ym) }
                )
            }
        }
    }
}

@Composable
private fun MiniMonthCard(
    yearMonth: YearMonth,
    today: LocalDate,
    dayTopColorMap: Map<LocalDate, Color>,
    onClick: () -> Unit
) {
    val firstDay = yearMonth.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value
    val totalDays = yearMonth.lengthOfMonth()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(
            1.dp,
            if (yearMonth == YearMonth.from(today)) AppColors.Primary else AppColors.BorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "${yearMonth.monthValue}月",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (yearMonth == YearMonth.from(today)) AppColors.Primary else AppColors.TextMain
            )
            Spacer(Modifier.height(6.dp))
            val totalSlots = leadingBlanks + totalDays
            val rows = (totalSlots + 6) / 7
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val slot = r * 7 + c
                        val dayNum = slot - leadingBlanks + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNum in 1..totalDays) {
                                val d = yearMonth.atDay(dayNum)
                                val dotColor = dayTopColorMap[d]
                                val isToday = d == today
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isToday -> AppColors.Primary
                                                dotColor != null -> dotColor.copy(alpha = 0.22f)
                                                else -> Color.Transparent
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        fontSize = 10.sp,
                                        fontWeight = if (isToday || dotColor != null) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isToday -> Color.White
                                            dotColor != null -> dotColor
                                            else -> AppColors.TextSecondary
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallDayBadge(text: String, isHoliday: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isHoliday) AppColors.HolidayBadgeBg else AppColors.WorkdayBadgeBg
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isHoliday) AppColors.HolidayBadgeText else AppColors.WorkdayBadgeText,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearMonthJumpSheet(
    current: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    var pickedYear by remember { mutableIntStateOf(current.year) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { pickedYear = (pickedYear - 1).coerceAtLeast(2000) }) {
                    Text("‹ ${pickedYear - 1}")
                }
                Text(
                    text = "${pickedYear}年",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextMain
                )
                TextButton(onClick = { pickedYear = (pickedYear + 1).coerceAtMost(2100) }) {
                    Text("${pickedYear + 1} ›")
                }
            }
            Spacer(Modifier.height(10.dp))
            for (row in 0 until 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 1..4) {
                        val m = row * 4 + col
                        val ym = YearMonth.of(pickedYear, m)
                        val selected = ym == current
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) AppColors.Primary else AppColors.SurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(ym) }
                        ) {
                            Text(
                                text = "${m}月",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) Color.White else AppColors.TextMain,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
