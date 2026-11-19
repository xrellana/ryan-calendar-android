package com.ryancalendar.android.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ryancalendar.android.RyanCalendarApp
import com.ryancalendar.android.domain.model.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val UNIQUE_WORK_NAME = "ryan_calendar_daily_reminder"

    fun applySettings(context: Context, settings: ReminderSettings) {
        if (settings.enabled) {
            scheduleNext(context, settings)
        } else {
            cancel(context)
        }
    }

    fun scheduleNext(context: Context, settings: ReminderSettings) {
        val now = ZonedDateTime.now()
        var nextTrigger = now
            .withHour(settings.hour.coerceIn(0, 23))
            .withMinute(settings.minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)

        // 若今天的目标时间已过（或距今不足 1 分钟），则排到明天
        if (!nextTrigger.isAfter(now.plusSeconds(30))) {
            nextTrigger = nextTrigger.plusDays(1)
        }

        val delayMillis = Duration.between(now, nextTrigger).toMillis().coerceAtLeast(60_000L)

        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? RyanCalendarApp
                    val settings = app?.container?.repository?.getReminderSettings()
                    if (settings != null && settings.enabled) {
                        ReminderScheduler.scheduleNext(context.applicationContext, settings)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
