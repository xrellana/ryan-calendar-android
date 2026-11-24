package com.ryancalendar.android.ui.eval

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.data.repository.ProjectInput
import com.ryancalendar.android.domain.eval.EvalCore
import com.ryancalendar.android.domain.eval.EvalEnums
import com.ryancalendar.android.domain.eval.EvalProject
import com.ryancalendar.android.domain.eval.EvalStats
import com.ryancalendar.android.domain.util.DateUtils
import com.ryancalendar.android.ui.theme.AppColors
import java.time.LocalDate

/** Plain number for an input field: 2000.0 -> "2000", 326.5 -> "326.5". */
private fun plainNumber(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else EvalCore.round2(v).toString()

@Composable
fun ProjectEditorScreen(
    viewModel: EvalViewModel,
    projectId: String?,
    prefill: ProjectPrefill?,
    assessmentCategoryName: String,
    onBack: () -> Unit,
    onAddTravel: (name: String, stage: String) -> Unit
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val editing = remember(projectId, snapshot) { projectId?.let { id -> snapshot.projects.find { it.id == id } } }

    // A linked calendar event can point to a project the local cache does not have yet.
    var triedRefresh by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(projectId, editing) {
        if (projectId != null && editing == null && !triedRefresh) {
            triedRefresh = true
            viewModel.refresh()
        }
    }

    if (projectId != null && editing == null) {
        MissingRecordScreen(
            title = "编辑评估项目",
            loading = isRefreshing || !triedRefresh,
            message = "未找到该评估项目，可能已在其他设备删除。",
            onBack = onBack
        )
        return
    }

    // Key the form on the record id so a background refresh does not wipe unsaved input.
    ProjectEditorForm(
        viewModel = viewModel,
        editing = remember(projectId) { editing },
        prefill = prefill,
        assessmentCategoryName = assessmentCategoryName,
        onBack = onBack,
        onAddTravel = onAddTravel
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProjectEditorForm(
    viewModel: EvalViewModel,
    editing: EvalProject?,
    prefill: ProjectPrefill?,
    assessmentCategoryName: String,
    onBack: () -> Unit,
    onAddTravel: (name: String, stage: String) -> Unit
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    val settings = snapshot.settings
    val key = editing?.id ?: "new"
    val today = LocalDate.now().toString()

    var name by rememberSaveable(key) { mutableStateOf(editing?.name ?: prefill?.name.orEmpty()) }
    var level by rememberSaveable(key) { mutableStateOf(editing?.level ?: prefill?.level?.ifEmpty { null } ?: "二级") }
    var role by rememberSaveable(key) { mutableStateOf(editing?.role ?: prefill?.role?.ifEmpty { null } ?: "组员") }
    var stage by rememberSaveable(key) { mutableStateOf(editing?.stage ?: prefill?.stage?.ifEmpty { null } ?: EvalEnums.STAGE_PRE) }
    var start by rememberSaveable(key) {
        mutableStateOf(editing?.start ?: prefill?.date?.takeIf { EvalCore.isValidDate(it) } ?: today)
    }
    var endManual by rememberSaveable(key) { mutableStateOf(editing?.let { EvalCore.isEndDateManual(it, settings) } ?: false) }
    var end by rememberSaveable(key) {
        mutableStateOf(editing?.end ?: EvalCore.autoEndDate(start, level, stage, settings))
    }
    var status by rememberSaveable(key) { mutableStateOf(editing?.status ?: EvalEnums.STATUS_PENDING) }
    var settle by rememberSaveable(key) { mutableStateOf(editing?.settle ?: EvalEnums.SETTLE_OPEN) }
    var note by rememberSaveable(key) { mutableStateOf(editing?.note.orEmpty()) }
    var feeManualOn by rememberSaveable(key) { mutableStateOf(editing?.feeManual != null) }
    var feeText by rememberSaveable(key) { mutableStateOf(editing?.feeManual?.let(::plainNumber).orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var nameFocused by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val wasUnscheduled = editing != null && !EvalCore.isValidDate(editing.start)
    val autoEnd = EvalCore.autoEndDate(start, level, stage, settings)
    val ruleDays = EvalCore.ruleDays(level, stage, settings)

    // Keep the end date on the rule until the user changes it by hand.
    fun reapplyRule(newStart: String = start, newLevel: String = level, newStage: String = stage) {
        if (!endManual) {
            val auto = EvalCore.autoEndDate(newStart, newLevel, newStage, settings)
            if (auto.isNotEmpty()) end = auto
        }
        if (EvalCore.isValidDate(end) && EvalCore.isValidDate(newStart) && end < newStart) {
            end = EvalCore.autoEndDate(newStart, newLevel, newStage, settings).ifEmpty { newStart }
            endManual = false
        }
    }

    val nameIndex = remember(snapshot.projects) { EvalStats.projectNameIndex(snapshot.projects) }
    val stagesForName = nameIndex.find { it.first == name.trim() }?.second.orEmpty()
    val duplicateStage = name.isNotBlank() && snapshot.projects.any {
        it.name == name.trim() && it.stage == stage && it.id != editing?.id
    }
    val suggestions = if (nameFocused && editing == null) {
        val q = name.trim().lowercase()
        nameIndex.filter { (n, _) -> n != name.trim() && (q.isEmpty() || n.lowercase().contains(q)) }.take(8)
    } else emptyList()

    val manualFee = feeText.trim().toDoubleOrNull()
    val fee = EvalCore.effectiveFee(start, end, level, role, if (feeManualOn) manualFee else null, settings)

    fun submit() {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) { error = "请填写项目名称"; return }
        if (!EvalCore.isValidDate(start) && !wasUnscheduled) { error = "请选择开始时间"; return }
        if (EvalCore.isValidDate(end) && EvalCore.isValidDate(start) && end < start) { error = "结束时间早于开始时间"; return }
        var feeValue: Double? = null
        if (feeManualOn && feeText.isNotBlank()) {
            val n = feeText.trim().toDoubleOrNull()
            if (n == null || n < 0 || !n.isFinite()) { error = "评估费用格式无效"; return }
            feeValue = EvalCore.round2(n)
        }
        error = null
        val unscheduled = !EvalCore.isValidDate(start)
        viewModel.saveProject(
            editingId = editing?.id,
            input = ProjectInput(
                name = cleanName,
                level = level,
                role = role,
                stage = stage,
                start = if (unscheduled) "" else start,
                end = if (unscheduled) "" else end,
                status = status,
                settle = settle,
                note = note,
                feeManual = feeValue
            ),
            allowUnscheduled = unscheduled && wasUnscheduled,
            onDone = { onBack() }
        )
    }

    androidx.compose.material3.Scaffold(
        containerColor = AppColors.CanvasBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
                },
                title = {
                    Text(if (editing == null) "新建评估项目" else "编辑评估项目", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                actions = {
                    if (editing != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "更多") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("记一笔差旅") }, onClick = {
                                    menuOpen = false
                                    onAddTravel(editing.name, editing.stage)
                                })
                                DropdownMenuItem(text = { Text("删除项目", color = AppColors.Danger) }, onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                })
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = AppColors.Surface, shadowElevation = 6.dp) {
                Button(
                    onClick = { submit() },
                    enabled = !isMutating,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(48.dp)
                ) {
                    if (isMutating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.Surface)
                    else Text(if (editing == null) "保存并同步到日程" else "保存修改", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            error?.let { ErrorBanner(it) }

            FormCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text("项目名称 *") },
                    placeholder = { Text("输入名称，可选择已有项目") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { nameFocused = it.isFocused }
                )
                if (suggestions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppColors.BorderSubtle),
                        color = AppColors.Surface,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Column {
                            suggestions.forEachIndexed { i, (n, stages) ->
                                if (i > 0) HorizontalDivider(color = AppColors.BorderSubtle)
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            name = n
                                            snapshot.projects.firstOrNull { it.name == n }?.let { m ->
                                                level = m.level
                                                role = m.role
                                            }
                                            if (EvalEnums.STAGE_PRE in stages && EvalEnums.STAGE_FORMAL !in stages) {
                                                stage = EvalEnums.STAGE_FORMAL
                                            }
                                            reapplyRule(newLevel = level, newStage = stage)
                                            nameFocused = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(n, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("已登记：" + stages.joinToString("、"), fontSize = 11.sp, color = AppColors.TextMuted)
                                }
                            }
                        }
                    }
                }
                if (stagesForName.isNotEmpty()) {
                    Text("已登记：" + stagesForName.joinToString("、"), fontSize = 12.sp, color = AppColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
                }
            }

            FormCard {
                FieldLabel("评估阶段")
                Segmented(EvalEnums.STAGES, stage) {
                    stage = it
                    reapplyRule(newStage = it)
                }
                if (duplicateStage) {
                    Text("提示：该项目已有「$stage」记录", fontSize = 12.sp, color = EvalTones.OrangeText, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel("项目等级")
                Segmented(EvalEnums.LEVELS, level) {
                    level = it
                    reapplyRule(newLevel = it)
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel("角色")
                Segmented(EvalEnums.ROLES, role) { role = it }
            }

            FormCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField(
                        "开始时间 *",
                        if (EvalCore.isValidDate(start)) "${EvalCore.formatDateSlash(start)} ${DateUtils.weekdayLabel(LocalDate.parse(start))}" else "未排期",
                        Modifier.weight(1f)
                    ) { pickStart = true }
                    PickerField(
                        "结束时间",
                        if (EvalCore.isValidDate(end)) "${EvalCore.formatDateSlash(end)} ${DateUtils.weekdayLabel(LocalDate.parse(end))}" else "—",
                        Modifier.weight(1f)
                    ) { if (EvalCore.isValidDate(start)) pickEnd = true }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hint = when {
                        !EvalCore.isValidDate(start) -> "未排期项目不会生成工作日程"
                        ruleDays == null -> "该等级/阶段未设置天数规则，请自行选择结束时间"
                        endManual && autoEnd.isNotEmpty() && end != autoEnd -> "已手动调整（规则为 ${EvalCore.formatDateSlash(autoEnd)}）"
                        else -> "按规则自动推算（$ruleDays 天）"
                    }
                    Text(hint, fontSize = 12.sp, color = AppColors.TextMuted, modifier = Modifier.weight(1f))
                    if (endManual && autoEnd.isNotEmpty() && end != autoEnd) {
                        TextButton(onClick = {
                            endManual = false
                            end = autoEnd
                        }) { Text("恢复规则") }
                    }
                }
                Text(
                    "评估天数：" + (EvalCore.calcDays(start, end).takeIf { it > 0 }?.let { "$it 天" } ?: "—"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            FormCard {
                FieldLabel("项目状态")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EvalEnums.PROJECT_STATUSES.forEach { s ->
                        FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                FieldLabel("费用结算状态")
                Segmented(EvalEnums.SETTLE_STATUSES, settle) { settle = it }
            }

            FormCard {
                FieldLabel("评估费用")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (fee.fee == null) "待设置" else money(fee.fee),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (fee.fee == null) AppColors.TextMuted else AppColors.TextMain
                    )
                    if (fee.isManual) SmallBadge("手动")
                }
                val rate = EvalCore.unitRate(level, role, settings)
                val days = EvalCore.calcDays(start, end)
                Text(
                    text = when {
                        days == 0 -> "请补全起止时间以计算评估天数与费用"
                        rate == null && !feeManualOn -> "该等级/角色费率未设置，可打开下方开关手填（留空将显示「待设置」）"
                        rate == null -> "该等级/角色费率未设置"
                        else -> "按规则 $days 天 × ${EvalCore.formatMoney(rate)} 元/天 = ${money(fee.autoFee)}"
                    },
                    fontSize = 12.sp,
                    color = AppColors.TextMuted
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { feeManualOn = !feeManualOn },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("手动改写费用", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = feeManualOn, onCheckedChange = { feeManualOn = it })
                }
                if (feeManualOn) {
                    OutlinedTextField(
                        value = feeText,
                        onValueChange = { v -> feeText = v.filter { it.isDigit() || it == '.' }.take(12) },
                        label = { Text("评估费用（元）") },
                        placeholder = { Text(fee.autoFee?.let(::plainNumber) ?: "如 3000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            FormCard {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(1000) },
                    label = { Text("备注（选填）") },
                    placeholder = { Text("现场联系人、协议定价说明等") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                if (EvalCore.isValidDate(start)) "保存后自动同步到工作日程「$assessmentCategoryName」类别" else "未排期项目保存后不会出现在工作日程中",
                fontSize = 12.sp,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    if (pickStart) {
        EvalDatePickerDialog(
            initial = start.ifEmpty { today },
            onDismiss = { pickStart = false },
            onClear = if (wasUnscheduled) ({ start = ""; end = "" }) else null
        ) {
            start = it
            reapplyRule(newStart = it)
        }
    }
    if (pickEnd) {
        EvalDatePickerDialog(initial = end.ifEmpty { start }, onDismiss = { pickEnd = false }) { picked ->
            end = if (picked < start) start else picked
            endManual = end != autoEnd
        }
    }
    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除评估项目？") },
            text = { Text("将删除「${editing.name}（${editing.stage}）」及其关联的工作日程，历史差旅记录会保留。此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteProject(editing.id, onDone = onBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger)
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

// ---------------------------------------------------------------------------
// Form helpers shared by both editors
// ---------------------------------------------------------------------------

@Composable
fun FormCard(content: @Composable () -> Unit) {
    EvalCard {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, opt ->
            SegmentedButton(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {}
            ) { Text(opt, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

@Composable
fun ErrorBanner(message: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = AppColors.DangerSoft, modifier = Modifier.fillMaxWidth()) {
        Text(message, color = AppColors.Danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingRecordScreen(title: String, loading: Boolean, message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") } },
            title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(color = AppColors.Primary)
            else EmptyState("记录不存在", message, action = { TextButton(onClick = onBack) { Text("返回") } })
        }
    }
}
