package com.ryancalendar.android.data.remote

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean
) {
    fun toOkHttpCookie(): Cookie {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
        if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        return builder.build()
    }

    companion object {
        fun from(c: Cookie) = StoredCookie(
            name = c.name,
            value = c.value,
            expiresAt = c.expiresAt,
            domain = c.domain,
            path = c.path,
            secure = c.secure,
            httpOnly = c.httpOnly,
            hostOnly = c.hostOnly
        )
    }
}

/**
 * 持久化 CookieJar：自动保存后端的 workcal_sid 会话 Cookie 并支持 30 天滑动续期，
 * 无需对已部署的服务端做任何修改即可直接无缝登录同步。
 */
class PersistentCookieJar(
    context: Context,
    private val json: Json
) : CookieJar {

    private val prefs = context.getSharedPreferences("ryan_calendar_cookies", Context.MODE_PRIVATE)
    private val lock = Any()
    private val memoryCache = mutableMapOf<String, StoredCookie>()

    init {
        loadFromDisk()
    }

    private fun cookieKey(domain: String, path: String, name: String): String =
        "$domain|$path|$name"

    private fun loadFromDisk() {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return
        try {
            val list = json.decodeFromString<List<StoredCookie>>(raw)
            val now = System.currentTimeMillis()
            list.filter { it.expiresAt > now }.forEach { sc ->
                memoryCache[cookieKey(sc.domain, sc.path, sc.name)] = sc
            }
        } catch (_: Exception) {
            // ignore corrupted cookie cache
        }
    }

    private fun saveToDiskLocked() {
        val now = System.currentTimeMillis()
        val valid = memoryCache.values.filter { it.expiresAt > now }
        prefs.edit().putString(KEY_COOKIES, json.encodeToString(valid)).apply()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            for (c in cookies) {
                val key = cookieKey(c.domain, c.path, c.name)
                if (c.expiresAt <= now || c.value.isEmpty()) {
                    memoryCache.remove(key)
                } else {
                    memoryCache[key] = StoredCookie.from(c)
                }
            }
            saveToDiskLocked()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val expiredKeys = mutableListOf<String>()
            val matched = mutableListOf<Cookie>()
            for ((k, sc) in memoryCache) {
                if (sc.expiresAt <= now) {
                    expiredKeys.add(k)
                    continue
                }
                val cookie = sc.toOkHttpCookie()
                if (cookie.matches(url)) {
                    matched.add(cookie)
                }
            }
            if (expiredKeys.isNotEmpty()) {
                expiredKeys.forEach { memoryCache.remove(it) }
                saveToDiskLocked()
            }
            return matched
        }
    }

    fun clearAll() {
        synchronized(lock) {
            memoryCache.clear()
            prefs.edit().remove(KEY_COOKIES).apply()
        }
    }

    companion object {
        private const val KEY_COOKIES = "cookies_v1"
    }
}

/**
 * 统一附加后端 CSRF 校验头（X-Requested-With: WorkCal）与禁止缓存头。
 */
class CsrfHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .header("X-Requested-With", "WorkCal")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .build()
        return chain.proceed(req)
    }
}

object NetworkClientFactory {
    const val SERVER_BASE_URL = "https://calendar.meowoo.net"
    private const val SERVER_BASE_URL_WITH_SLASH = "$SERVER_BASE_URL/"

    fun createJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun createApiService(
        cookieJar: PersistentCookieJar,
        json: Json
    ): CalendarApiService {
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(CsrfHeaderInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(SERVER_BASE_URL_WITH_SLASH)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(CalendarApiService::class.java)
    }

    fun parseErrorMessage(throwable: Throwable, json: Json): Pair<Int?, String> {
        if (throwable is HttpException) {
            val code = throwable.code()
            val body = try {
                throwable.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            val parsedMsg = if (!body.isNullOrBlank()) {
                try {
                    json.decodeFromString<ErrorResponse>(body).error
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            return code to (parsedMsg ?: "请求失败（HTTP $code）")
        }
        return null to (throwable.message ?: "网络连接失败，请检查网络状态后重试")
    }
}
