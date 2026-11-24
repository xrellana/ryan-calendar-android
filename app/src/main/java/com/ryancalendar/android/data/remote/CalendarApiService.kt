package com.ryancalendar.android.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface CalendarApiService {

    // 认证与账号
    @GET("api/auth/me")
    suspend fun getMe(): MeResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    /** 显式携带会话 Cookie，供不挂 CookieJar 的独立客户端在本地登录态清除后注销服务端会话。 */
    @POST("api/auth/logout")
    suspend fun logout(
        @Header("Cookie") cookie: String,
        @Body empty: Map<String, String> = emptyMap()
    ): OkResponse

    @PUT("api/auth/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): LoginResponse

    @PUT("api/auth/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): OkResponse

    @POST("api/auth/ics-token/reset")
    suspend fun resetIcsToken(@Body empty: Map<String, String> = emptyMap()): ResetIcsResponse

    // 状态与节假日
    @GET("api/state")
    suspend fun getState(): StateResponse

    @GET("api/holidays")
    suspend fun getHolidays(): HolidaysResponse

    // 类别管理
    @POST("api/categories")
    suspend fun addCategory(@Body request: CategoryCreateRequest): CategoryMutationResponse

    @PATCH("api/categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: String,
        @Body request: CategoryPatchRequest
    ): CategoryMutationResponse

    @DELETE("api/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: String): CategoriesListResponse

    @PUT("api/categories/reorder")
    suspend fun reorderCategories(@Body request: ReorderCategoriesRequest): CategoriesListResponse

    // 日程 CRUD
    @POST("api/events")
    suspend fun addEvent(@Body request: EventUpsertRequest): EventResponse

    @PATCH("api/events/{id}")
    suspend fun updateEvent(
        @Path("id") id: String,
        @Body request: EventUpsertRequest
    ): EventResponse

    @DELETE("api/events/{id}")
    suspend fun deleteEvent(@Path("id") id: String): DeleteEventResponse
}
