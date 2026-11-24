package com.ryancalendar.android.domain.eval

/**
 * Enum value sets shared with the web client (js/eval-core.js). The server stores and
 * validates these exact Chinese literals, so they are data, not display copy.
 */
object EvalEnums {
    const val STAGE_PRE = "预评估"
    const val STAGE_FORMAL = "正式评估"

    const val STATUS_PENDING = "待开始"
    const val STATUS_IN_PROGRESS = "进行中"
    const val STATUS_SUBMITTED = "已提交"
    const val STATUS_DONE = "已完成"

    const val SETTLE_OPEN = "未结"
    const val SETTLE_DONE = "已结"

    const val REIMB_NONE = "未报"
    const val REIMB_SUBMITTED = "已报"
    const val REIMB_DONE = "已结"

    const val CAT_CUSTOM = "自定义"
    const val CAT_DEFAULT = "打车"

    val LEVELS = listOf("一级", "二级", "三级", "四级")
    val ROLES = listOf("组员", "组长")
    val STAGES = listOf(STAGE_PRE, STAGE_FORMAL)
    val PROJECT_STATUSES = listOf(STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_SUBMITTED, STATUS_DONE)
    val SETTLE_STATUSES = listOf(SETTLE_OPEN, SETTLE_DONE)
    val TRAVEL_CATS = listOf("打车", "高铁", "飞机", "自驾", "住宿", "餐饮", CAT_CUSTOM)
    val REIMB_STATUSES = listOf(REIMB_NONE, REIMB_SUBMITTED, REIMB_DONE)

    val LEVEL_RANK = LEVELS.withIndex().associate { it.value to it.index + 1 }
    val STAGE_RANK = STAGES.withIndex().associate { it.value to it.index + 1 }
    val STATUS_RANK = PROJECT_STATUSES.withIndex().associate { it.value to it.index + 1 }
    val SETTLE_RANK = SETTLE_STATUSES.withIndex().associate { it.value to it.index + 1 }

    /** The protected calendar category that is linked to evaluation projects. */
    const val ASSESSMENT_CATEGORY_ID = "cat-assessment"
}

data class EvalProject(
    val id: String,
    val name: String,
    val level: String,
    val role: String,
    val stage: String,
    /** YYYY-MM-DD, or empty for an unscheduled project. */
    val start: String,
    val end: String,
    val status: String,
    val settle: String,
    val note: String,
    /** Null means the fee follows the rate table. */
    val feeManual: Double?,
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class EvalTravel(
    val id: String,
    val date: String,
    val name: String,
    val stage: String,
    val level: String,
    val planStart: String,
    val planEnd: String,
    val planText: String,
    val cat: String,
    val catCustom: String,
    val amount: Double,
    val detail: String,
    val reimb: String,
    val createdAt: String = "",
    val updatedAt: String = "",
    /** Manual order within the same date (1-based, smaller = earlier). Read-only; only POST /travels/reorder changes it. */
    val sortOrder: Int = 0
)

data class EvalSettings(
    /** level -> stage -> rule days (null = no rule). */
    val daysRules: Map<String, Map<String, Int?>>,
    /** level -> role -> yuan per day (null = rate not set). */
    val feeRates: Map<String, Map<String, Double?>>
) {
    companion object {
        val Default = EvalSettings(
            daysRules = mapOf(
                "一级" to mapOf(EvalEnums.STAGE_PRE to null, EvalEnums.STAGE_FORMAL to 2),
                "二级" to mapOf(EvalEnums.STAGE_PRE to 1, EvalEnums.STAGE_FORMAL to 2),
                "三级" to mapOf(EvalEnums.STAGE_PRE to 2, EvalEnums.STAGE_FORMAL to 3),
                "四级" to mapOf(EvalEnums.STAGE_PRE to 2, EvalEnums.STAGE_FORMAL to 4)
            ),
            feeRates = mapOf(
                "一级" to mapOf("组员" to null, "组长" to null),
                "二级" to mapOf("组员" to 1000.0, "组长" to 2000.0),
                "三级" to mapOf("组员" to 1250.0, "组长" to 3000.0),
                "四级" to mapOf("组员" to null, "组长" to null)
            )
        )
    }
}

data class EvalSnapshot(
    val projects: List<EvalProject>,
    val travels: List<EvalTravel>,
    val settings: EvalSettings,
    val mergeNames: Boolean,
    /** False until the first successful load, so screens can tell "empty" from "not loaded". */
    val loaded: Boolean
) {
    companion object {
        val Empty = EvalSnapshot(emptyList(), emptyList(), EvalSettings.Default, mergeNames = true, loaded = false)
    }
}
