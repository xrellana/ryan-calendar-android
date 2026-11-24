package com.ryancalendar.android.domain.eval

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

data class FeeResult(
    val days: Int,
    val unitRate: Double?,
    val autoFee: Double?,
    /** Null only when the rate is missing and no manual value exists ("待设置"). */
    val fee: Double?,
    /** True when a manual fee differs from the rule value (shows the "改" badge). */
    val isManual: Boolean,
    val pendingRate: Boolean
)

data class MergedGroupMeta(
    val anchorStart: String,
    val latestEnd: String,
    val totalDays: Int,
    val totalFee: Double?,
    val hasPendingRate: Boolean,
    val status: String,
    val allSettled: Boolean,
    val primaryLevel: String
)

data class TravelLink(
    val linked: Boolean,
    val level: String,
    val planStart: String,
    val planEnd: String,
    val planText: String
)

/**
 * Kotlin port of js/eval-core.js. Every rule here must stay identical to the web client so
 * that both clients show the same numbers; see docs/API.md §8 in the web repository.
 */
object EvalCore {
    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val JS_EPSILON = Math.ulp(1.0)

    /** Mirrors JS `Math.round((n + Number.EPSILON) * 100) / 100` (round half toward +infinity). */
    fun round2(n: Double): Double = floor((n + JS_EPSILON) * 100.0 + 0.5) / 100.0

    fun isValidDate(s: String?): Boolean = parseDate(s) != null

    fun parseDate(s: String?): LocalDate? {
        if (s == null || !DATE_RE.matches(s)) return null
        return try {
            LocalDate.parse(s)
        } catch (_: Exception) {
            null
        }
    }

    fun addDays(date: String, delta: Long): String = parseDate(date)?.plusDays(delta)?.toString().orEmpty()

    /** Signed day distance from [start] to [end]; null when either side is invalid. */
    fun diffDays(start: String, end: String): Long? {
        val a = parseDate(start) ?: return null
        val b = parseDate(end) ?: return null
        return ChronoUnit.DAYS.between(a, b)
    }

    fun ruleDays(level: String, stage: String, settings: EvalSettings): Int? {
        val v = settings.daysRules[level]?.get(stage) ?: return null
        return if (v > 0) v else null
    }

    fun unitRate(level: String, role: String, settings: EvalSettings): Double? {
        val v = settings.feeRates[level]?.get(role) ?: return null
        return if (v.isFinite()) round2(v) else null
    }

    fun autoEndDate(start: String, level: String, stage: String, settings: EvalSettings): String {
        if (!isValidDate(start)) return ""
        val rd = ruleDays(level, stage, settings) ?: return ""
        return addDays(start, (rd - 1).toLong())
    }

    fun isEndDateManual(project: EvalProject, settings: EvalSettings): Boolean {
        if (!isValidDate(project.start) || !isValidDate(project.end)) return false
        val autoEnd = autoEndDate(project.start, project.level, project.stage, settings)
        if (autoEnd.isEmpty()) return false
        return project.end != autoEnd
    }

    /** Natural days including both ends; 0 when a side is missing or end < start. */
    fun calcDays(start: String, end: String): Int {
        val d = diffDays(start, end) ?: return 0
        if (d < 0) return 0
        return (d + 1).toInt()
    }

    fun autoFee(days: Int, level: String, role: String, settings: EvalSettings): Double? {
        val rate = unitRate(level, role, settings) ?: return null
        if (days <= 0) return 0.0
        return round2(days * rate)
    }

    fun effectiveFee(project: EvalProject, settings: EvalSettings): FeeResult =
        effectiveFee(project.start, project.end, project.level, project.role, project.feeManual, settings)

    fun effectiveFee(
        start: String,
        end: String,
        level: String,
        role: String,
        feeManual: Double?,
        settings: EvalSettings
    ): FeeResult {
        val days = calcDays(start, end)
        val rate = unitRate(level, role, settings)
        val auto = autoFee(days, level, role, settings)
        if (feeManual != null && feeManual.isFinite()) {
            val manual = round2(feeManual)
            val differs = auto == null || abs(manual - auto) >= 0.005
            return FeeResult(days, rate, auto, manual, isManual = differs, pendingRate = false)
        }
        if (rate == null) {
            return FeeResult(days, null, null, null, isManual = false, pendingRate = true)
        }
        return FeeResult(days, rate, auto, auto, isManual = false, pendingRate = false)
    }

    /** Overlap in natural days between the closed ranges [s1, e1] and [s2, e2]. */
    fun overlapDays(s1: String, e1: String, s2: String, e2: String): Int {
        if (!isValidDate(s1) || !isValidDate(s2) || !isValidDate(e2)) return 0
        val effE1 = if (isValidDate(e1) && e1 >= s1) e1 else s1
        val start = maxOf(s1, s2)
        val end = minOf(effE1, e2)
        if (end < start) return 0
        return calcDays(start, end)
    }

    /** Range intersection used by list filters; unscheduled projects never match. */
    fun intersectsRange(project: EvalProject, rangeStart: String, rangeEnd: String): Boolean {
        if (!isValidDate(project.start)) return false
        val pEnd = effectiveEnd(project)
        if (rangeStart.isNotEmpty() && pEnd < rangeStart) return false
        if (rangeEnd.isNotEmpty() && project.start > rangeEnd) return false
        return true
    }

    fun effectiveEnd(project: EvalProject): String =
        if (isValidDate(project.end) && project.end >= project.start) project.end else project.start

    /** Group status: the formal stage wins; otherwise the first (pre-assessment) record. */
    fun mergedGroupStatus(records: List<EvalProject>): String {
        if (records.isEmpty()) return EvalEnums.STATUS_PENDING
        val formal = records.lastOrNull { it.stage == EvalEnums.STAGE_FORMAL }
        if (formal != null) return formal.status.ifEmpty { EvalEnums.STATUS_PENDING }
        return records[0].status.ifEmpty { EvalEnums.STATUS_PENDING }
    }

    fun mergedGroupMeta(records: List<EvalProject>, settings: EvalSettings): MergedGroupMeta {
        var preStart = ""
        var anyStart = ""
        var latestEnd = ""
        var totalDays = 0
        var totalFee = 0.0
        var hasPending = false
        var allSettled = records.isNotEmpty()
        val levels = mutableListOf<String>()
        for (r in records) {
            if (r.stage == EvalEnums.STAGE_PRE && isValidDate(r.start)) {
                if (preStart.isEmpty() || r.start < preStart) preStart = r.start
            }
            if (isValidDate(r.start)) {
                if (anyStart.isEmpty() || r.start < anyStart) anyStart = r.start
            }
            if (isValidDate(r.end)) {
                if (latestEnd.isEmpty() || r.end > latestEnd) latestEnd = r.end
            }
            val eff = effectiveFee(r, settings)
            totalDays += eff.days
            if (eff.pendingRate) {
                hasPending = true
            } else if (eff.fee != null) {
                totalFee = round2(totalFee + eff.fee)
            }
            if (r.settle != EvalEnums.SETTLE_DONE) allSettled = false
            if (r.level.isNotEmpty()) levels.add(r.level)
        }
        return MergedGroupMeta(
            anchorStart = preStart.ifEmpty { anyStart },
            latestEnd = latestEnd,
            totalDays = totalDays,
            totalFee = if (hasPending && totalFee == 0.0) null else totalFee,
            hasPendingRate = hasPending,
            status = mergedGroupStatus(records),
            allSettled = allSettled,
            primaryLevel = levels.firstOrNull().orEmpty()
        )
    }

    /** In-group order is fixed: pre-assessment first, then formal, then by start and creation time. */
    fun sortGroupRecords(records: List<EvalProject>): List<EvalProject> =
        records.sortedWith(
            compareBy<EvalProject> { if (it.stage == EvalEnums.STAGE_PRE) 0 else 1 }
                .thenBy { it.start.ifEmpty { "9999-99-99" } }
                .thenBy { it.createdAt }
        )

    fun formatDateSlash(date: String): String = if (isValidDate(date)) date.replace('-', '/') else ""

    /** Short form for cards: "12/13" in the current year, "2025/12/13" otherwise. */
    fun formatDateShort(date: String, todayYear: Int): String {
        val d = parseDate(date) ?: return ""
        return if (d.year == todayYear) "%02d/%02d".format(d.monthValue, d.dayOfMonth)
        else formatDateSlash(date)
    }

    fun formatPlanText(start: String, end: String): String {
        if (!isValidDate(start)) return ""
        if (!isValidDate(end) || end == start) return formatDateSlash(start)
        return formatDateSlash(start) + " ~ " + formatDateSlash(end)
    }

    private fun nearestByStart(matches: List<EvalProject>, travelDate: String): EvalProject {
        var best = matches[0]
        var bestDist = Long.MAX_VALUE
        for (m in matches) {
            val d = diffDays(m.start, travelDate)?.let { abs(it) } ?: Long.MAX_VALUE
            if (d < bestDist) {
                bestDist = d
                best = m
            }
        }
        return best
    }

    fun findLinkedProject(name: String, stage: String, travelDate: String, projects: List<EvalProject>): EvalProject? {
        val target = name.trim()
        if (target.isEmpty()) return null
        val matches = projects.filter { it.name.trim() == target && it.stage == stage }
        if (matches.isEmpty()) return null
        if (matches.size == 1 || !isValidDate(travelDate)) return matches[0]
        return nearestByStart(matches, travelDate)
    }

    fun findNearestStage(name: String, travelDate: String, projects: List<EvalProject>): String {
        val target = name.trim()
        if (target.isEmpty()) return EvalEnums.STAGE_PRE
        val matches = projects.filter { it.name.trim() == target }
        if (matches.isEmpty()) return EvalEnums.STAGE_PRE
        if (matches.size == 1) return matches[0].stage.ifEmpty { EvalEnums.STAGE_PRE }
        if (!isValidDate(travelDate)) {
            return if (matches.any { it.stage == EvalEnums.STAGE_PRE }) EvalEnums.STAGE_PRE else EvalEnums.STAGE_FORMAL
        }
        return nearestByStart(matches, travelDate).stage.ifEmpty { EvalEnums.STAGE_PRE }
    }

    fun resolveTravelLink(travel: EvalTravel, projects: List<EvalProject>): TravelLink {
        val linked = findLinkedProject(travel.name, travel.stage, travel.date, projects)
        if (linked != null) {
            return TravelLink(
                linked = true,
                level = linked.level.ifEmpty { travel.level },
                planStart = linked.start,
                planEnd = linked.end,
                planText = formatPlanText(linked.start, linked.end).ifEmpty { travel.planText }
            )
        }
        return TravelLink(
            linked = false,
            level = travel.level,
            planStart = travel.planStart,
            planEnd = travel.planEnd,
            planText = travel.planText.ifEmpty { formatPlanText(travel.planStart, travel.planEnd) }
        )
    }

    fun travelDisplayCat(travel: EvalTravel): String {
        if (travel.cat == EvalEnums.CAT_CUSTOM) return travel.catCustom.trim().ifEmpty { EvalEnums.CAT_CUSTOM }
        return travel.cat.ifEmpty { EvalEnums.CAT_DEFAULT }
    }

    private val moneyFormat: ThreadLocal<DecimalFormat> = ThreadLocal.withInitial {
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).apply { roundingMode = RoundingMode.HALF_UP }
    }

    /** "1,234.50"; null renders as "待设置" like the web client. */
    fun formatMoney(n: Double?): String {
        if (n == null || !n.isFinite()) return "待设置"
        return moneyFormat.get()!!.format(BigDecimal.valueOf(n))
    }

    fun monthRange(ym: YearMonth): Pair<String, String> = ym.atDay(1).toString() to ym.atEndOfMonth().toString()

    fun quarterRange(year: Int, quarter: Int): Pair<String, String> {
        val startMonth = (quarter - 1) * 3 + 1
        return YearMonth.of(year, startMonth).atDay(1).toString() to
            YearMonth.of(year, startMonth + 2).atEndOfMonth().toString()
    }

    fun quarterOf(date: LocalDate): Int = (date.monthValue - 1) / 3 + 1
}
