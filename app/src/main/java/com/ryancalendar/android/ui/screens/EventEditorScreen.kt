package com.ryancalendar.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.eval.EvalEnums
import com.ryancalendar.android.domain.util.ColorUtils
import com.ryancalendar.android.domain.util.DateUtils
import com.ryancalendar.android.ui.AppViewModel
import com.ryancalendar.android.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditorScreen(
    viewModel: AppViewModel,
    eventId: String?,
    initialDate: LocalDate,
    onBack: () -> Unit,
    onSwitchToProject: (LocalDate) -> Unit = {}
) {
    val categories by viewModel.categories.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()

    val editingEvent = remember(eventId) {
        if (eventId.isNullOrBlank()) null else allEvents.find { it.id == eventId }
    }
    val isEditing = editingEvent != null

    var title by remember(eventId) { mutableStateOf(editingEvent?.title.orEmpty()) }
    var categoryId by remember(eventId) {
        mutableStateOf(
            editingEvent?.categoryId?.takeIf { id -> categories.any { it.id == id } }
                ?: categories.firstOrNull()?.id.orEmpty()
        )
    }
    var startDate by remember(eventId, initialDate) {
        mutableStateOf(
            DateUtils.parseOrNull(editingEvent?.startDate) ?: initialDate
        )
    }
    var isMultiDay by remember(eventId) {
        mutableStateOf(editingEvent?.isMultiDay == true)
    }
    var endDate by remember(eventId, initialDate) {
        mutableStateOf(
            DateUtils.parseOrNull(editingEvent?.endDate)?.takeIf { it.isAfter(startDate) }
                ?: startDate.plusDays(1)
        )
    }
    var allDay by remember(eventId) { mutableStateOf(editingEvent?.allDay != false) }
    var startTime by remember(eventId) {
        mutableStateOf(editingEvent?.startTime?.ifBlank { "09:00" } ?: "09:00")
    }
    var endTime by remember(eventId) {
        mutableStateOf(editingEvent?.endTime?.ifBlank { "18:00" } ?: "18:00")
    }
    var notes by remember(eventId) { mutableStateOf(editingEvent?.notes.orEmpty()) }
    var formError by remember { mutableStateOf<String?>(null) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val assessmentPicked = !isEditing && categoryId == EvalEnums.ASSESSMENT_CATEGORY_ID

    fun submit() {
        if (assessmentPicked) {
            onSwitchToProject(startDate)
            return
        }
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            formError = "请输入日程标题"
            return
        }
        val finalEndStr = if (isMultiDay) {
            if (endDate.isBefore(startDate)) {
                formError = "结束日期不能早于开始日期"
                return
            }
            if (endDate == startDate) "" else endDate.toString()
        } else {
            ""
        }
        formError = null
        viewModel.saveEvent(
            editingEventId = editingEvent?.id,
            title = cleanTitle,
            categoryId = categoryId,
            startDate = startDate.toString(),
            endDate = finalEndStr,
            allDay = allDay,
            startTime = if (allDay) "" else startTime,
            endTime = if (allDay) "" else endTime,
            notes = notes,
            onSuccess = onBack
        )
    }

    Scaffold(
        containerColor = AppColors.CanvasBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Text(
                        text = if (isEditing) "编辑日程" else "新建日程",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (isEditing) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = !isMutating
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除日程",
                                tint = AppColors.Danger
                            )
                        }
                    }
                    TextButton(
                        onClick = { submit() },
                        enabled = !isMutating
                    ) {
                        if (isMutating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("保存", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (assessmentPicked) {
                val assessName = categories.find { it.id == EvalEnums.ASSESSMENT_CATEGORY_ID }?.name.orEmpty()
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.PrimarySoft,
                    border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "「$assessName」日程需通过评估项目录入",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "填写评估项目后会自动生成这条日程，并计入评估统计。",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onSwitchToProject(startDate) }, shape = RoundedCornerShape(10.dp)) {
                            Text("填写评估项目")
                        }
                    }
                }
            }

            // 若来源于 Web 端台账导入，展示只读来源徽标
            if ((editingEvent?.source == "ledger" || editingEvent?.source == "eval") && editingEvent.ledgerMeta != null) {
                val meta = editingEvent.ledgerMeta
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.PrimarySoft,
                    border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "来源：评估项目台账",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Primary
                        )
                        val desc = listOfNotNull(
                            meta.stage.takeIf { it.isNotBlank() }?.let { "阶段：$it" },
                            meta.level.takeIf { it.isNotBlank() }?.let { "等级：$it" },
                            meta.role.takeIf { it.isNotBlank() }?.let { "角色：$it" }
                        ).joinToString(" · ")
                        if (desc.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(text = desc, fontSize = 12.sp, color = AppColors.TextSecondary)
                        }
                    }
                }
            }

            if (formError != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.DangerSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formError!!,
                        color = AppColors.Danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // 标题输入
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppColors.Surface,
                border = BorderStroke(1.dp, AppColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(60) },
                        label = { Text("日程标题 *") },
                        placeholder = { Text("例如：华东新能源现场评估") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 类别选择
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppColors.Surface,
                border = BorderStroke(1.dp, AppColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "日程类别",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val selected = cat.id == categoryId
                            val style = ColorUtils.getEventColorStyle(cat.color)
                            FilterChip(
                                selected = selected,
                                onClick = { categoryId = cat.id },
                                label = { Text(cat.name, fontSize = 13.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(style.primary)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = style.chipBg,
                                    selectedLabelColor = style.text
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = AppColors.BorderSubtle,
                                    selectedBorderColor = style.border
                                )
                            )
                        }
                    }
                }
            }

            // 日期与时间设置
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppColors.Surface,
                border = BorderStroke(1.dp, AppColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    PickerRow(
                        label = "开始日期",
                        value = "$startDate（${DateUtils.weekdayLabel(startDate)}）",
                        onClick = { showStartDatePicker = true }
                    )
                    HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))

                    SwitchRow(
                        label = "跨天日程",
                        checked = isMultiDay,
                        onCheckedChange = { checked ->
                            isMultiDay = checked
                            if (checked && !endDate.isAfter(startDate)) {
                                endDate = startDate.plusDays(1)
                            }
                        }
                    )

                    if (isMultiDay) {
                        HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                        PickerRow(
                            label = "结束日期",
                            value = "$endDate（${DateUtils.weekdayLabel(endDate)}）",
                            onClick = { showEndDatePicker = true }
                        )
                    }

                    HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                    SwitchRow(
                        label = "全天日程",
                        checked = allDay,
                        onCheckedChange = { allDay = it }
                    )

                    if (!allDay) {
                        HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                        PickerRow(
                            label = "开始时间",
                            value = startTime,
                            onClick = { showStartTimePicker = true }
                        )
                        HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                        PickerRow(
                            label = "结束时间",
                            value = endTime,
                            onClick = { showEndTimePicker = true }
                        )
                    }
                }
            }

            // 备注
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppColors.Surface,
                border = BorderStroke(1.dp, AppColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(500) },
                        label = { Text("备注（选填）") },
                        placeholder = { Text("地点、联系人、需要携带的资料等...") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = { submit() },
                enabled = !isMutating,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(if (isEditing) "保存修改" else "创建日程", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showStartDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            startDate = picked
                            if (isMultiDay && endDate.isBefore(picked)) {
                                endDate = picked.plusDays(1)
                            }
                        }
                        showStartDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEndDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            endDate = if (picked.isBefore(startDate)) startDate else picked
                        }
                        showEndDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showStartTimePicker) {
        TimePickerAlertDialog(
            title = "选择开始时间",
            initialHHMM = startTime,
            onConfirm = {
                startTime = it
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        TimePickerAlertDialog(
            title = "选择结束时间",
            initialHHMM = endTime,
            onConfirm = {
                endTime = it
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }

    if (showDeleteConfirm && editingEvent != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除日程？") },
            text = {
                Text(
                    if (editingEvent.isEvalLinked) {
                        "确认删除「${editingEvent.title}」吗？将同步删除评估项目列表中的对应记录（历史差旅保留），无法撤销。"
                    } else {
                        "确认删除「${editingEvent.title}」吗？删除后会同步从云端移除，无法撤销。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteEvent(editingEvent.id, onSuccess = onBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger)
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PickerRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = AppColors.TextMain)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Primary
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = AppColors.TextMain)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerAlertDialog(
    title: String,
    initialHHMM: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = initialHHMM.split(":")
    val initH = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
    val initM = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val state = rememberTimePickerState(initialHour = initH, initialMinute = initM, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
