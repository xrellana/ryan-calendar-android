package com.ryancalendar.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.util.ColorUtils
import com.ryancalendar.android.domain.util.DateUtils
import com.ryancalendar.android.ui.AppViewModel
import com.ryancalendar.android.ui.theme.AppColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private data class CategoryStatRow(
    val category: CalendarCategory,
    val eventCount: Int,
    val coveredDays: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: AppViewModel) {
    val categories by viewModel.categories.collectAsState()
    val events by viewModel.allEvents.collectAsState()

    val now = remember { LocalDate.now() }
    var statsMode by rememberSaveable { mutableStateOf("month") } // "month" | "year"
    var statsYear by rememberSaveable { mutableIntStateOf(now.year) }
    var statsMonth by rememberSaveable { mutableIntStateOf(now.monthValue) }

    val rangeStart = remember(statsMode, statsYear, statsMonth) {
        if (statsMode == "year") LocalDate.of(statsYear, 1, 1)
        else LocalDate.of(statsYear, statsMonth, 1)
    }
    val rangeEnd = remember(statsMode, statsYear, statsMonth) {
        if (statsMode == "year") LocalDate.of(statsYear, 12, 31)
        else YearMonth.of(statsYear, statsMonth).atEndOfMonth()
    }

    val statsSummary = remember(events, categories, rangeStart, rangeEnd) {
        val catMap = categories.associateBy { it.id }
        val rangeStartStr = rangeStart.toString()
        val rangeEndStr = rangeEnd.toString()

        val matchedEvents = events.filter {
            it.effectiveEndDate >= rangeStartStr && it.startDate <= rangeEndStr
        }

        val countByCat = mutableMapOf<String, Int>()
        val daysByCat = mutableMapOf<String, MutableSet<LocalDate>>()
        val allBusyDays = mutableSetOf<LocalDate>()

        for (ev in matchedEvents) {
            val catId = if (catMap.containsKey(ev.categoryId)) ev.categoryId else CalendarCategory.Fallback.id
            countByCat[catId] = (countByCat[catId] ?: 0) + 1

            val s = DateUtils.parseOrNull(ev.startDate) ?: continue
            val e = DateUtils.parseOrNull(ev.effectiveEndDate) ?: s
            val clampedS = if (s.isBefore(rangeStart)) rangeStart else s
            val clampedE = if (e.isAfter(rangeEnd)) rangeEnd else e
            val set = daysByCat.getOrPut(catId) { mutableSetOf() }
            var cur = clampedS
            while (!cur.isAfter(clampedE)) {
                set.add(cur)
                allBusyDays.add(cur)
                cur = cur.plusDays(1)
            }
        }

        val rows = categories.map { cat ->
            CategoryStatRow(
                category = cat,
                eventCount = countByCat[cat.id] ?: 0,
                coveredDays = daysByCat[cat.id]?.size ?: 0
            )
        }.toMutableList()

        val fallbackCount = countByCat[CalendarCategory.Fallback.id] ?: 0
        if (fallbackCount > 0) {
            rows.add(
                CategoryStatRow(
                    category = CalendarCategory.Fallback,
                    eventCount = fallbackCount,
                    coveredDays = daysByCat[CalendarCategory.Fallback.id]?.size ?: 0
                )
            )
        }

        val multiDayCount = matchedEvents.count { it.isMultiDay }
        val totalPeriodDays = ChronoUnit.DAYS.between(rangeStart, rangeEnd).toInt() + 1

        Triple(
            matchedEvents.size,
            Pair(multiDayCount, Pair(allBusyDays.size, totalPeriodDays)),
            rows
        )
    }

    val totalEvents = statsSummary.first
    val multiDayCount = statsSummary.second.first
    val busyDays = statsSummary.second.second.first
    val totalPeriodDays = statsSummary.second.second.second
    val rows = statsSummary.third

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CanvasBg)
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
            title = { Text("日程统计", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
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
                        selected = statsMode == "month",
                        onClick = { statsMode = "month" },
                        label = { Text("按月") }
                    )
                    FilterChip(
                        selected = statsMode == "year",
                        onClick = { statsMode = "year" },
                        label = { Text("按年") }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            if (statsMode == "year") {
                                statsYear--
                            } else {
                                val prev = YearMonth.of(statsYear, statsMonth).minusMonths(1)
                                statsYear = prev.year
                                statsMonth = prev.monthValue
                            }
                        }
                    ) { Text("‹") }
                    Text(
                        text = if (statsMode == "year") "${statsYear}年" else "${statsYear}年${statsMonth}月",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    TextButton(
                        onClick = {
                            if (statsMode == "year") {
                                statsYear++
                            } else {
                                val next = YearMonth.of(statsYear, statsMonth).plusMonths(1)
                                statsYear = next.year
                                statsMonth = next.monthValue
                            }
                        }
                    ) { Text("›") }
                }
            }
        }
        HorizontalDivider(color = AppColors.BorderSubtle)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "overview_cards") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatSummaryCard(
                        label = "日程总数",
                        value = "$totalEvents",
                        unit = "项",
                        modifier = Modifier.weight(1f)
                    )
                    StatSummaryCard(
                        label = "跨天项目",
                        value = "$multiDayCount",
                        unit = "项",
                        modifier = Modifier.weight(1f)
                    )
                    StatSummaryCard(
                        label = "有日程天数",
                        value = "$busyDays",
                        unit = "/ ${totalPeriodDays}天",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "breakdown_title") {
                Text(
                    text = "各类别分布",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextMain,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(rows, key = { it.category.id }) { row ->
                val style = ColorUtils.getEventColorStyle(row.category.color)
                val ratio = if (totalEvents > 0) row.eventCount.toFloat() / totalEvents.toFloat() else 0f
                val pct = (ratio * 100).toInt()

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Surface,
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(style.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = row.category.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.TextMain
                                )
                            }
                            Text(
                                text = "${row.eventCount} 项 · ${row.coveredDays} 天（$pct%）",
                                fontSize = 12.sp,
                                color = AppColors.TextSecondary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            color = style.primary,
                            trackColor = AppColors.SurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatSummaryCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, fontSize = 12.sp, color = AppColors.TextMuted)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextMain
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    color = AppColors.TextMuted,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}
