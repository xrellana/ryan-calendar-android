package com.ryancalendar.android

import com.ryancalendar.android.domain.eval.DaysWindow
import com.ryancalendar.android.domain.eval.DaysWindowMode
import com.ryancalendar.android.domain.eval.EvalCore
import com.ryancalendar.android.domain.eval.EvalProject
import com.ryancalendar.android.domain.eval.EvalSettings
import com.ryancalendar.android.domain.eval.EvalStats
import com.ryancalendar.android.domain.eval.EvalTravel
import com.ryancalendar.android.domain.eval.FeeFilter
import com.ryancalendar.android.domain.eval.FeeGroupBy
import com.ryancalendar.android.domain.eval.ProjectFilter
import com.ryancalendar.android.domain.eval.ProjectSort
import com.ryancalendar.android.domain.eval.ProjectSortField
import com.ryancalendar.android.domain.eval.ProjectTimeType
import com.ryancalendar.android.domain.eval.RangePreset
import com.ryancalendar.android.domain.eval.TravelFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvalCoreTest {
    private val settings = EvalSettings.Default

    private fun project(
        id: String,
        name: String,
        stage: String = "正式评估",
        level: String = "三级",
        role: String = "组长",
        start: String = "2026-12-13",
        end: String = "2026-12-15",
        status: String = "待开始",
        settle: String = "未结",
        feeManual: Double? = null
    ) = EvalProject(id, name, level, role, stage, start, end, status, settle, "", feeManual)

    private fun travel(id: String, name: String, amount: Double, reimb: String = "未报", date: String = "2026-12-13", stage: String = "正式评估") =
        EvalTravel(id, date, name, stage, "", "", "", "", "打车", "", amount, "detail", reimb)

    @Test
    fun round2_matchesJsMathRound() {
        assertEquals(1.01, EvalCore.round2(1.005), 0.0)
        assertEquals(0.3, EvalCore.round2(0.1 + 0.2), 0.0)
        assertEquals(1234.57, EvalCore.round2(1234.567), 0.0)
    }

    @Test
    fun autoEndDate_addsRuleDaysMinusOne() {
        assertEquals("2026-12-15", EvalCore.autoEndDate("2026-12-13", "三级", "正式评估", settings))
        assertEquals("2026-12-13", EvalCore.autoEndDate("2026-12-13", "二级", "预评估", settings))
        // Level 1 has no pre-assessment rule.
        assertEquals("", EvalCore.autoEndDate("2026-12-13", "一级", "预评估", settings))
        assertEquals("2027-01-01", EvalCore.autoEndDate("2026-12-31", "二级", "正式评估", settings))
    }

    @Test
    fun calcDays_isInclusiveAndZeroWhenInvalid() {
        assertEquals(3, EvalCore.calcDays("2026-12-13", "2026-12-15"))
        assertEquals(1, EvalCore.calcDays("2026-12-13", "2026-12-13"))
        assertEquals(0, EvalCore.calcDays("2026-12-15", "2026-12-13"))
        assertEquals(0, EvalCore.calcDays("", "2026-12-13"))
        assertEquals(0, EvalCore.calcDays("2026-02-30", "2026-03-01"))
    }

    @Test
    fun effectiveFee_coversRuleManualAndPending() {
        val auto = EvalCore.effectiveFee(project("a", "A"), settings)
        assertEquals(9000.0, auto.fee!!, 0.0)
        assertFalse(auto.isManual)

        val manual = EvalCore.effectiveFee(project("b", "B", feeManual = 8000.0), settings)
        assertEquals(8000.0, manual.fee!!, 0.0)
        assertTrue(manual.isManual)

        val sameAsRule = EvalCore.effectiveFee(project("c", "C", feeManual = 9000.0), settings)
        assertFalse(sameAsRule.isManual)

        val pending = EvalCore.effectiveFee(project("d", "D", level = "四级"), settings)
        assertTrue(pending.pendingRate)
        assertNull(pending.fee)

        val unscheduled = EvalCore.effectiveFee(project("e", "E", start = "", end = ""), settings)
        assertEquals(0.0, unscheduled.fee!!, 0.0)
        assertEquals(0, unscheduled.days)
    }

    @Test
    fun endDateManual_detectsDeviationFromRule() {
        assertFalse(EvalCore.isEndDateManual(project("a", "A"), settings))
        assertTrue(EvalCore.isEndDateManual(project("a", "A", end = "2026-12-16"), settings))
    }

    @Test
    fun overlapAndIntersection() {
        assertEquals(1, EvalCore.overlapDays("2026-11-30", "2026-12-01", "2026-11-01", "2026-11-30"))
        assertEquals(1, EvalCore.overlapDays("2026-11-30", "2026-12-01", "2026-12-01", "2026-12-31"))
        val p = project("a", "A", start = "2026-11-30", end = "2026-12-02")
        assertTrue(EvalCore.intersectsRange(p, "2026-12-01", "2026-12-31"))
        assertFalse(EvalCore.intersectsRange(p, "2026-12-03", ""))
        assertFalse(EvalCore.intersectsRange(project("u", "U", start = "", end = ""), "", ""))
    }

    @Test
    fun mergedGroupStatus_prefersFormalStage() {
        val pre = project("p", "X", stage = "预评估", status = "已完成")
        val formal = project("f", "X", stage = "正式评估", status = "进行中")
        assertEquals("进行中", EvalCore.mergedGroupStatus(listOf(pre, formal)))
        assertEquals("已完成", EvalCore.mergedGroupStatus(listOf(pre)))
    }

    @Test
    fun nearestStage_picksClosestStart() {
        val list = listOf(
            project("p", "X", stage = "预评估", start = "2026-11-01", end = "2026-11-02"),
            project("f", "X", stage = "正式评估", start = "2026-12-13", end = "2026-12-15")
        )
        assertEquals("正式评估", EvalCore.findNearestStage("X", "2026-12-10", list))
        assertEquals("预评估", EvalCore.findNearestStage("X", "2026-11-03", list))
        assertEquals("预评估", EvalCore.findNearestStage("Unknown", "2026-11-03", list))
    }

    @Test
    fun formatMoney_usesGroupingAndTwoDecimals() {
        assertEquals("1,234.50", EvalCore.formatMoney(1234.5))
        assertEquals("0.00", EvalCore.formatMoney(0.0))
        assertEquals("待设置", EvalCore.formatMoney(null))
    }

    @Test
    fun projectKpis_countDistinctNames() {
        val list = listOf(
            project("p", "X", stage = "预评估", start = "2026-12-10", end = "2026-12-11", settle = "已结"),
            project("f", "X"),
            project("y", "Y", level = "四级")
        )
        val k = EvalStats.projectKpis(list, settings)
        assertEquals(2, k.projectCount)
        assertEquals(3, k.recordCount)
        assertEquals(1, k.pendingRateCount)
        // Pre: 2 days x 3000 = 6000 (settled); formal: 3 x 3000 = 9000.
        assertEquals(15000.0, k.totalFee, 0.0)
        assertEquals(6000.0, k.settledFee, 0.0)
        assertEquals(9000.0, k.unsettledFee, 0.0)
    }

    @Test
    fun filterProjects_timeRangeUsesIntersection() {
        val list = listOf(
            project("a", "A", start = "2026-11-30", end = "2026-12-02"),
            project("b", "B", start = "2026-10-01", end = "2026-10-02"),
            project("u", "U", start = "", end = "")
        )
        val f = ProjectFilter(timeType = ProjectTimeType.MONTH, month = java.time.YearMonth.of(2026, 12))
        assertEquals(listOf("a"), EvalStats.filterProjects(list, f).map { it.id })
        assertEquals(3, EvalStats.filterProjects(list, ProjectFilter()).size)
    }

    @Test
    fun buildGroups_sortsStagesAndKeepsFullIds() {
        val list = listOf(
            project("f", "X", stage = "正式评估", start = "2026-12-13"),
            project("p", "X", stage = "预评估", start = "2026-12-01", end = "2026-12-02"),
            project("y", "Y", start = "2026-11-01", end = "2026-11-03")
        )
        val filtered = list.filter { it.id != "p" }
        val groups = EvalStats.buildGroups(filtered, list, settings, ProjectSort(ProjectSortField.START, ascending = true))
        assertEquals(listOf("Y", "X"), groups.map { it.name })
        val x = groups.last()
        assertEquals(setOf("f", "p"), x.allRecordIds.toSet())
        assertEquals(listOf("f"), x.rows.map { it.project.id })
        assertNull(x.missingStage)
    }

    @Test
    fun feeStats_attributeByStartMonthAndDedupNames() {
        val list = listOf(
            project("p", "X", stage = "预评估", start = "2026-11-30", end = "2026-12-01"),
            project("f", "X", start = "2026-12-13", end = "2026-12-15", settle = "已结"),
            project("z", "Z", level = "四级", start = "2026-12-01", end = "2026-12-01")
        )
        val s = EvalStats.feeStats(list, settings, FeeFilter(preset = RangePreset.ALL, start = "", end = "", groupBy = FeeGroupBy.MONTH))
        assertEquals(2, s.projectCount)
        assertEquals(1, s.pendingRateProjectCount)
        assertEquals(listOf("2026-11", "2026-12"), s.monthly.map { it.month })
        assertEquals(6000.0, s.monthly[0].total, 0.0)
        assertEquals(9000.0, s.monthly[1].settled, 0.0)
        assertEquals(2, s.unsettledProjectCount)
    }

    @Test
    fun daysStats_splitAcrossMonths() {
        val list = listOf(
            project("a", "A", start = "2026-11-29", end = "2026-12-02", status = "进行中"),
            project("b", "B", start = "2026-12-20", end = "2026-12-21")
        )
        val s = EvalStats.daysStats(list, "2026-11-30", DaysWindow(DaysWindowMode.WEEK))
        assertEquals(listOf("2026-11", "2026-12"), s.monthly.map { it.month })
        assertEquals(2, s.monthly[0].days)
        assertEquals(4, s.monthly[1].days)
        assertEquals(2, s.monthly[1].doingDays)
        assertEquals(1, s.todayInEval)
        assertEquals(2, s.elapsedTotal)
        assertEquals(2, s.futurePlanDays)
        assertEquals(2, s.thisMonthDays)
        assertEquals(1, s.windowProjectCount)
    }

    @Test
    fun travelTotalsAndGroups() {
        val projects = listOf(project("f", "X"))
        val list = listOf(
            travel("1", "X", 100.10, "未报"),
            travel("2", "X", 200.20, "已报"),
            travel("3", "Y", 50.0, "已结")
        )
        val t = EvalStats.travelTotals(list)
        assertEquals(350.3, t.total, 0.0)
        assertEquals(2, t.projectCount)
        assertEquals(100.1, t.unrep, 0.0)

        val groups = EvalStats.travelGroups(list, projects, merge = true)
        assertEquals(2, groups.size)
        assertEquals("三级", groups[0].link.level)
        assertEquals("2026/12/13 ~ 2026/12/15", groups[0].link.planText)
        assertEquals(3, EvalStats.travelGroups(list, projects, merge = false).size)
    }

    // ------------------------------------------------------------ same-day travel order

    private fun ordered(id: String, date: String, sortOrder: Int, name: String = "项目", createdAt: String = "") =
        travel(id, name, 10.0, date = date).copy(sortOrder = sortOrder, createdAt = createdAt)

    @Test
    fun travelsSortBySortOrderWithinDayRegardlessOfDateDirection() {
        val list = listOf(
            ordered("a2", "2026-11-23", 2, name = "甲"),
            ordered("b1", "2026-11-24", 1),
            ordered("a1", "2026-11-23", 1, name = "乙"),
            ordered("a3", "2026-11-23", 3, name = "丙")
        )
        val desc = EvalStats.filterTravels(list, TravelFilter(ascending = false)).map { it.id }
        val asc = EvalStats.filterTravels(list, TravelFilter(ascending = true)).map { it.id }
        assertEquals(listOf("b1", "a1", "a2", "a3"), desc)
        assertEquals(listOf("a1", "a2", "a3", "b1"), asc)
    }

    @Test
    fun travelsWithEqualSortOrderFallBackToNameThenCreatedAt() {
        // Old servers send no sortOrder (0 everywhere): order stays the previous name order.
        val list = listOf(
            ordered("late", "2026-11-23", 0, name = "A", createdAt = "2026-11-23T02:00:00Z"),
            ordered("b", "2026-11-23", 0, name = "B"),
            ordered("early", "2026-11-23", 0, name = "A", createdAt = "2026-11-23T01:00:00Z")
        )
        assertEquals(listOf("early", "late", "b"), list.sortedWith(EvalCore.travelComparator(true)).map { it.id })
    }

    @Test
    fun buildTravelDayOrderReturnsWholeDayInNewOrder() {
        val list = listOf(
            ordered("t1", "2026-11-23", 1),
            ordered("t2", "2026-11-23", 2),
            ordered("t3", "2026-11-23", 3),
            ordered("other", "2026-11-24", 1)
        )
        assertEquals(listOf("t3", "t1", "t2"), EvalCore.buildTravelDayOrder(list, "2026-11-23", listOf("t3", "t1", "t2")))
    }

    @Test
    fun buildTravelDayOrderKeepsRowsNotOnScreenInPlace() {
        // h1 / h2 are filtered out or live in another project group; only v1..v3 were dragged.
        val list = listOf(
            ordered("v1", "2026-11-23", 1),
            ordered("h1", "2026-11-23", 2),
            ordered("v2", "2026-11-23", 3),
            ordered("v3", "2026-11-23", 4),
            ordered("h2", "2026-11-23", 5)
        )
        assertEquals(
            listOf("v3", "h1", "v1", "v2", "h2"),
            EvalCore.buildTravelDayOrder(list, "2026-11-23", listOf("v3", "v1", "v2"))
        )
    }

    @Test
    fun buildTravelDayOrderRejectsUnchangedForeignOrDuplicateIds() {
        val list = listOf(
            ordered("t1", "2026-11-23", 1),
            ordered("t2", "2026-11-23", 2),
            ordered("x", "2026-11-24", 1)
        )
        assertNull(EvalCore.buildTravelDayOrder(list, "2026-11-23", listOf("t1", "t2")))
        assertNull(EvalCore.buildTravelDayOrder(list, "2026-11-23", listOf("x", "t1")))
        assertNull(EvalCore.buildTravelDayOrder(list, "2026-11-23", listOf("t2", "t2")))
        assertNull(EvalCore.buildTravelDayOrder(list, "2026-11-23", emptyList()))
    }
}
