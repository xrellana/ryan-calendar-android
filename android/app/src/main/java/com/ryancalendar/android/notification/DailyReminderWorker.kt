package com.ryancalendar.android.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ryancalendar.android.MainActivity
import com.ryancalendar.android.R
import com.ryancalendar.android.RyanCalendarApp
import com.ryancalendar.android.domain.calendar.WeekLayoutEngine
import com.ryancalendar.android.domain.model.CalendarCategory
import com.ryancalendar.android.domain.model.CalendarEvent
import java.time.LocalDate

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? RyanCalendarApp ?: return Result.success()
        val repo = app.container.repository
        repo.init()

        val settings = repo.getReminderSettings()
        if (!settings.enabled) {
            return Result.success()
        }

        // 先尝试从云端静默拉取最新日程，若无网则回退使用本地 Room 缓存
        val cachedUser = repo.getCachedUser()
        if (cachedUser != null) {
            try {
                repo.syncFromServer(includeHolidays = false)
            } catch (_: Exception) {
                // ignore offline error
            }
            val categories = repo.getCategoriesOnce()
            val events = repo.getEventsOnce()
            val todayStr = LocalDate.now().toString()
            val priorityMap = categories.mapIndexed { idx, c -> c.id to idx }.toMap()
            val catMap = categories.associateBy { it.id }

            val todayEvents = events
                .filter { it.startDate <= todayStr && it.effectiveEndDate >= todayStr }
                .sortedWith { a, b -> WeekLayoutEngine.compareEvents(a, b, priorityMap) }

            if (todayEvents.isNotEmpty()) {
                NotificationHelper.showTodayAgendaNotification(
                    context = applicationContext,
                    todayEvents = todayEvents,
                    categoryMap = catMap
                )
            }
        }

        // 自动预约明天的同时间点提醒（避免 PeriodicWorkRequest 时间漂移）
        ReminderScheduler.scheduleNext(applicationContext, settings)
        return Result.success()
    }
}

object NotificationHelper {
    const val CHANNEL_ID = "daily_agenda_reminder"
    private const val NOTIFICATION_ID_DAILY = 1001
    private const val NOTIFICATION_ID_TEST = 1002

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "每日工作日程提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "每天定时推送当日的工作日程与跨天项目安排"
        }
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showTodayAgendaNotification(
        context: Context,
        todayEvents: List<CalendarEvent>,
        categoryMap: Map<String, CalendarCategory>,
        isTest: Boolean = false
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (todayEvents.isEmpty()) {
            if (isTest) "工作日程提醒测试" else "今日暂无工作日程"
        } else {
            val prefix = if (isTest) "[测试] " else ""
            "${prefix}今日共有 ${todayEvents.size} 项工作日程"
        }

        val lines = todayEvents.map { ev ->
            val catName = categoryMap[ev.categoryId]?.name ?: "未归类"
            val timePart = if (ev.allDay) {
                if (ev.isMultiDay) "跨天" else "全天"
            } else {
                ev.startTime.ifBlank { "全天" }
            }
            "[$catName] ${ev.title} · $timePart"
        }

        val summary = if (lines.isEmpty()) {
            "每日定时提醒已生效，有日程时将在设定时间准时推送。"
        } else {
            lines.first()
        }

        val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(title)
        if (lines.isEmpty()) {
            inboxStyle.addLine(summary)
        } else {
            lines.take(6).forEach { inboxStyle.addLine(it) }
            if (lines.size > 6) {
                inboxStyle.setSummaryText("还有 ${lines.size - 6} 项日程，点击查看全部")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_calendar)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(inboxStyle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context)
            .notify(if (isTest) NOTIFICATION_ID_TEST else NOTIFICATION_ID_DAILY, notification)
        return true
    }
}
