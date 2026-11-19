package com.ryancalendar.android

import android.app.Application
import android.content.Context
import com.ryancalendar.android.data.local.CalendarDatabase
import com.ryancalendar.android.data.local.SettingsStore
import com.ryancalendar.android.data.remote.NetworkClientFactory
import com.ryancalendar.android.data.remote.PersistentCookieJar
import com.ryancalendar.android.data.repository.CalendarRepository
import com.ryancalendar.android.notification.NotificationHelper

class AppContainer(context: Context) {
    val json = NetworkClientFactory.createJson()
    val settingsStore = SettingsStore(context, json)
    val cookieJar = PersistentCookieJar(context, json)
    val database = CalendarDatabase.create(context)
    val apiService = NetworkClientFactory.createApiService(settingsStore, cookieJar, json)
    val repository = CalendarRepository(
        api = apiService,
        dao = database.calendarDao(),
        settingsStore = settingsStore,
        cookieJar = cookieJar,
        json = json
    )
}

class RyanCalendarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)
    }
}
