package com.example.data.api

import retrofit2.http.Body
import retrofit2.http.POST

interface SyncApi {

    @POST("api/activities/sync")
    suspend fun syncActivities(@Body request: SyncRequest): SyncResponse
}
