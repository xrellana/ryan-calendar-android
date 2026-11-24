package com.ryancalendar.android.ui.eval

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.eval.EvalCore
import com.ryancalendar.android.domain.eval.EvalEnums
import com.ryancalendar.android.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------------------------------------------------------------------
// Palette for status values (matches the web badge colors)
// ---------------------------------------------------------------------------

data class ChipTone(val bg: Color, val fg: Color)

object EvalTones {
    val Gray = ChipTone(Color(0xFFF1F5F9), Color(0xFF475569))
    val Blue = ChipTone(Color(0xFFDBEAFE), Color(0xFF1D4ED8))
    val Purple = ChipTone(Color(0xFFEDE9FE), Color(0xFF6D28D9))
    val Green = ChipTone(Color(0xFFDCFCE7), Color(0xFF15803D))
    val Orange = ChipTone(Color(0xFFFFEDD5), Color(0xFFC2410C))

    val GreenText = Color(0xFF15803D)
    val OrangeText = Color(0xFFC2410C)
    val BlueText = Color(0xFF1D4ED8)

    fun forStatus(s: String): ChipTone = when (s) {
        EvalEnums.STATUS_IN_PROGRESS -> Blue
        EvalEnums.STATUS_SUBMITTED -> Purple
        EvalEnums.STATUS_DONE -> Green
        else -> Gray
    }

    fun forSettle(s: String): ChipTone = if (s == EvalEnums.SETTLE_DONE) Green else Orange

    fun forReimb(s: String): ChipTone = when (s) {
        EvalEnums.REIMB_SUBMITTED -> Blue
        EvalEnums.REIMB_DONE -> Green
        else -> Orange
    }
}

fun money(n: Double?): String = "¥" + EvalCore.formatMoney(n)

fun pctText(part: Double, total: Double): String =
    if (total > 0) "%.1f%%".format(part / total * 100) else "0.0%"

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

@Composable
fun TonePill(text: String, tone: ChipTone, modifier: Modifier = Modifier, trailingArrow: Boolean = false) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tone.bg)
            .padding(start = 10.dp, end = if (trailingArrow) 4.dp else 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tone.fg, maxLines = 1)
        if (trailingArrow) {
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = tone.fg, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * A status pill that opens a dropdown in place. Touch target is padded to 48dp height so
 * it is easy to hit on a card without opening the card itself.
 */
@Composable
fun StatusMenuChip(
    value: String,
    options: List<String>,
    tone: (String) -> ChipTone,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(50))
                .clickable(enabled = enabled) { open = true }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            TonePill(value, tone(value), trailingArrow = enabled)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { TonePill(opt, tone(opt)) },
                    trailingIcon = {
                        if (opt == value) Icon(Icons.Outlined.Check, contentDescription = null, tint = AppColors.Primary)
                    },
                    onClick = {
                        open = false
                        if (opt != value) onSelect(opt)
                    }
                )
            }
        }
    }
}

@Composable
fun SmallBadge(text: String, tone: ChipTone = EvalTones.Orange) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = tone.fg,
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tone.bg)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
fun EvalCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) AppColors.PrimarySoft else AppColors.Surface,
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, if (highlighted) AppColors.Primary else AppColors.BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextMain)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = AppColors.TextMuted)
            }
        }
        trailing?.invoke()
    }
}

data class KpiItem(
    val label: String,
    val value: String,
    val sub: String = "",
    val color: Color = AppColors.TextMain,
    val onClick: (() -> Unit)? = null,
    val active: Boolean = false
)

@Composable
fun KpiGrid(items: List<KpiItem>, columns: Int = 2, highlighted: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            ) {
                rowItems.forEach { KpiCard(it, Modifier.weight(1f).fillMaxHeight(), highlighted) }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun KpiCard(item: KpiItem, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val border = when {
        item.active -> BorderStroke(1.5.dp, AppColors.Primary)
        highlighted -> BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.4f))
        else -> BorderStroke(1.dp, AppColors.BorderSubtle)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (item.active) AppColors.PrimarySoft else AppColors.Surface,
        border = border,
        modifier = modifier.then(if (item.onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable { item.onClick.invoke() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(item.label, fontSize = 12.sp, color = AppColors.TextMuted, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                item.value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = item.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.sub.isNotBlank()) {
                Text(item.sub, fontSize = 11.sp, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ScopePill(text: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AppColors.PrimarySoft)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 12.sp, color = AppColors.Primary, fontWeight = FontWeight.Medium)
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "清除选择", tint = AppColors.Primary, modifier = Modifier.size(16.dp))
        }
    }
}

/** Search box + filter button (with active-count badge) + optional trailing actions. */
@Composable
fun SearchFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    filterCount: Int,
    onOpenFilter: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.CanvasBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder, fontSize = 14.sp, maxLines = 1) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清空搜索", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = AppColors.Surface,
                focusedContainerColor = AppColors.Surface,
                unfocusedBorderColor = AppColors.BorderSubtle
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
        )
        IconButton(onClick = onOpenFilter) {
            BadgedBox(badge = { if (filterCount > 0) Badge { Text("$filterCount") } }) {
                Icon(Icons.Outlined.FilterList, contentDescription = "筛选")
            }
        }
        trailing()
    }
}

data class ActiveFilter(val label: String, val onRemove: () -> Unit)

@Composable
fun ActiveFilterRow(filters: List<ActiveFilter>, onClearAll: () -> Unit) {
    if (filters.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { f ->
            InputChip(
                selected = true,
                onClick = f.onRemove,
                label = { Text(f.label, fontSize = 12.sp, maxLines = 1) },
                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(InputChipDefaults.IconSize)) }
            )
        }
        TextButton(onClick = onClearAll) { Text("清空", fontSize = 13.sp) }
    }
}

fun selectionLabel(name: String, values: Set<String>): String =
    if (values.size == 1) "$name：${values.first()}" else "$name：${values.first()} 等 ${values.size} 项"

/** Multi-select chip group for filter sheets; options are few, so they are laid out flat. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiChipGroup(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                TextButton(onClick = { onChange(emptySet()) }) { Text("不限", fontSize = 12.sp) }
            }
        }
        if (options.isEmpty()) {
            Text("暂无可选项", fontSize = 12.sp, color = AppColors.TextMuted)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { opt ->
                val on = opt in selected
                FilterChip(
                    selected = on,
                    onClick = { onChange(if (on) selected - opt else selected + opt) },
                    label = { Text(opt, fontSize = 13.sp) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null
                )
            }
        }
    }
}

/** Horizontally scrollable single-choice chips (presets, group-by dimensions). */
@Composable
fun <T> ChoiceChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(label(opt), fontSize = 13.sp) }
            )
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextMain)
        Text(message, fontSize = 13.sp, color = AppColors.TextMuted, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun ProgressLine(pct: Int, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val done = pct >= 100
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(AppColors.SurfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth((pct.coerceIn(0, 100)) / 100f)
                .clip(RoundedCornerShape(50))
                .background(if (done) EvalTones.GreenText else AppColors.Primary)
        )
    }
}

@Composable
fun SheetHeader(title: String, onReset: (() -> Unit)?, onDone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onReset != null) TextButton(onClick = onReset) { Text("重置") }
        TextButton(onClick = onDone) { Text("完成", fontWeight = FontWeight.SemiBold) }
    }
}

/** Two-line label/value row used inside filter sheets and editors. */
@Composable
fun PickerField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 11.sp, color = AppColors.TextMuted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.TextMain)
    }
}

// ---------------------------------------------------------------------------
// Date pickers (Material pickers work in UTC millis)
// ---------------------------------------------------------------------------

private fun dateToMillis(date: String): Long? =
    EvalCore.parseDate(date)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

private fun millisToDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvalDatePickerDialog(
    initial: String,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
    onConfirm: (String) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = dateToMillis(initial) ?: dateToMillis(LocalDate.now().toString())
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(millisToDate(it)) }
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            Row {
                if (onClear != null) {
                    TextButton(onClick = {
                        onClear()
                        onDismiss()
                    }) { Text("清空") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvalDateRangePickerDialog(
    initialStart: String,
    initialEnd: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = dateToMillis(initialStart),
        initialSelectedEndDateMillis = dateToMillis(initialEnd)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis?.let(::millisToDate)
                    if (s != null) {
                        val e = state.selectedEndDateMillis?.let(::millisToDate) ?: s
                        onConfirm(s, e)
                    }
                    onDismiss()
                }
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        DateRangePicker(
            state = state,
            title = { Text("选择日期区间", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom batch-action bar shown in selection mode
// ---------------------------------------------------------------------------

@Composable
fun BatchActionBar(info: String, content: @Composable RowScope.() -> Unit) {
    Surface(
        color = AppColors.Surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(info, fontSize = 12.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** Batch button that expands into a dropdown of target values. */
@Composable
fun BatchMenuButton(
    label: String,
    options: List<String>,
    tone: (String) -> ChipTone,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        androidx.compose.material3.OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text(label, fontSize = 14.sp)
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { TonePill(opt, tone(opt)) },
                    onClick = {
                        open = false
                        onSelect(opt)
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bar charts built from plain layouts (no chart library)
// ---------------------------------------------------------------------------

data class BarSegment(val value: Double, val color: Color)

data class BarColumn(val label: String, val segments: List<BarSegment>, val topLabel: String, val dimmed: Boolean = false)

/**
 * Vertical (optionally stacked) bar chart. Each column is a touch target; the selected
 * column is highlighted and reported back so the caller can show its details.
 */
@Composable
fun VerticalBarChart(
    columns: List<BarColumn>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    chartHeight: Dp = 150.dp
) {
    val maxTotal = columns.maxOfOrNull { c -> c.segments.sumOf { it.value } }?.takeIf { it > 0 } ?: 1.0
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        columns.forEachIndexed { idx, col ->
            val total = col.segments.sumOf { it.value }
            val selected = idx == selectedIndex
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) AppColors.PrimarySoft else Color.Transparent)
                    .clickable { onSelect(idx) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    col.topLabel,
                    fontSize = 9.sp,
                    color = if (selected) AppColors.Primary else AppColors.TextMuted,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Box(modifier = Modifier.height(chartHeight).width(22.dp), contentAlignment = Alignment.BottomCenter) {
                    val frac = (total / maxTotal).toFloat().coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (total > 0) frac.coerceAtLeast(0.02f) else 0f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    ) {
                        // Segments are listed bottom-up; draw top segment first.
                        col.segments.asReversed().forEach { seg ->
                            if (seg.value > 0 && total > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight((seg.value / total).toFloat())
                                        .background(if (col.dimmed) seg.color.copy(alpha = 0.45f) else seg.color)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    col.label,
                    fontSize = 10.sp,
                    color = if (selected) AppColors.Primary else AppColors.TextSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun LegendRow(items: List<Pair<String, Color>>) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 11.sp, color = AppColors.TextSecondary)
            }
        }
    }
}

/** "2026-11" -> "26/11" so twelve-plus months fit comfortably. */
fun shortMonthLabel(ym: String): String =
    if (ym.length == 7) ym.substring(2, 4) + "/" + ym.substring(5, 7) else ym

/** A labeled horizontal share bar used for group tables and category structure. */
@Composable
fun ShareBarRow(
    title: String,
    value: String,
    detail: String,
    fraction: Float,
    pct: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextMain, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextMain)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.SurfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.Primary)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(pct, fontSize = 11.sp, color = AppColors.TextMuted, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
        }
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(detail, fontSize = 11.sp, color = AppColors.TextMuted)
        }
    }
}
