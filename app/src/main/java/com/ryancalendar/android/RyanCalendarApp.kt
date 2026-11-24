package com.ryancalendar.android

import android.app.Application
import android.content.Context
import com.ryancalendar.android.data.local.CalendarDatabase
import com.ryancalendar.android.data.local.SettingsStore
import com.ryancalendar.android.data.remote.CalendarApiService
import com.ryancalendar.android.data.remote.EvalApiService
import com.ryancalendar.android.data.remote.NetworkClientFactory
import com.ryancalendar.android.data.remote.PersistentCookieJar
import com.ryancalendar.android.data.repository.CalendarRepository
import com.ryancalendar.android.data.repository.EvalRepository
import com.ryancalendar.android.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.CookieJar

class AppContainer(context: Context) {
    val json = NetworkClientFactory.createJson()
    val settingsStore = SettingsStore(context, json)
    val cookieJar = PersistentCookieJar(context, json)
    val database = CalendarDatabase.create(context)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val retrofit = NetworkClientFactory.createRetrofit(cookieJar, json)
    val apiService: CalendarApiService = retrofit.create(CalendarApiService::class.java)
    val evalApiService: EvalApiService = retrofit.create(EvalApiService::class.java)
    val sessionlessApiService = NetworkClientFactory.createApiService(CookieJar.NO_COOKIES, json)
    val repository = CalendarRepository(
        api = apiService,
        dao = database.calendarDao(),
        settingsStore = settingsStore,
        cookieJar = cookieJar,
        json = json,
        sessionlessApi = sessionlessApiService,
        appScope = appScope
    )
    val evalRepository = EvalRepository(
        api = evalApiService,
        dao = database.evalDao(),
        settingsStore = settingsStore,
        calendarRepository = repository
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
