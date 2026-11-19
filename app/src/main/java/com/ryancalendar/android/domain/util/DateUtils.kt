package com.ryancalendar.android.domain.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object DateUtils {

    val WeekdayHeaders = listOf("一", "二", "三", "四", "五", "六", "日")

    fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }

    fun mondayOfWeek(date: LocalDate): LocalDate {
        val diff = date.dayOfWeek.value - DayOfWeek.MONDAY.value
        return date.minusDays(diff.toLong())
    }

    fun weekDates(monday: LocalDate): List<LocalDate> =
        (0L..6L).map { monday.plusDays(it) }

    /**
     * 生成指定月份的完整周矩阵（周一为每周首日，通常 5 或 6 行）
     */
    fun monthGridWeeks(yearMonth: YearMonth): List<List<LocalDate>> {
        val firstOfMonth = yearMonth.atDay(1)
        val lastOfMonth = yearMonth.atEndOfMonth()
        val gridStart = mondayOfWeek(firstOfMonth)
        val gridEnd = mondayOfWeek(lastOfMonth).plusDays(6)

        val weeks = mutableListOf<List<LocalDate>>()
        var cursor = gridStart
        while (!cursor.isAfter(gridEnd)) {
            weeks.add(weekDates(cursor))
            cursor = cursor.plusDays(7)
        }
        return weeks
    }

    fun parseOrNull(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            LocalDate.parse(dateStr.trim())
        } catch (_: Exception) {
            null
        }
    }

    fun formatShortMD(dateStr: String): String {
        val d = parseOrNull(dateStr) ?: return dateStr
        return "${d.monthValue}月${d.dayOfMonth}日"
    }

    fun formatRangeSummary(startDate: String, endDate: String, today: LocalDate = LocalDate.now()): String {
        val s = parseOrNull(startDate) ?: return startDate
        val e = parseOrNull(endDate)?.takeIf { !it.isBefore(s) } ?: s
        val todayStr = today.toString()
        return if (e.isAfter(s)) {
            val days = ChronoUnit.DAYS.between(s, e) + 1
            "${s.monthValue}月${s.dayOfMonth}日 - ${e.monthValue}月${e.dayOfMonth}日（共${days}天）"
        } else if (startDate == todayStr) {
            "今天（${weekdayLabel(s)}）"
        } else {
            "${s.monthValue}月${s.dayOfMonth}日（${weekdayLabel(s)}）"
        }
    }

    fun relativeTag(startDate: String, endDate: String, today: LocalDate = LocalDate.now()): String {
        val s = parseOrNull(startDate) ?: return ""
        val e = parseOrNull(endDate)?.takeIf { !it.isBefore(s) } ?: s
        return when {
            today.isBefore(s) -> {
                val diff = ChronoUnit.DAYS.between(today, s)
                when (diff) {
                    1L -> "明天"
                    2L -> "后天"
                    else -> "${diff}天后"
                }
            }
            !today.isAfter(e) -> {
                if (e.isAfter(s)) "进行中" else "今天"
            }
            else -> "已结束"
        }
    }
}
