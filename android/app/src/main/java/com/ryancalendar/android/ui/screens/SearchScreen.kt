package com.ryancalendar.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.model.CalendarEvent
import com.ryancalendar.android.domain.util.ColorUtils
import com.ryancalendar.android.ui.AppViewModel
import com.ryancalendar.android.ui.components.EventListCard
import com.ryancalendar.android.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSelectEvent: (CalendarEvent) -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var selectedCatIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val results = remember(query, selectedCatIds, allEvents, categories) {
        if (query.isBlank() && selectedCatIds.isEmpty()) emptyList()
        else viewModel.searchEvents(query, selectedCatIds)
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
                title = { Text("搜索日程", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Surface(color = AppColors.Surface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索标题、备注、项目名、日期或类别...") },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "清空")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            val selected = cat.id in selectedCatIds
                            val style = ColorUtils.getEventColorStyle(cat.color)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCatIds = if (selected) selectedCatIds - cat.id
                                    else selectedCatIds + cat.id
                                },
                                label = { Text(cat.name, fontSize = 12.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(style.primary)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = style.chipBg,
                                    selectedLabelColor = style.text
                                )
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = AppColors.BorderSubtle)

            if (query.isBlank() && selectedCatIds.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "输入关键词或选择类别即可实时搜索",
                        fontSize = 14.sp,
                        color = AppColors.TextMuted
                    )
                }
            } else if (results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到匹配的日程",
                        fontSize = 14.sp,
                        color = AppColors.TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "count") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "找到 ${results.size} 条日程（点击直达详情）",
                                fontSize = 12.sp,
                                color = AppColors.TextMuted
                            )
                        }
                    }
                    items(results, key = { it.id }) { ev ->
                        val cat = categoryMap[ev.categoryId] ?: CalendarCategory.Fallback
                        EventListCard(
                            event = ev,
                            category = cat,
                            showDateRange = true,
                            onClick = { onSelectEvent(ev) }
                        )
                    }
                }
            }
        }
    }
}
