package com.ryancalendar.android.data.remote

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Evaluation module endpoints that the Android client is allowed to use (docs/API.md §1.0).
 * Write bodies are JsonObject because PATCH semantics depend on which keys are present.
 */
interface EvalApiService {

    @GET("api/eval/state")
    suspend fun getState(@Query("today") today: String): EvalStateResponse

    @POST("api/eval/projects")
    suspend fun createProject(@Body body: JsonObject): EvalProjectMutationResponse

    @PATCH("api/eval/projects/{id}")
    suspend fun updateProject(@Path("id") id: String, @Body body: JsonObject): EvalProjectMutationResponse

    @POST("api/eval/projects/batch")
    suspend fun batchProjects(@Body body: JsonObject): EvalProjectBatchResponse

    @DELETE("api/eval/projects/{id}")
    suspend fun deleteProject(@Path("id") id: String, @Query("today") today: String): EvalProjectDeleteResponse

    @POST("api/eval/travels")
    suspend fun createTravel(@Body body: JsonObject): EvalTravelMutationResponse

    @PATCH("api/eval/travels/{id}")
    suspend fun updateTravel(@Path("id") id: String, @Body body: JsonObject): EvalTravelMutationResponse

    @POST("api/eval/travels/batch")
    suspend fun batchTravels(@Body body: JsonObject): EvalTravelMutationResponse

    @DELETE("api/eval/travels/{id}")
    suspend fun deleteTravel(@Path("id") id: String, @Query("today") today: String): EvalTravelMutationResponse

    @PUT("api/eval/preferences")
    suspend fun updatePreferences(@Body body: EvalPreferencesRequest): EvalPreferencesResponse
}
