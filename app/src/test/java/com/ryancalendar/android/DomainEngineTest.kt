package com.ryancalendar.android

import com.ryancalendar.android.domain.calendar.HolidayIndex
import com.ryancalendar.android.domain.calendar.LunarCalendar
import com.ryancalendar.android.domain.calendar.WeekLayoutEngine
import com.ryancalendar.android.domain.model.CalendarEvent
import com.ryancalendar.android.domain.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainEngineTest {

    @Test
    fun lunarCalendarMatchesSpringFestival2026() {
        val springFestival = LunarCalendar.getLunarInfo(LocalDate.of(2026, 2, 17))
        assertEquals("春节", springFestival.festival)
        assertEquals("春节", springFestival.label)
        assertTrue(springFestival.isHighlight)

        val eve = LunarCalendar.getLunarInfo(LocalDate.of(2026, 2, 16))
        assertEquals("除夕", eve.festival)
        assertEquals("除夕", eve.label)
    }

    @Test
    fun holidayCalendarCoversBuiltinYears() {
        val index = HolidayIndex.Builtin
        assertTrue(index.isHoliday("2026-10-01"))
        assertTrue(index.isMakeupWorkday("2026-10-10"))
        assertFalse(index.isRestDay(LocalDate.of(2026, 10, 10), "2026-10-10"))
        assertTrue(index.isRestDay(LocalDate.of(2026, 10, 1), "2026-10-01"))
    }

    @Test
    fun weekLayoutAssignsNonOverlappingLanes() {
        val week = DateUtils.weekDates(LocalDate.of(2026, 11, 16)) // Mon..Sun
        val events = listOf(
            CalendarEvent(
                id = "e1",
                title = "跨天A",
                categoryId = "cat-assessment",
                startDate = "2026-11-16",
                endDate = "2026-11-19"
            ),
            CalendarEvent(
                id = "e2",
                title = "跨天B",
                categoryId = "cat-consulting",
                startDate = "2026-11-18",
                endDate = "2026-11-21"
            ),
            CalendarEvent(
                id = "e3",
                title = "跨天C",
                categoryId = "cat-rest",
                startDate = "2026-11-20",
                endDate = "2026-11-22"
            )
        )
        val priority = mapOf("cat-assessment" to 0, "cat-consulting" to 1, "cat-rest" to 2)
        val layout = WeekLayoutEngine.layoutWeek(week, events, priority, 2026, 11)

        assertEquals(2, layout.laneCount)
        val segA = layout.segments.first { it.event.id == "e1" }
        val segB = layout.segments.first { it.event.id == "e2" }
        val segC = layout.segments.first { it.event.id == "e3" }
        assertEquals(0, segA.lane)
        assertEquals(1, segB.lane)
        assertEquals(0, segC.lane)
    }
}
