package com.ryancalendar.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.model.CalendarEvent
import com.ryancalendar.android.domain.util.ColorUtils
import com.ryancalendar.android.domain.util.DateUtils
import com.ryancalendar.android.ui.theme.AppColors

@Composable
fun EventListCard(
    event: CalendarEvent,
    category: CalendarCategory,
    showDateRange: Boolean = false,
    relativeTag: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = ColorUtils.getEventColorStyle(category.color)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(style.primary)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = event.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!relativeTag.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        val isToday = relativeTag == "今天" || relativeTag == "进行中"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isToday) AppColors.PrimarySoft else AppColors.SurfaceVariant
                        ) {
                            Text(
                                text = relativeTag,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isToday) AppColors.Primary else AppColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = style.chipBg
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(style.primary)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = category.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = style.text
                            )
                        }
                    }

                    val timeDesc = buildString {
                        if (showDateRange || event.isMultiDay) {
                            append(DateUtils.formatRangeSummary(event.startDate, event.endDate))
                        } else if (event.allDay) {
                            append("全天")
                        } else {
                            append("${event.startTime} - ${event.endTime}")
                        }
                        if (!event.allDay && (showDateRange || event.isMultiDay)) {
                            append(" · ${event.startTime}-${event.endTime}")
                        }
                    }
                    Text(
                        text = timeDesc,
                        fontSize = 12.sp,
                        color = AppColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (event.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = event.notes.replace('\n', ' '),
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryFilterBottomSheet(
    categories: List<CalendarCategory>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "按类别筛选日程",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextMain
                )
                if (selectedIds.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("清除筛选", color = AppColors.Primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (selectedIds.isEmpty()) "当前展示全部类别，点击下方标签可多选过滤"
                else "已选 ${selectedIds.size} 个类别（日历、近期视图同步生效）",
                fontSize = 12.sp,
                color = AppColors.TextMuted
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val selected = cat.id in selectedIds
                    val style = ColorUtils.getEventColorStyle(cat.color)
                    FilterChip(
                        selected = selected,
                        onClick = { onToggle(cat.id) },
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
}
