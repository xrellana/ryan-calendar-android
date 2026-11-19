package com.ryancalendar.android.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryancalendar.android.notification.NotificationHelper
import com.ryancalendar.android.ui.AppViewModel
import com.ryancalendar.android.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AppViewModel,
    onOpenCategories: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val user by viewModel.currentUser.collectAsState()
    val serverUrl = viewModel.serverUrl
    val lastSyncMs by viewModel.lastSyncMs.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val reminder by viewModel.reminderSettings.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showResetIcsDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.updateReminderSettings(true, reminder.hour, reminder.minute)
        } else {
            viewModel.emitToast("未授予通知权限，无法开启每日定时提醒")
        }
    }

    val lastSyncLabel = remember(lastSyncMs) {
        if (lastSyncMs <= 0L) "尚未同步"
        else Instant.ofEpochMilli(lastSyncMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    }

    val icsUrl = remember(serverUrl, user?.icsToken) {
        val token = user?.icsToken.orEmpty()
        if (serverUrl.isNotBlank() && token.isNotBlank()) "$serverUrl/ics/$token.ics" else ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.CanvasBg)
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface),
            title = { Text("我的", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        )
        HorizontalDivider(color = AppColors.BorderSubtle)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 账号与云端同步卡片
            item(key = "account_card") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.Surface,
                    border = BorderStroke(1.dp, AppColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.PrimarySoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (user?.shownName?.firstOrNull()?.uppercaseChar() ?: 'U').toString(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.Primary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user?.shownName ?: "未登录",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.TextMain
                                )
                                Text(
                                    text = "账号 ID：${user?.username.orEmpty()} · 共 ${allEvents.size} 条日程",
                                    fontSize = 12.sp,
                                    color = AppColors.TextMuted
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.manualSync() },
                                enabled = !isSyncing,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isSyncing) "同步中" else "立即同步", fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = AppColors.BorderSubtle)
                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "云端服务器：$serverUrl",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "最近同步：$lastSyncLabel（切回前台时自动静默同步）",
                            fontSize = 12.sp,
                            color = AppColors.TextMuted
                        )
                    }
                }
            }

            // 本地每日定时通知设置
            item(key = "reminder_card") {
                SectionCard(title = "本地定时通知") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = AppColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("每日日程提醒", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "当天有日程时准时推送今日安排",
                                    fontSize = 12.sp,
                                    color = AppColors.TextMuted
                                )
                            }
                        }
                        Switch(
                            checked = reminder.enabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !NotificationHelper.hasNotificationPermission(context)
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.updateReminderSettings(true, reminder.hour, reminder.minute)
                                    }
                                } else {
                                    viewModel.updateReminderSettings(false, reminder.hour, reminder.minute)
                                }
                            }
                        )
                    }
                    HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Schedule,
                        title = "提醒时间",
                        subtitle = "每天 ${reminder.formattedTime}（重启手机自动恢复）",
                        onClick = { showReminderTimePicker = true }
                    )
                    HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.sendTestNotification() }) {
                            Text("发送一条测试通知", fontSize = 13.sp)
                        }
                    }
                }
            }

            // 日程工具
            item(key = "tools_card") {
                SectionCard(title = "日程设置") {
                    SettingsRow(
                        icon = Icons.Outlined.Category,
                        title = "类别管理与优先级",
                        subtitle = "当前 ${categories.size} 个类别 · 控制配色与同日排序",
                        onClick = onOpenCategories
                    )
                }
            }

            // 个人 ICS 日历订阅
            item(key = "ics_card") {
                SectionCard(title = "个人日历订阅 (.ics)") {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "可将下方私有订阅链接添加到系统日历或 Outlook，实现只读同步：",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.SurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = icsUrl.ifBlank { "暂无订阅链接" },
                                fontSize = 12.sp,
                                color = AppColors.TextMain,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (icsUrl.isNotBlank()) {
                                        clipboard.setText(AnnotatedString(icsUrl))
                                        viewModel.emitToast("已复制 .ics 订阅链接")
                                    }
                                },
                                enabled = icsUrl.isNotBlank()
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("复制链接", fontSize = 12.sp)
                            }
                            TextButton(onClick = { showResetIcsDialog = true }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("重置链接", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 账号安全
            item(key = "security_card") {
                SectionCard(title = "账号与安全") {
                    SettingsRow(
                        icon = Icons.Outlined.Edit,
                        title = "修改显示名称",
                        subtitle = user?.shownName.orEmpty(),
                        onClick = { showEditNameDialog = true }
                    )
                    HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = "修改登录密码",
                        subtitle = "修改后其他设备会话将自动下线",
                        onClick = { showChangePasswordDialog = true }
                    )
                    HorizontalDivider(color = AppColors.BorderSubtle, modifier = Modifier.padding(horizontal = 14.dp))
                    SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        title = "退出登录",
                        subtitle = "清除本机缓存并返回登录页",
                        tint = AppColors.Danger,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }

            item {
                Text(
                    text = "注：管理员后台与 Excel 台账批量导入请在 Web 端使用。",
                    fontSize = 12.sp,
                    color = AppColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (showReminderTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = reminder.hour,
            initialMinute = reminder.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showReminderTimePicker = false },
            title = { Text("设置每日提醒时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateReminderSettings(
                            enabled = reminder.enabled,
                            hour = timeState.hour,
                            minute = timeState.minute
                        )
                        showReminderTimePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showReminderTimePicker = false }) { Text("取消") }
            }
        )
    }

    if (showEditNameDialog) {
        var nameInput by remember { mutableStateOf(user?.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("修改显示名称") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(32) },
                    label = { Text("显示名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.updateDisplayName(nameInput) { showEditNameDialog = false }
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("取消") }
            }
        )
    }

    if (showChangePasswordDialog) {
        var curPwd by remember { mutableStateOf("") }
        var newPwd by remember { mutableStateOf("") }
        var confirmPwd by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("修改登录密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = curPwd,
                        onValueChange = { curPwd = it },
                        label = { Text("当前密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPwd,
                        onValueChange = { newPwd = it },
                        label = { Text("新密码（至少 6 位）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPwd,
                        onValueChange = { confirmPwd = it },
                        label = { Text("确认新密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (err != null) {
                        Text(err!!, color = AppColors.Danger, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            curPwd.isEmpty() || newPwd.isEmpty() -> err = "请填写完整密码"
                            newPwd.length < 6 -> err = "新密码长度至少 6 位"
                            newPwd != confirmPwd -> err = "两次输入的新密码不一致"
                            else -> {
                                err = null
                                viewModel.changePassword(curPwd, newPwd) {
                                    showChangePasswordDialog = false
                                }
                            }
                        }
                    }
                ) { Text("确认修改") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("取消") }
            }
        )
    }

    if (showResetIcsDialog) {
        AlertDialog(
            onDismissRequest = { showResetIcsDialog = false },
            title = { Text("确认重置订阅链接？") },
            text = { Text("重置后，旧的 .ics 订阅链接将立即失效，已在外部日历订阅的设备需要重新添加新链接。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetIcsDialog = false
                        viewModel.resetIcsToken()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger)
                ) { Text("确认重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetIcsDialog = false }) { Text("取消") }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录？") },
            text = { Text("将清除本机登录态与本地缓存日程，云端数据不会受到影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Danger)
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppColors.Surface,
            border = BorderStroke(1.dp, AppColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = AppColors.TextSecondary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (tint == AppColors.Danger) AppColors.Danger else AppColors.TextMain
            )
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, fontSize = 12.sp, color = AppColors.TextMuted)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.TextMuted
        )
    }
}
