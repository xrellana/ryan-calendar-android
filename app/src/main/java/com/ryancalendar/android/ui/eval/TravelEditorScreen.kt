package com.ryancalendar.android.ui.eval

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.data.repository.TravelInput
import com.ryancalendar.android.domain.eval.EvalCore
import com.ryancalendar.android.domain.eval.EvalEnums
import com.ryancalendar.android.domain.eval.EvalStats
import com.ryancalendar.android.domain.eval.EvalTravel
import com.ryancalendar.android.domain.util.DateUtils
import com.ryancalendar.android.ui.theme.AppColors
import java.time.LocalDate

@Composable
fun TravelEditorScreen(
    viewModel: EvalViewModel,
    travelId: String?,
    prefillName: String,
    prefillStage: String,
    onBack: () -> Unit
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val editing = remember(travelId, snapshot) { travelId?.let { id -> snapshot.travels.find { it.id == id } } }
    if (travelId != null && editing == null) {
        MissingRecordScreen("编辑差旅", loading = isRefreshing, message = "未找到该差旅记录，可能已在其他设备删除。", onBack = onBack)
        return
    }
    TravelEditorForm(viewModel, remember(travelId) { editing }, prefillName, prefillStage, onBack)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TravelEditorForm(
    viewModel: EvalViewModel,
    editing: EvalTravel?,
    prefillName: String,
    prefillStage: String,
    onBack: () -> Unit
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    val projects = snapshot.projects
    val key = editing?.id ?: "new"
    val today = LocalDate.now().toString()

    var date by rememberSaveable(key) { mutableStateOf(editing?.date ?: today) }
    var amountText by rememberSaveable(key) {
        mutableStateOf(editing?.amount?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }.orEmpty())
    }
    var cat by rememberSaveable(key) { mutableStateOf(editing?.cat ?: EvalEnums.CAT_DEFAULT) }
    var catCustom by rememberSaveable(key) { mutableStateOf(editing?.catCustom.orEmpty()) }
    var name by rememberSaveable(key) { mutableStateOf(editing?.name ?: prefillName) }
    var stage by rememberSaveable(key) {
        mutableStateOf(
            editing?.stage
                ?: prefillStage.ifEmpty { null }
                ?: (if (prefillName.isNotBlank()) EvalCore.findNearestStage(prefillName, today, projects) else EvalEnums.STAGE_PRE)
        )
    }
    // Once the user picks a stage by hand, stop auto-picking it from the date.
    var stageManual by rememberSaveable(key) { mutableStateOf(editing != null || prefillStage.isNotEmpty()) }
    var reimb by rememberSaveable(key) { mutableStateOf(editing?.reimb ?: EvalEnums.REIMB_NONE) }
    var detail by rememberSaveable(key) { mutableStateOf(editing?.detail.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var nameFocused by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (editing == null) {
            try {
                amountFocus.requestFocus()
            } catch (_: IllegalStateException) {
                // Focus target not attached yet; the user can tap the field.
            }
        }
    }

    val nameIndex = remember(projects) { EvalStats.projectNameIndex(projects) }
    val travelCounts = remember(snapshot.travels) { snapshot.travels.groupingBy { it.name }.eachCount() }
    val registeredStages = nameIndex.find { it.first == name.trim() }?.second.orEmpty()
    val nearby = remember(projects, today) { EvalStats.nearbyProjects(projects, today) }

    fun pickName(n: String, presetStage: String? = null) {
        name = n
        if (presetStage != null) {
            stage = presetStage
            stageManual = true
        } else {
            stageManual = false
            if (projects.any { it.name == n }) stage = EvalCore.findNearestStage(n, date, projects)
        }
        nameFocused = false
    }

    val suggestions = if (nameFocused) {
        val q = name.trim().lowercase()
        (nameIndex.map { it.first } + travelCounts.keys).distinct()
            .filter { it != name.trim() && (q.isEmpty() || it.lowercase().contains(q)) }
            .take(8)
    } else emptyList()

    // Level and plan come from the linked project record; fall back to the saved copy.
    val linked = EvalCore.findLinkedProject(name, stage, date, projects)
    val linkLevel: String
    val linkStart: String
    val linkEnd: String
    val linkText: String
    when {
        linked != null -> {
            linkLevel = linked.level
            linkStart = linked.start
            linkEnd = linked.end
            linkText = EvalCore.formatPlanText(linked.start, linked.end)
        }
        editing != null && name.trim() == editing.name && stage == editing.stage -> {
            linkLevel = editing.level
            linkStart = editing.planStart
            linkEnd = editing.planEnd
            linkText = editing.planText.ifEmpty { EvalCore.formatPlanText(editing.planStart, editing.planEnd) }
        }
        else -> {
            linkLevel = ""
            linkStart = ""
            linkEnd = ""
            linkText = ""
        }
    }

    fun submit(continueAfter: Boolean) {
        val amt = amountText.trim().toDoubleOrNull()
        when {
            !EvalCore.isValidDate(date) -> { error = "请选择差旅发生日期"; return }
            amt == null || amt <= 0 || !amt.isFinite() -> { error = "请填写大于 0 的差旅金额"; return }
            name.isBlank() -> { error = "请填写项目名称"; return }
            cat == EvalEnums.CAT_CUSTOM && catCustom.isBlank() -> { error = "选择「自定义」类别时必须填写类别名称"; return }
            detail.isBlank() -> { error = "请填写差旅具体内容"; return }
        }
        error = null
        viewModel.saveTravel(
            editingId = editing?.id,
            input = TravelInput(
                date = date,
                name = name.trim(),
                stage = stage,
                level = linkLevel,
                planStart = linkStart,
                planEnd = linkEnd,
                planText = linkText,
                cat = cat,
                catCustom = if (cat == EvalEnums.CAT_CUSTOM) catCustom.trim() else "",
                amount = EvalCore.round2(amt!!),
                detail = detail.trim(),
                reimb = reimb
            ),
            onDone = {
                if (continueAfter) {
                    // Same trip, next receipt: keep project, stage, date and category.
                    amountText = ""
                    detail = ""
                    try {
                        amountFocus.requestFocus()
                    } catch (_: IllegalStateException) {
                    }
                } else {
                    onBack()
                }
            }
        )
    }

    Scaffold(
        containerColor = AppColors.CanvasBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
                },
                title = { Text(if (editing == null) "记一笔差旅" else "编辑差旅", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    if (editing != null) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !isMutating) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除差旅", tint = AppColors.Danger)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = AppColors.Surface, shadowElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (editing == null) {
                        OutlinedButton(
                            onClick = { submit(continueAfter = true) },
                            enabled = !isMutating,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("保存并再记一笔", fontSize = 14.sp) }
                    }
                    Button(
                        onClick = { submit(continueAfter = false) },
                        enabled = !isMutating,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        if (isMutating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.Surface)
                        else Text("保存", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
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
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it.isDigit() || it == '.' }.take(12) },
                    label = { Text("金额（元）*") },
                    placeholder = { Text("如 326.50") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocus)
                )
                Spacer(Modifier.height(10.dp))
                PickerField(
                    "差旅日期 *",
                    if (EvalCore.isValidDate(date)) "${EvalCore.formatDateSlash(date)} ${DateUtils.weekdayLabel(LocalDate.parse(date))}" else "请选择",
                    Modifier.fillMaxWidth()
                ) { pickDate = true }
            }

            FormCard {
                FieldLabel("差旅类别")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EvalEnums.TRAVEL_CATS.forEach { c ->
                        FilterChip(
                            selected = cat == c,
                            onClick = { cat = c },
                            label = { Text(c) },
                            leadingIcon = { Icon(travelCatIcon(c), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        )
                    }
                }
                if (cat == EvalEnums.CAT_CUSTOM) {
                    OutlinedTextField(
                        value = catCustom,
                        onValueChange = { catCustom = it.take(40) },
                        label = { Text("自定义类别名称 *") },
                        placeholder = { Text("例如：机场停车费") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            }

            FormCard {
                if (nearby.isNotEmpty() && editing == null) {
                    FieldLabel("近期项目（点选即可）")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        nearby.forEach { p ->
                            val on = name.trim() == p.name && stage == p.stage
                            FilterChip(
                                selected = on,
                                onClick = { pickName(p.name, p.stage) },
                                label = {
                                    Text("${p.name} · ${p.stage}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(120)
                        stageManual = false
                    },
                    label = { Text("项目名称 *") },
                    placeholder = { Text("选择已登记项目或直接输入新名称") },
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
                            suggestions.forEachIndexed { i, n ->
                                if (i > 0) HorizontalDivider(color = AppColors.BorderSubtle)
                                val meta = listOfNotNull(
                                    nameIndex.find { it.first == n }?.second?.joinToString("/"),
                                    travelCounts[n]?.let { "$it 笔差旅" }
                                ).joinToString(" · ")
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { pickName(n) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(n, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (meta.isNotEmpty()) Text(meta, fontSize = 11.sp, color = AppColors.TextMuted)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FieldLabel("评估阶段")
                val stageLabels = EvalEnums.STAGES.map { st ->
                    if (name.isNotBlank() && registeredStages.isNotEmpty() && st !in registeredStages) "$st（未登记）" else st
                }
                Segmented(stageLabels, stageLabels[EvalEnums.STAGES.indexOf(stage).coerceAtLeast(0)]) { label ->
                    stage = EvalEnums.STAGES[stageLabels.indexOf(label)]
                    stageManual = true
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadonlyBox("项目等级（自动带出）", linkLevel.ifEmpty { "—" }, Modifier.weight(1f))
                    ReadonlyBox("评估计划（自动带出）", linkText.ifEmpty { "—" }, Modifier.weight(1.4f))
                }
            }

            FormCard {
                FieldLabel("报销状态")
                Segmented(EvalEnums.REIMB_STATUSES, reimb) { reimb = it }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it.take(500) },
                    label = { Text("差旅具体内容 *") },
                    placeholder = { Text("例如：高铁往返二等座、现场住宿两晚") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (editing == null) {
                Text(
                    "同一次出差有多张票据时，用「保存并再记一笔」连续录入",
                    fontSize = 12.sp,
                    color = AppColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    if (pickDate) {
        EvalDatePickerDialog(initial = date, onDismiss = { pickDate = false }) { picked ->
            date = picked
            if (!stageManual && name.isNotBlank() && projects.any { it.name == name.trim() }) {
                stage = EvalCore.findNearestStage(name, picked, projects)
            }
        }
    }
    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除差旅记录？") },
            text = { Text("确认删除 ${EvalCore.formatDateSlash(editing.date)} ${EvalCore.travelDisplayCat(editing)} ${money(editing.amount)} 吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteTravel(editing.id, onDone = onBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger)
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun ReadonlyBox(label: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(label, fontSize = 11.sp, color = AppColors.TextMuted)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}
