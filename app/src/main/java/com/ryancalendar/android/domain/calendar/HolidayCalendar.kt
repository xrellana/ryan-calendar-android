package com.ryancalendar.android.domain.calendar

import com.ryancalendar.android.domain.model.HolidayEntry
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 1:1 移植 Web 端 js/holidays.js：内置 2024-2027 法定节假日与调休上班日，并支持服务端数据覆盖融合
 */
data class HolidayIndex(
    val holidays: Map<String, String>,
    val workdays: Map<String, String>,
    val coveredYears: Set<Int>
) {
    fun getHolidayName(dateStr: String): String? = holidays[dateStr]
    fun getWorkdayName(dateStr: String): String? = workdays[dateStr]
    fun isHoliday(dateStr: String): Boolean = holidays.containsKey(dateStr)
    fun isMakeupWorkday(dateStr: String): Boolean = workdays.containsKey(dateStr)

    fun isRestDay(date: LocalDate, dateStr: String): Boolean {
        if (isHoliday(dateStr)) return true
        if (isMakeupWorkday(dateStr)) return false
        return date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }

    fun isYearCovered(year: Int): Boolean = coveredYears.contains(year)

    companion object {
        private data class YearPlan(
            val holidays: Map<String, String>,
            val workdays: Map<String, String>
        )

        private fun expandRange(start: String, end: String, name: String, target: MutableMap<String, String>) {
            var cur = LocalDate.parse(start)
            val endDate = LocalDate.parse(end)
            while (!cur.isAfter(endDate)) {
                target[cur.toString()] = name
                cur = cur.plusDays(1)
            }
        }

        private fun buildYear(
            ranges: List<Triple<String, String, String>>,
            workdays: List<Pair<String, String>>
        ): YearPlan {
            val h = mutableMapOf<String, String>()
            ranges.forEach { (s, e, n) -> expandRange(s, e, n, h) }
            val w = workdays.toMap()
            return YearPlan(h, w)
        }

        private val BUILTIN_PLANS: Map<Int, YearPlan> = mapOf(
            2024 to buildYear(
                listOf(
                    Triple("2024-01-01", "2024-01-01", "元旦"),
                    Triple("2024-02-10", "2024-02-17", "春节"),
                    Triple("2024-04-04", "2024-04-06", "清明节"),
                    Triple("2024-05-01", "2024-05-05", "劳动节"),
                    Triple("2024-06-08", "2024-06-10", "端午节"),
                    Triple("2024-09-15", "2024-09-17", "中秋节"),
                    Triple("2024-10-01", "2024-10-07", "国庆节")
                ),
                listOf(
                    "2024-02-04" to "春节调休上班",
                    "2024-02-18" to "春节调休上班",
                    "2024-04-07" to "清明节调休上班",
                    "2024-04-28" to "劳动节调休上班",
                    "2024-05-11" to "劳动节调休上班",
                    "2024-09-14" to "中秋节调休上班",
                    "2024-09-29" to "国庆节调休上班",
                    "2024-10-12" to "国庆节调休上班"
                )
            ),
            2025 to buildYear(
                listOf(
                    Triple("2025-01-01", "2025-01-01", "元旦"),
                    Triple("2025-01-28", "2025-02-04", "春节"),
                    Triple("2025-04-04", "2025-04-06", "清明节"),
                    Triple("2025-05-01", "2025-05-05", "劳动节"),
                    Triple("2025-05-31", "2025-06-02", "端午节"),
                    Triple("2025-10-01", "2025-10-08", "国庆节/中秋节")
                ),
                listOf(
                    "2025-01-26" to "春节调休上班",
                    "2025-02-08" to "春节调休上班",
                    "2025-04-27" to "劳动节调休上班",
                    "2025-09-28" to "国庆节调休上班",
                    "2025-10-11" to "国庆节调休上班"
                )
            ),
            2026 to buildYear(
                listOf(
                    Triple("2026-01-01", "2026-01-03", "元旦"),
                    Triple("2026-02-15", "2026-02-23", "春节"),
                    Triple("2026-04-04", "2026-04-06", "清明节"),
                    Triple("2026-05-01", "2026-05-05", "劳动节"),
                    Triple("2026-06-19", "2026-06-21", "端午节"),
                    Triple("2026-09-25", "2026-09-27", "中秋节"),
                    Triple("2026-10-01", "2026-10-07", "国庆节")
                ),
                listOf(
                    "2026-01-04" to "元旦调休上班",
                    "2026-02-14" to "春节调休上班",
                    "2026-02-28" to "春节调休上班",
                    "2026-05-09" to "劳动节调休上班",
                    "2026-09-20" to "国庆节调休上班",
                    "2026-10-10" to "国庆节调休上班"
                )
            ),
            2027 to buildYear(
                listOf(
                    Triple("2027-01-01", "2027-01-03", "元旦"),
                    Triple("2027-02-05", "2027-02-13", "春节"),
                    Triple("2027-04-03", "2027-04-05", "清明节"),
                    Triple("2027-05-01", "2027-05-05", "劳动节"),
                    Triple("2027-06-09", "2027-06-09", "端午节"),
                    Triple("2027-09-15", "2027-09-15", "中秋节"),
                    Triple("2027-10-01", "2027-10-07", "国庆节")
                ),
                listOf(
                    "2027-01-31" to "春节调休上班",
                    "2027-02-14" to "春节调休上班",
                    "2027-05-08" to "劳动节调休上班",
                    "2027-09-26" to "国庆节调休上班",
                    "2027-10-09" to "国庆节调休上班"
                )
            )
        )

        val Builtin: HolidayIndex by lazy { fromServerEntries(emptyList()) }

        fun fromServerEntries(entries: List<HolidayEntry>): HolidayIndex {
            val merged = BUILTIN_PLANS.toMutableMap()
            if (entries.isNotEmpty()) {
                val grouped = entries.groupBy { it.year }
                grouped.forEach { (year, list) ->
                    val h = mutableMapOf<String, String>()
                    val w = mutableMapOf<String, String>()
                    list.forEach { item ->
                        if (item.type == "holiday") {
                            h[item.date] = item.name.ifBlank { "法定节假日" }
                        } else if (item.type == "workday") {
                            w[item.date] = item.name.ifBlank { "调休上班" }
                        }
                    }
                    merged[year] = YearPlan(h, w)
                }
            }
            val allH = mutableMapOf<String, String>()
            val allW = mutableMapOf<String, String>()
            merged.values.forEach { plan ->
                allH.putAll(plan.holidays)
                allW.putAll(plan.workdays)
            }
            return HolidayIndex(
                holidays = allH,
                workdays = allW,
                coveredYears = merged.keys
            )
        }
    }
}
