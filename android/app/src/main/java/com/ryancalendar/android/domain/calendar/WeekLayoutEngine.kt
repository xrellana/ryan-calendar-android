package com.ryancalendar.android.domain.calendar

import com.ryancalendar.android.domain.model.CalendarEvent
import java.time.LocalDate

/**
 * 1:1 移植 Web 端 js/layout.js 的月视图跨天行程切段与泳道排版引擎
 */
object WeekLayoutEngine {

    data class SpanSegment(
        val event: CalendarEvent,
        val startCol: Int,
        val endCol: Int,
        val colSpan: Int,
        val cutLeft: Boolean,
        val cutRight: Boolean,
        val isOutOfMonth: Boolean,
        val lane: Int
    )

    data class WeekLayout(
        val segments: List<SpanSegment>,
        val laneCount: Int,
        val dayReservedLanes: IntArray,
        val daySingleEvents: List<List<CalendarEvent>>,
        val dayTotalCount: IntArray
    )

    fun compareEvents(a: CalendarEvent, b: CalendarEvent, priorityMap: Map<String, Int>): Int {
        val pa = priorityMap[a.categoryId] ?: 9999
        val pb = priorityMap[b.categoryId] ?: 9999
        if (pa != pb) return pa - pb

        val aAll = if (a.allDay) 0 else 1
        val bAll = if (b.allDay) 0 else 1
        if (aAll != bAll) return aAll - bAll

        if (a.startTime != b.startTime) {
            return a.startTime.compareTo(b.startTime)
        }
        return a.id.compareTo(b.id)
    }

    fun layoutWeek(
        weekDates: List<LocalDate>,
        allEvents: List<CalendarEvent>,
        priorityMap: Map<String, Int>,
        activeYear: Int,
        activeMonth: Int
    ): WeekLayout {
        val weekStrs = weekDates.map { it.toString() }
        val weekStart = weekStrs.first()
        val weekEnd = weekStrs.last()
        val activePrefix = "%04d-%02d-".format(activeYear, activeMonth)

        val rawSegments = mutableListOf<SpanSegment>()

        for (ev in allEvents) {
            if (!ev.isMultiDay) continue
            val evEnd = ev.effectiveEndDate
            if (evEnd < weekStart || ev.startDate > weekEnd) continue

            val segStartDate = if (ev.startDate > weekStart) ev.startDate else weekStart
            val segEndDate = if (evEnd < weekEnd) evEnd else weekEnd
            val startCol = weekStrs.indexOf(segStartDate)
            val endCol = weekStrs.indexOf(segEndDate)
            if (startCol < 0 || endCol < 0) continue

            var hasAnyInMonth = false
            for (c in startCol..endCol) {
                if (weekStrs[c].startsWith(activePrefix)) {
                    hasAnyInMonth = true
                    break
                }
            }

            rawSegments.add(
                SpanSegment(
                    event = ev,
                    startCol = startCol,
                    endCol = endCol,
                    colSpan = endCol - startCol + 1,
                    cutLeft = ev.startDate < weekStart,
                    cutRight = evEnd > weekEnd,
                    isOutOfMonth = !hasAnyInMonth,
                    lane = 0
                )
            )
        }

        rawSegments.sortWith { a, b ->
            when {
                a.startCol != b.startCol -> a.startCol - b.startCol
                a.colSpan != b.colSpan -> b.colSpan - a.colSpan
                else -> compareEvents(a.event, b.event, priorityMap)
            }
        }

        val laneOccupancy = mutableListOf<BooleanArray>()
        var maxLane = -1
        val placed = mutableListOf<SpanSegment>()

        for (seg in rawSegments) {
            var assigned = -1
            for (l in laneOccupancy.indices) {
                var conflict = false
                for (col in seg.startCol..seg.endCol) {
                    if (laneOccupancy[l][col]) {
                        conflict = true
                        break
                    }
                }
                if (!conflict) {
                    assigned = l
                    break
                }
            }
            if (assigned == -1) {
                assigned = laneOccupancy.size
                laneOccupancy.add(BooleanArray(7))
            }
            for (col in seg.startCol..seg.endCol) {
                laneOccupancy[assigned][col] = true
            }
            placed.add(seg.copy(lane = assigned))
            if (assigned > maxLane) maxLane = assigned
        }

        val dayReservedLanes = IntArray(7)
        for (sg in placed) {
            for (c in sg.startCol..sg.endCol) {
                if (sg.lane + 1 > dayReservedLanes[c]) {
                    dayReservedLanes[c] = sg.lane + 1
                }
            }
        }

        val daySingleEvents = MutableList<List<CalendarEvent>>(7) { emptyList() }
        val dayTotalCount = IntArray(7)

        for (colIdx in 0 until 7) {
            val dateStr = weekStrs[colIdx]
            val singleList = mutableListOf<CalendarEvent>()
            var multiCount = 0
            for (ev in allEvents) {
                if (ev.startDate <= dateStr && ev.effectiveEndDate >= dateStr) {
                    if (ev.isMultiDay) multiCount++ else singleList.add(ev)
                }
            }
            singleList.sortWith { a, b -> compareEvents(a, b, priorityMap) }
            daySingleEvents[colIdx] = singleList
            dayTotalCount[colIdx] = multiCount + singleList.size
        }

        return WeekLayout(
            segments = placed,
            laneCount = maxLane + 1,
            dayReservedLanes = dayReservedLanes,
            daySingleEvents = daySingleEvents,
            dayTotalCount = dayTotalCount
        )
    }
}
