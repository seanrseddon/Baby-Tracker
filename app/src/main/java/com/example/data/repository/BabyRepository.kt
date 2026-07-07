package com.example.data.repository

import com.example.data.api.SyncClient
import com.example.data.api.SyncRequest
import com.example.data.api.toDto
import com.example.data.api.toEntity
import com.example.data.local.BabyActivityDao
import com.example.data.model.BabyActivity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BabyRepository(private val dao: BabyActivityDao) {

    val allActiveActivities: Flow<List<BabyActivity>> = dao.getAllActiveActivities()

    suspend fun insertActivity(activity: BabyActivity) {
        dao.insertActivity(activity)
    }

    suspend fun softDeleteActivity(id: String) {
        dao.softDeleteActivity(id, System.currentTimeMillis())
    }

    /**
     * Performs a bidirectional sync with the self-hosted Unraid/Docker backend.
     * Returns the new server sync time if successful, or throws an exception.
     */
    suspend fun syncWithServer(serverUrl: String, lastSyncTime: Long): Long {
        if (serverUrl.isBlank()) throw IllegalArgumentException("Server URL is not set")

        // 1. Get all activities that have been updated since our last sync
        val clientUpdates = dao.getActivitiesUpdatedSince(lastSyncTime)
        val clientDtos = clientUpdates.map { it.toDto() }

        // 2. Perform the sync network request
        val apiService = SyncClient.getApiService(serverUrl)
        val request = SyncRequest(
            lastSyncTime = lastSyncTime,
            clientActivities = clientDtos
        )

        val response = apiService.syncActivities(request)

        // 3. Save updates from the server to local database
        if (response.updates.isNotEmpty()) {
            val serverEntities = response.updates.map { it.toEntity() }
            dao.insertActivities(serverEntities)
        }

        // 4. Return new sync time
        return response.serverSyncTime
    }
}
