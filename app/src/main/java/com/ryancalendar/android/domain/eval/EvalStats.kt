package com.ryancalendar.android.domain.eval

import java.text.Collator
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

// ---------------------------------------------------------------------------
// Filter and sort definitions
// ---------------------------------------------------------------------------

enum class ProjectTimeType(val label: String) {
    ALL("全部时间"), YEAR("按年"), QUARTER("按季"), MONTH("按月"), CUSTOM("自定义")
}

data class ProjectFilter(
    val query: String = "",
    val levels: Set<String> = emptySet(),
    val roles: Set<String> = emptySet(),
    val stages: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val settles: Set<String> = emptySet(),
    val timeType: ProjectTimeType = ProjectTimeType.ALL,
    val year: Int = LocalDate.now().year,
    val quarter: Int = EvalCore.quarterOf(LocalDate.now()),
    val month: YearMonth = YearMonth.now(),
    val customStart: String = "",
    val customEnd: String = ""
) {
    /** Number of active conditions, excluding the search query (shown as a badge). */
    val activeCount: Int
        get() = listOf(levels, roles, stages, statuses, settles).count { it.isNotEmpty() } +
            (if (timeType != ProjectTimeType.ALL) 1 else 0)

    fun timeRange(): Pair<String, String> = when (timeType) {
        ProjectTimeType.ALL -> "" to ""
        ProjectTimeType.YEAR -> "$year-01-01" to "$year-12-31"
        ProjectTimeType.QUARTER -> EvalCore.quarterRange(year, quarter)
        ProjectTimeType.MONTH -> EvalCore.monthRange(month)
        ProjectTimeType.CUSTOM -> customStart to customEnd
    }

    fun timeLabel(): String = when (timeType) {
        ProjectTimeType.ALL -> "全部时间"
        ProjectTimeType.YEAR -> "${year}年"
        ProjectTimeType.QUARTER -> "${year}年第${quarter}季度"
        ProjectTimeType.MONTH -> "${month.year}年${month.monthValue}月"
        ProjectTimeType.CUSTOM -> listOf(customStart, customEnd)
            .joinToString(" ~ ") { EvalCore.formatDateSlash(it).ifEmpty { "不限" } }
    }
}

enum class ProjectSortField(val label: String) {
    START("开始时间"), END("结束时间"), NAME("项目名称"), LEVEL("等级"), STAGE("评估阶段"),
    DAYS("天数"), STATUS("项目状态"), FEE("评估费用"), SETTLE("费用状态")
}

data class ProjectSort(val field: ProjectSortField = ProjectSortField.START, val ascending: Boolean = false)

enum class RangePreset(val label: String) {
    MONTH("本月"), QUARTER("本季"), YEAR("本年"), ALL("全部"), CUSTOM("自定义")
}

fun presetRange(preset: RangePreset, today: LocalDate): Pair<String, String> = when (preset) {
    RangePreset.MONTH -> EvalCore.monthRange(YearMonth.from(today))
    RangePreset.QUARTER -> EvalCore.quarterRange(today.year, EvalCore.quarterOf(today))
    RangePreset.YEAR -> "${today.year}-01-01" to "${today.year}-12-31"
    RangePreset.ALL, RangePreset.CUSTOM -> "" to ""
}

enum class FeeGroupBy(val label: String) {
    MONTH("年月"), LEVEL("等级"), ROLE("角色"), SETTLE("费用状态"), STATUS("项目状态"), STAGE("评估阶段")
}

data class FeeFilter(
    val preset: RangePreset = RangePreset.YEAR,
    val start: String = "${LocalDate.now().year}-01-01",
    val end: String = "${LocalDate.now().year}-12-31",
    val levels: Set<String> = emptySet(),
    val roles: Set<String> = emptySet(),
    val settles: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val groupBy: FeeGroupBy = FeeGroupBy.MONTH
) {
    val activeCount: Int
        get() = listOf(levels, roles, settles, statuses).count { it.isNotEmpty() }
}

enum class DaysWindowMode(val label: String, val spanDays: Long) {
    TODAY("今天", 0), WEEK("未来一周", 6), MONTH("未来一月", 29), QUARTER("未来一季", 89), CUSTOM("自定义", 0)
}

data class DaysWindow(
    val mode: DaysWindowMode = DaysWindowMode.WEEK,
    val customStart: String = "",
    val customEnd: String = ""
) {
    fun range(today: String): Pair<String, String> = when (mode) {
        DaysWindowMode.CUSTOM -> {
            val cs = customStart.ifEmpty { today }
            cs to customEnd.ifEmpty { cs }
        }
        else -> today to EvalCore.addDays(today, mode.spanDays)
    }
}

enum class TravelGroupBy(val label: String) {
    CAT("差旅类别"), PROJECT("项目"), MONTH("月份"), REIMB("报销状态")
}

data class TravelFilter(
    val preset: RangePreset = RangePreset.ALL,
    val start: String = "",
    val end: String = "",
    val names: Set<String> = emptySet(),
    val cats: Set<String> = emptySet(),
    val reimbs: Set<String> = emptySet(),
    val query: String = "",
    val groupBy: TravelGroupBy = TravelGroupBy.CAT,
    val ascending: Boolean = false
) {
    val activeCount: Int
        get() = listOf(names, cats, reimbs).count { it.isNotEmpty() } + (if (preset != RangePreset.ALL) 1 else 0)
}

// ---------------------------------------------------------------------------
// Result models
// ---------------------------------------------------------------------------

data class ProjectKpis(
    val projectCount: Int,
    val recordCount: Int,
    val inProgress: Int,
    val totalDays: Int,
    val totalFee: Double,
    val settledFee: Double,
    val unsettledFee: Double,
    val pendingRateCount: Int
)

data class ProjectRow(val project: EvalProject, val fee: FeeResult, val endManual: Boolean)

data class ProjectGroup(
    val name: String,
    val rows: List<ProjectRow>,
    /** All records sharing the name, including ones hidden by filters (selection targets these). */
    val allRecordIds: List<String>,
    val meta: MergedGroupMeta,
    val overallStatus: String,
    val sameLevel: Boolean,
    val sameRole: Boolean,
    val missingStage: String?
)

data class FeeMonthRow(val month: String, val total: Double, val settled: Double, val unsettled: Double)

data class FeeGroupRow(
    val key: String,
    val projectCount: Int,
    val days: Int,
    val fee: Double,
    val settled: Double,
    val unsettled: Double,
    val projectIds: List<String>
)

data class FeeStatsResult(
    val projectCount: Int,
    val recordCount: Int,
    val totalDays: Int,
    val totalFee: Double,
    val settledFee: Double,
    val unsettledFee: Double,
    val unsettledProjectCount: Int,
    val pendingRateProjectCount: Int,
    val monthly: List<FeeMonthRow>,
    val groups: List<FeeGroupRow>
)

data class ProgressRow(
    val project: EvalProject,
    val effEnd: String,
    val days: Int,
    val elapsed: Int,
    val remaining: Int,
    val pct: Int
)

data class DaysMonthRow(
    val month: String,
    val days: Int,
    val projectCount: Int,
    val doneDays: Int,
    val doingDays: Int,
    val isFuture: Boolean
)

data class DaysStatsResult(
    val windowStart: String,
    val windowEnd: String,
    val todayInEval: Int,
    val windowProjectCount: Int,
    val elapsedTotal: Int,
    val thisMonthDays: Int,
    val futurePlanDays: Int,
    val allPlanDays: Int,
    val uniqueProjectCount: Int,
    val recent: List<ProgressRow>,
    val progress: List<ProgressRow>,
    val monthly: List<DaysMonthRow>
)

data class TravelTotals(
    val total: Double,
    val count: Int,
    val projectCount: Int,
    val unrep: Double,
    val unrepCount: Int,
    val rep: Double,
    val repCount: Int,
    val done: Double,
    val doneCount: Int
)

data class TravelCatRow(val name: String, val amount: Double, val count: Int)

data class TravelSummaryRow(
    val key: String,
    val count: Int,
    val total: Double,
    val unrep: Double,
    val rep: Double,
    val done: Double
)

data class TravelGroup(val key: String, val first: EvalTravel, val link: TravelLink, val rows: List<EvalTravel>)

// ---------------------------------------------------------------------------
// Computations (ported from js/eval-ui.js render functions)
// ---------------------------------------------------------------------------

object EvalStats {
    private val zhCollator: Collator = Collator.getInstance(Locale.CHINA)

    fun filterProjects(all: List<EvalProject>, f: ProjectFilter): List<EvalProject> {
        val q = f.query.trim().lowercase()
        val (rs, re) = f.timeRange()
        val hasTime = f.timeType != ProjectTimeType.ALL && (rs.isNotEmpty() || re.isNotEmpty())
        return all.filter { p ->
            if (q.isNotEmpty()) {
                val hay = "${p.name} ${p.note} ${p.role}".lowercase()
                if (!hay.contains(q)) return@filter false
            }
            if (f.levels.isNotEmpty() && p.level !in f.levels) return@filter false
            if (f.roles.isNotEmpty() && p.role !in f.roles) return@filter false
            if (f.stages.isNotEmpty() && p.stage !in f.stages) return@filter false
            if (f.statuses.isNotEmpty() && p.status !in f.statuses) return@filter false
            if (f.settles.isNotEmpty() && p.settle !in f.settles) return@filter false
            if (hasTime && !EvalCore.intersectsRange(p, rs, re)) return@filter false
            true
        }
    }

    /** Null and empty values always sink to the bottom, whatever the direction. */
    private fun <T : Comparable<T>> cmpNullable(a: T?, b: T?, asc: Boolean): Int {
        val aNull = a == null || (a is String && a.isEmpty())
        val bNull = b == null || (b is String && b.isEmpty())
        if (aNull && bNull) return 0
        if (aNull) return 1
        if (bNull) return -1
        val diff = a!!.compareTo(b!!)
        return if (asc) diff else -diff
    }

    private fun cmpName(a: String, b: String, asc: Boolean): Int {
        if (a.isEmpty() && b.isEmpty()) return 0
        if (a.isEmpty()) return 1
        if (b.isEmpty()) return -1
        val diff = zhCollator.compare(a, b)
        return if (asc) diff else -diff
    }

    fun toRow(p: EvalProject, settings: EvalSettings) =
        ProjectRow(p, EvalCore.effectiveFee(p, settings), EvalCore.isEndDateManual(p, settings))

    fun sortFlat(records: List<EvalProject>, settings: EvalSettings, sort: ProjectSort): List<ProjectRow> {
        val asc = sort.ascending
        return records.map { toRow(it, settings) }.sortedWith { ra, rb ->
            val a = ra.project
            val b = rb.project
            val cmp = when (sort.field) {
                ProjectSortField.NAME -> cmpName(a.name, b.name, asc)
                ProjectSortField.LEVEL -> cmpNullable(EvalEnums.LEVEL_RANK[a.level], EvalEnums.LEVEL_RANK[b.level], asc)
                ProjectSortField.STAGE -> cmpNullable(EvalEnums.STAGE_RANK[a.stage], EvalEnums.STAGE_RANK[b.stage], asc)
                ProjectSortField.START -> cmpNullable(a.start, b.start, asc)
                ProjectSortField.END -> cmpNullable(a.end, b.end, asc)
                ProjectSortField.DAYS -> cmpNullable(ra.fee.days, rb.fee.days, asc)
                ProjectSortField.STATUS -> cmpNullable(EvalEnums.STATUS_RANK[a.status], EvalEnums.STATUS_RANK[b.status], asc)
                ProjectSortField.FEE -> cmpNullable(ra.fee.fee, rb.fee.fee, asc)
                ProjectSortField.SETTLE -> cmpNullable(EvalEnums.SETTLE_RANK[a.settle], EvalEnums.SETTLE_RANK[b.settle], asc)
            }
            if (cmp != 0) cmp else b.start.compareTo(a.start)
        }
    }

    fun buildGroups(
        filtered: List<EvalProject>,
        all: List<EvalProject>,
        settings: EvalSettings,
        sort: ProjectSort
    ): List<ProjectGroup> {
        val byName = LinkedHashMap<String, MutableList<EvalProject>>()
        filtered.forEach { byName.getOrPut(it.name) { mutableListOf() }.add(it) }
        val fullByName = all.groupBy { it.name }

        val groups = byName.map { (name, recs) ->
            val visible = EvalCore.sortGroupRecords(recs)
            val full = EvalCore.sortGroupRecords(fullByName[name] ?: visible)
            val meta = EvalCore.mergedGroupMeta(visible, settings)
            val fullMeta = EvalCore.mergedGroupMeta(full, settings)
            val stagesPresent = full.map { it.stage }.toSet()
            // Only suggest the usual next step (pre -> formal) to keep cards uncluttered.
            val missing = EvalEnums.STAGE_FORMAL.takeIf { it !in stagesPresent }
            ProjectGroup(
                name = name,
                rows = visible.map { toRow(it, settings) },
                allRecordIds = full.map { it.id },
                meta = meta,
                overallStatus = fullMeta.status,
                sameLevel = visible.all { it.level == visible[0].level },
                sameRole = visible.all { it.role == visible[0].role },
                missingStage = missing
            )
        }

        val asc = sort.ascending
        return groups.sortedWith { ga, gb ->
            val ma = ga.meta
            val mb = gb.meta
            val cmp = when (sort.field) {
                ProjectSortField.NAME -> cmpName(ga.name, gb.name, asc)
                ProjectSortField.LEVEL -> cmpNullable(EvalEnums.LEVEL_RANK[ma.primaryLevel], EvalEnums.LEVEL_RANK[mb.primaryLevel], asc)
                ProjectSortField.STAGE -> cmpNullable(
                    EvalEnums.STAGE_RANK[ga.rows[0].project.stage],
                    EvalEnums.STAGE_RANK[gb.rows[0].project.stage],
                    asc
                )
                ProjectSortField.START -> cmpNullable(ma.anchorStart, mb.anchorStart, asc)
                ProjectSortField.END -> cmpNullable(ma.latestEnd, mb.latestEnd, asc)
                ProjectSortField.DAYS -> cmpNullable(ma.totalDays, mb.totalDays, asc)
                ProjectSortField.STATUS -> cmpNullable(EvalEnums.STATUS_RANK[ga.overallStatus], EvalEnums.STATUS_RANK[gb.overallStatus], asc)
                ProjectSortField.FEE -> cmpNullable(ma.totalFee, mb.totalFee, asc)
                ProjectSortField.SETTLE -> cmpNullable(if (ma.allSettled) 2 else 1, if (mb.allSettled) 2 else 1, asc)
            }
            if (cmp != 0) cmp else cmpNullable(ma.anchorStart, mb.anchorStart, false)
        }
    }

    fun projectKpis(records: List<EvalProject>, settings: EvalSettings): ProjectKpis {
        val names = HashSet<String>()
        var inProgress = 0
        var totalDays = 0
        var totalFee = 0.0
        var settled = 0.0
        var unsettled = 0.0
        var pending = 0
        records.forEach { r ->
            names.add(r.name)
            if (r.status == EvalEnums.STATUS_IN_PROGRESS) inProgress++
            val eff = EvalCore.effectiveFee(r, settings)
            totalDays += eff.days
            if (eff.pendingRate) {
                pending++
            } else if (eff.fee != null) {
                totalFee = EvalCore.round2(totalFee + eff.fee)
                if (r.settle == EvalEnums.SETTLE_DONE) settled = EvalCore.round2(settled + eff.fee)
                else unsettled = EvalCore.round2(unsettled + eff.fee)
            }
        }
        return ProjectKpis(names.size, records.size, inProgress, totalDays, totalFee, settled, unsettled, pending)
    }

    /** Fee statistics: the range filter matches by project start date (whole-amount attribution). */
    fun feeStats(all: List<EvalProject>, settings: EvalSettings, f: FeeFilter): FeeStatsResult {
        val list = all.filter { p ->
            if (f.start.isNotEmpty() || f.end.isNotEmpty()) {
                if (!EvalCore.isValidDate(p.start)) return@filter false
                if (f.start.isNotEmpty() && p.start < f.start) return@filter false
                if (f.end.isNotEmpty() && p.start > f.end) return@filter false
            }
            if (f.levels.isNotEmpty() && p.level !in f.levels) return@filter false
            if (f.roles.isNotEmpty() && p.role !in f.roles) return@filter false
            if (f.settles.isNotEmpty() && p.settle !in f.settles) return@filter false
            if (f.statuses.isNotEmpty() && p.status !in f.statuses) return@filter false
            true
        }

        var totalDays = 0
        var totalFee = 0.0
        var settled = 0.0
        var unsettled = 0.0
        val names = HashSet<String>()
        val unsettledNames = HashSet<String>()
        val pendingNames = HashSet<String>()

        class MonthAcc(var total: Double = 0.0, var settled: Double = 0.0, var unsettled: Double = 0.0)
        class GroupAcc(
            val names: MutableSet<String> = HashSet(),
            var days: Int = 0,
            var fee: Double = 0.0,
            var settled: Double = 0.0,
            var unsettled: Double = 0.0,
            val ids: MutableList<String> = mutableListOf()
        )
        val monthMap = HashMap<String, MonthAcc>()
        val groupMap = LinkedHashMap<String, GroupAcc>()

        list.forEach { p ->
            val eff = EvalCore.effectiveFee(p, settings)
            totalDays += eff.days
            names.add(p.name)
            if (p.settle == EvalEnums.SETTLE_OPEN) unsettledNames.add(p.name)
            val feeVal = if (eff.pendingRate) 0.0 else eff.fee ?: 0.0
            val isSettled = p.settle == EvalEnums.SETTLE_DONE
            if (eff.pendingRate) {
                pendingNames.add(p.name)
            } else {
                totalFee = EvalCore.round2(totalFee + feeVal)
                if (isSettled) settled = EvalCore.round2(settled + feeVal)
                else unsettled = EvalCore.round2(unsettled + feeVal)
            }
            if (EvalCore.isValidDate(p.start) && !eff.pendingRate) {
                val acc = monthMap.getOrPut(p.start.take(7)) { MonthAcc() }
                acc.total = EvalCore.round2(acc.total + feeVal)
                if (isSettled) acc.settled = EvalCore.round2(acc.settled + feeVal)
                else acc.unsettled = EvalCore.round2(acc.unsettled + feeVal)
            }
            val key = when (f.groupBy) {
                FeeGroupBy.MONTH -> if (EvalCore.isValidDate(p.start)) p.start.take(7) else "未排期"
                FeeGroupBy.LEVEL -> p.level
                FeeGroupBy.ROLE -> p.role
                FeeGroupBy.SETTLE -> p.settle
                FeeGroupBy.STATUS -> p.status
                FeeGroupBy.STAGE -> p.stage
            }
            val g = groupMap.getOrPut(key) { GroupAcc() }
            g.names.add(p.name)
            g.ids.add(p.id)
            g.days += eff.days
            if (!eff.pendingRate) {
                g.fee = EvalCore.round2(g.fee + feeVal)
                if (isSettled) g.settled = EvalCore.round2(g.settled + feeVal)
                else g.unsettled = EvalCore.round2(g.unsettled + feeVal)
            }
        }

        return FeeStatsResult(
            projectCount = names.size,
            recordCount = list.size,
            totalDays = totalDays,
            totalFee = totalFee,
            settledFee = settled,
            unsettledFee = unsettled,
            unsettledProjectCount = unsettledNames.size,
            pendingRateProjectCount = pendingNames.size,
            monthly = monthMap.keys.sorted().map { k ->
                val m = monthMap.getValue(k)
                FeeMonthRow(k, m.total, m.settled, m.unsettled)
            },
            groups = groupMap.map { (k, g) ->
                FeeGroupRow(k, g.names.size, g.days, g.fee, g.settled, g.unsettled, g.ids)
            }.sortedByDescending { it.fee }
        )
    }

    /** Days statistics: monthly days split by the real overlap with each natural month. */
    fun daysStats(all: List<EvalProject>, today: String, window: DaysWindow): DaysStatsResult {
        val todayDate = EvalCore.parseDate(today) ?: LocalDate.now()
        val curYm = YearMonth.from(todayDate)
        val (curMonthStart, curMonthEnd) = EvalCore.monthRange(curYm)
        val (winStart, winEnd) = window.range(today)

        val todayNames = HashSet<String>()
        val winNames = HashSet<String>()
        val uniqueNames = HashSet<String>()
        var elapsedTotal = 0
        var thisMonthDays = 0
        var futurePlanDays = 0
        var allPlanDays = 0
        val progress = mutableListOf<ProgressRow>()
        val recent = mutableListOf<ProgressRow>()

        class MonthAcc(
            var days: Int = 0,
            val names: MutableSet<String> = HashSet(),
            var done: Int = 0,
            var doing: Int = 0
        )
        val bucket = HashMap<String, MonthAcc>()

        all.forEach { p ->
            uniqueNames.add(p.name)
            if (!EvalCore.isValidDate(p.start)) return@forEach
            val effEnd = EvalCore.effectiveEnd(p)
            val days = EvalCore.calcDays(p.start, effEnd)
            allPlanDays += days
            if (p.start <= today && effEnd >= today) todayNames.add(p.name)

            var elapsed = 0
            if (p.start <= today) {
                val capEnd = if (effEnd < today) effEnd else today
                elapsed = EvalCore.calcDays(p.start, capEnd)
            } else {
                futurePlanDays += days
            }
            elapsedTotal += elapsed
            thisMonthDays += EvalCore.overlapDays(p.start, effEnd, curMonthStart, curMonthEnd)

            val pct = if (days > 0) minOf(100, Math.round(elapsed.toDouble() / days * 100).toInt()) else 0
            val row = ProgressRow(p, effEnd, days, elapsed, maxOf(0, days - elapsed), pct)
            progress.add(row)
            if (EvalCore.intersectsRange(p, winStart, winEnd)) {
                winNames.add(p.name)
                recent.add(row)
            }

            var ym = YearMonth.from(LocalDate.parse(p.start))
            val endYm = YearMonth.from(LocalDate.parse(effEnd))
            while (!ym.isAfter(endYm)) {
                val (ms, me) = EvalCore.monthRange(ym)
                val ov = EvalCore.overlapDays(p.start, effEnd, ms, me)
                if (ov > 0) {
                    val acc = bucket.getOrPut(ym.toString()) { MonthAcc() }
                    acc.days += ov
                    acc.names.add(p.name)
                    if (p.status == EvalEnums.STATUS_DONE) acc.done += ov
                    if (p.status == EvalEnums.STATUS_IN_PROGRESS) acc.doing += ov
                }
                ym = ym.plusMonths(1)
            }
        }

        return DaysStatsResult(
            windowStart = winStart,
            windowEnd = winEnd,
            todayInEval = todayNames.size,
            windowProjectCount = winNames.size,
            elapsedTotal = elapsedTotal,
            thisMonthDays = thisMonthDays,
            futurePlanDays = futurePlanDays,
            allPlanDays = allPlanDays,
            uniqueProjectCount = uniqueNames.size,
            recent = recent.sortedBy { it.project.start },
            progress = progress.sortedByDescending { it.project.start },
            monthly = bucket.keys.sorted().map { k ->
                val b = bucket.getValue(k)
                DaysMonthRow(k, b.days, b.names.size, b.done, b.doing, k > curYm.toString())
            }
        )
    }

    fun filterTravels(all: List<EvalTravel>, f: TravelFilter): List<EvalTravel> {
        val q = f.query.trim().lowercase()
        val filtered = all.filter { t ->
            if (f.start.isNotEmpty() && t.date < f.start) return@filter false
            if (f.end.isNotEmpty() && t.date > f.end) return@filter false
            if (f.names.isNotEmpty() && t.name !in f.names) return@filter false
            val cat = EvalCore.travelDisplayCat(t)
            if (f.cats.isNotEmpty() && cat !in f.cats) return@filter false
            if (f.reimbs.isNotEmpty() && t.reimb !in f.reimbs) return@filter false
            if (q.isNotEmpty() && !"${t.name} ${t.detail} $cat".lowercase().contains(q)) return@filter false
            true
        }
        return filtered.sortedWith(EvalCore.travelComparator(f.ascending))
    }

    fun travelTotals(list: List<EvalTravel>): TravelTotals {
        var total = 0.0
        var unrep = 0.0
        var rep = 0.0
        var done = 0.0
        var unrepCnt = 0
        var repCnt = 0
        var doneCnt = 0
        val names = HashSet<String>()
        list.forEach { t ->
            val amt = EvalCore.round2(t.amount)
            total = EvalCore.round2(total + amt)
            names.add(t.name)
            when (t.reimb) {
                EvalEnums.REIMB_NONE -> { unrep = EvalCore.round2(unrep + amt); unrepCnt++ }
                EvalEnums.REIMB_SUBMITTED -> { rep = EvalCore.round2(rep + amt); repCnt++ }
                else -> { done = EvalCore.round2(done + amt); doneCnt++ }
            }
        }
        return TravelTotals(total, list.size, names.size, unrep, unrepCnt, rep, repCnt, done, doneCnt)
    }

    fun travelCategories(list: List<EvalTravel>): List<TravelCatRow> {
        val acc = LinkedHashMap<String, Pair<Double, Int>>()
        list.forEach { t ->
            val c = EvalCore.travelDisplayCat(t)
            val cur = acc[c] ?: (0.0 to 0)
            acc[c] = EvalCore.round2(cur.first + EvalCore.round2(t.amount)) to cur.second + 1
        }
        return acc.map { (k, v) -> TravelCatRow(k, v.first, v.second) }.sortedByDescending { it.amount }
    }

    fun travelSummary(list: List<EvalTravel>, groupBy: TravelGroupBy): List<TravelSummaryRow> {
        class Acc(var count: Int = 0, var total: Double = 0.0, var unrep: Double = 0.0, var rep: Double = 0.0, var done: Double = 0.0)
        val map = LinkedHashMap<String, Acc>()
        list.forEach { t ->
            val key = when (groupBy) {
                TravelGroupBy.CAT -> EvalCore.travelDisplayCat(t)
                TravelGroupBy.PROJECT -> t.name
                TravelGroupBy.MONTH -> t.date.take(7).ifEmpty { "未知" }
                TravelGroupBy.REIMB -> t.reimb
            }
            val a = map.getOrPut(key) { Acc() }
            val amt = EvalCore.round2(t.amount)
            a.count++
            a.total = EvalCore.round2(a.total + amt)
            when (t.reimb) {
                EvalEnums.REIMB_NONE -> a.unrep = EvalCore.round2(a.unrep + amt)
                EvalEnums.REIMB_SUBMITTED -> a.rep = EvalCore.round2(a.rep + amt)
                else -> a.done = EvalCore.round2(a.done + amt)
            }
        }
        return map.map { (k, a) -> TravelSummaryRow(k, a.count, a.total, a.unrep, a.rep, a.done) }
            .sortedByDescending { it.total }
    }

    /**
     * Merge key includes level and plan: same-name, same-stage projects from different years
     * may link to different plans and must not merge.
     */
    fun travelGroups(filtered: List<EvalTravel>, projects: List<EvalProject>, merge: Boolean): List<TravelGroup> {
        if (!merge) {
            return filtered.map { t ->
                TravelGroup(t.id, t, EvalCore.resolveTravelLink(t, projects), listOf(t))
            }
        }
        val index = LinkedHashMap<String, Pair<TravelLink, MutableList<EvalTravel>>>()
        filtered.forEach { t ->
            val link = EvalCore.resolveTravelLink(t, projects)
            val key = listOf(t.name, t.stage, link.level, link.planText).joinToString("||")
            index.getOrPut(key) { link to mutableListOf() }.second.add(t)
        }
        return index.map { (k, v) -> TravelGroup(k, v.second[0], v.first, v.second) }
    }

    fun projectYears(all: List<EvalProject>, todayYear: Int): List<Int> {
        val set = sortedSetOf(todayYear)
        all.forEach { p ->
            EvalCore.parseDate(p.start)?.let { set.add(it.year) }
            EvalCore.parseDate(p.end)?.let { set.add(it.year) }
        }
        return set.toList().sortedDescending()
    }

    /** Distinct project names with their registered stages, ordered by most recent start. */
    fun projectNameIndex(all: List<EvalProject>): List<Pair<String, List<String>>> =
        all.groupBy { it.name }
            .map { (name, recs) -> Triple(name, EvalCore.sortGroupRecords(recs).map { it.stage }.distinct(), recs.maxOf { it.start }) }
            .sortedByDescending { it.third }
            .map { it.first to it.second }

    /**
     * Projects worth suggesting when logging a travel expense on a phone: those running
     * around [today] (from 7 days before start to 7 days after end), nearest first.
     */
    fun nearbyProjects(all: List<EvalProject>, today: String, limit: Int = 6): List<EvalProject> {
        val from = EvalCore.addDays(today, -7)
        val to = EvalCore.addDays(today, 7)
        return all.filter { EvalCore.intersectsRange(it, from, to) }
            .sortedBy { kotlin.math.abs(EvalCore.diffDays(it.start, today) ?: Long.MAX_VALUE / 2) }
            .distinctBy { it.name + "|" + it.stage }
            .take(limit)
    }
}
