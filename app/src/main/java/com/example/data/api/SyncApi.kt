package com.example.data.api

import retrofit2.http.Body
import retrofit2.http.POST

interface SyncApi {

    @POST("api/activities/sync")
    suspend fun syncActivities(@Body request: SyncRequest): SyncResponse

    @retrofit2.http.GET("api/sleep-timer")
    suspend fun getSleepTimer(): SleepTimerStatus

    @retrofit2.http.POST("api/sleep-timer")
    suspend fun startSleepTimer(@retrofit2.http.Body status: SleepTimerStatus): retrofit2.Response<Unit>

    @retrofit2.http.DELETE("api/sleep-timer")
    suspend fun deleteSleepTimer(): retrofit2.Response<Unit>

    @retrofit2.http.DELETE("api/activities")
    suspend fun eraseAllServerActivities(): retrofit2.Response<Unit>
}
