package com.ryancalendar.android.domain.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * 1:1 移植 Web 端 js/lunar.js 的零依赖中国农历、二十四节气与传统节日计算（1900 - 2100）
 */
object LunarCalendar {

    data class LunarInfo(
        val lunarYear: Int = 0,
        val lunarMonth: Int = 0,
        val lunarDay: Int = 0,
        val isLeap: Boolean = false,
        val lunarMonthName: String = "",
        val lunarDayName: String = "",
        val solarTerm: String = "",
        val festival: String = "",
        val label: String = "",
        val isHighlight: Boolean = false
    ) {
        val fullDisplay: String
            get() = if (lunarMonthName.isNotEmpty() && lunarDayName.isNotEmpty()) {
                "$lunarMonthName$lunarDayName"
            } else {
                ""
            }
    }

    private data class LunarRaw(
        val lunarYear: Int,
        val lunarMonth: Int,
        val lunarDay: Int,
        val isLeap: Boolean
    )

    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x16a95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
        0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
        0x0d520
    )

    private val SOLAR_TERM_NAMES = arrayOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
        "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
        "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    )

    private val S_TERM_INFO = longArrayOf(
        0, 21208, 42467, 63836, 85337, 107014,
        128867, 150921, 173149, 195551, 218072, 240693,
        263343, 285989, 308563, 331033, 353350, 375494,
        397447, 419210, 440795, 462224, 483532, 504758
    )

    private val MONTH_CN = arrayOf("", "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
    private val DAY_PREFIX = arrayOf("初", "十", "廿", "三")
    private val DAY_NUM = arrayOf("日", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    private val LUNAR_FESTIVALS = mapOf(
        "1-1" to "春节",
        "1-15" to "元宵",
        "5-5" to "端午",
        "7-7" to "七夕",
        "8-15" to "中秋",
        "9-9" to "重阳",
        "12-8" to "腊八",
        "12-23" to "小年"
    )

    private val SOLAR_FESTIVALS = mapOf(
        "1-1" to "元旦",
        "5-1" to "劳动节",
        "10-1" to "国庆节"
    )

    private val BASE_DATE_1900_01_31 = LocalDate.of(1900, 1, 31)
    private val BASE_TERM_EPOCH_MS = LocalDate.of(1900, 1, 6)
        .atTime(2, 5)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    private val solarTermCache = ConcurrentHashMap<Int, Map<String, String>>()
    private val infoCache = ConcurrentHashMap<LocalDate, LunarInfo>()

    private fun leapMonth(y: Int): Int = LUNAR_INFO[y - 1900] and 0xf

    private fun leapDays(y: Int): Int =
        if (leapMonth(y) != 0) {
            if ((LUNAR_INFO[y - 1900] and 0x10000) != 0) 30 else 29
        } else {
            0
        }

    private fun monthDays(y: Int, m: Int): Int =
        if ((LUNAR_INFO[y - 1900] and (0x10000 shr m)) != 0) 30 else 29

    private fun lYearDays(y: Int): Int {
        var sum = 348
        val info = LUNAR_INFO[y - 1900]
        var i = 0x8000
        while (i > 0x8) {
            if ((info and i) != 0) sum += 1
            i = i shr 1
        }
        return sum + leapDays(y)
    }

    private fun getSolarTermMap(year: Int): Map<String, String> {
        return solarTermCache.getOrPut(year) {
            val map = HashMap<String, String>(24)
            for (i in 0 until 24) {
                val ms = (31556925974.7 * (year - 1900)).toLong() + S_TERM_INFO[i] * 60000L
                val dt = Instant.ofEpochMilli(BASE_TERM_EPOCH_MS + ms + 8L * 3600_000L)
                    .atZone(ZoneOffset.UTC)
                map["${dt.monthValue}-${dt.dayOfMonth}"] = SOLAR_TERM_NAMES[i]
            }
            map
        }
    }

    private fun formatLunarDay(d: Int): String = when (d) {
        10 -> "初十"
        20 -> "二十"
        30 -> "三十"
        else -> DAY_PREFIX[d / 10] + DAY_NUM[d % 10]
    }

    private fun solarToLunarRaw(date: LocalDate): LunarRaw? {
        if (date.year < 1900 || date.year > 2100) return null
        var offset = (date.toEpochDay() - BASE_DATE_1900_01_31.toEpochDay()).toInt()
        if (offset < 0) return null

        var year = 1900
        var temp = 0
        while (year < 2101 && offset > 0) {
            temp = lYearDays(year)
            offset -= temp
            year++
        }
        if (offset < 0) {
            offset += temp
            year--
        }

        val leap = leapMonth(year)
        var isLeap = false
        var month = 1
        while (month < 13 && offset > 0) {
            if (leap > 0 && month == leap + 1 && !isLeap) {
                month--
                isLeap = true
                temp = leapDays(year)
            } else {
                temp = monthDays(year, month)
            }
            if (isLeap && month == leap + 1) {
                isLeap = false
            }
            offset -= temp
            month++
        }

        if (offset == 0 && leap > 0 && month == leap + 1) {
            if (isLeap) {
                isLeap = false
            } else {
                isLeap = true
                month--
            }
        }
        if (offset < 0) {
            offset += temp
            month--
        }

        return LunarRaw(
            lunarYear = year,
            lunarMonth = month,
            lunarDay = offset + 1,
            isLeap = isLeap
        )
    }

    fun getLunarInfo(date: LocalDate): LunarInfo {
        return infoCache.getOrPut(date) {
            val raw = solarToLunarRaw(date) ?: return@getOrPut LunarInfo()

            val monthName = (if (raw.isLeap) "闰" else "") + MONTH_CN[raw.lunarMonth] + "月"
            val dayName = formatLunarDay(raw.lunarDay)
            val solarTerm = getSolarTermMap(date.year)["${date.monthValue}-${date.dayOfMonth}"].orEmpty()

            val nextRaw = solarToLunarRaw(date.plusDays(1))
            val festival = when {
                nextRaw != null && !nextRaw.isLeap && nextRaw.lunarMonth == 1 && nextRaw.lunarDay == 1 -> "除夕"
                !raw.isLeap -> LUNAR_FESTIVALS["${raw.lunarMonth}-${raw.lunarDay}"].orEmpty()
                else -> ""
            }

            val solarFest = SOLAR_FESTIVALS["${date.monthValue}-${date.dayOfMonth}"].orEmpty()
            val primaryFest = festival.ifEmpty { solarFest }

            val label: String
            val isHighlight: Boolean
            when {
                primaryFest.isNotEmpty() -> {
                    label = primaryFest
                    isHighlight = true
                }
                solarTerm.isNotEmpty() -> {
                    label = solarTerm
                    isHighlight = true
                }
                raw.lunarDay == 1 -> {
                    label = monthName
                    isHighlight = false
                }
                else -> {
                    label = dayName
                    isHighlight = false
                }
            }

            LunarInfo(
                lunarYear = raw.lunarYear,
                lunarMonth = raw.lunarMonth,
                lunarDay = raw.lunarDay,
                isLeap = raw.isLeap,
                lunarMonthName = monthName,
                lunarDayName = dayName,
                solarTerm = solarTerm,
                festival = primaryFest,
                label = label,
                isHighlight = isHighlight
            )
        }
    }
}
