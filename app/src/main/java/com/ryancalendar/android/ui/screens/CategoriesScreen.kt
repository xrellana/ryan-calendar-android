package com.ryancalendar.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.util.ColorUtils
import com.ryancalendar.android.ui.AppViewModel
import com.ryancalendar.android.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()

    var editingCategory by remember { mutableStateOf<CalendarCategory?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<CalendarCategory?>(null) }

    val countByCat = remember(allEvents) {
        allEvents.groupingBy { it.categoryId }.eachCount()
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
                title = { Text("类别管理", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = AppColors.Primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新增类别")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "hint") {
                Text(
                    text = "排序越靠前，同日多条日程的展示与取色优先级越高（与 Web 端同步）。",
                    fontSize = 12.sp,
                    color = AppColors.TextMuted,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            itemsIndexed(categories, key = { _, cat -> cat.id }) { index, cat ->
                val style = ColorUtils.getEventColorStyle(cat.color)
                val count = countByCat[cat.id] ?: 0
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Surface,
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(style.primary)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cat.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextMain
                            )
                            Text(
                                text = "优先级 #${index + 1} · 关联 $count 条日程",
                                fontSize = 12.sp,
                                color = AppColors.TextMuted
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveCategory(cat.id, moveUp = true) },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { viewModel.moveCategory(cat.id, moveUp = false) },
                            enabled = index < categories.lastIndex
                        ) {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { editingCategory = cat }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { deletingCategory = cat }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = AppColors.Danger,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CategoryEditBottomSheet(
            title = "新增类别",
            initialName = "",
            initialColor = ColorUtils.PresetColors.first(),
            onSave = { name, color ->
                viewModel.addCategory(name, color) { showCreateSheet = false }
            },
            onDismiss = { showCreateSheet = false }
        )
    }

    editingCategory?.let { target ->
        CategoryEditBottomSheet(
            title = "编辑类别",
            initialName = target.name,
            initialColor = target.color,
            onSave = { name, color ->
                viewModel.updateCategory(target.id, name, color) { editingCategory = null }
            },
            onDismiss = { editingCategory = null }
        )
    }

    deletingCategory?.let { target ->
        val linked = countByCat[target.id] ?: 0
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("删除类别「${target.name}」？") },
            text = {
                Text(
                    if (linked > 0) {
                        "该类别当前关联了 $linked 条日程。删除后这些日程不会被删除，将转为“未归类”样式显示。"
                    } else {
                        "确认删除该类别吗？"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(target.id)
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger)
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditBottomSheet(
    title: String,
    initialName: String,
    initialColor: String,
    onSave: (name: String, color: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(ColorUtils.normalizeHex(initialColor)) }
    val previewStyle = ColorUtils.getEventColorStyle(color)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = { if (name.isNotBlank()) onSave(name.trim(), color) },
                    enabled = name.isNotBlank()
                ) { Text("保存", fontWeight = FontWeight.SemiBold) }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = { Text("类别名称（最多 20 字）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 预览效果
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = previewStyle.softBg,
                border = BorderStroke(1.dp, previewStyle.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "预览：${name.ifBlank { "示例日程标题" }}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = previewStyle.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Text("选择颜色", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ColorUtils.PresetColors.forEach { hex ->
                    val selected = hex.equals(color, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ColorUtils.parseColor(hex))
                            .then(
                                if (selected) Modifier.border(2.5.dp, AppColors.TextMain, CircleShape)
                                else Modifier
                            )
                            .clickable { color = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "已选",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
