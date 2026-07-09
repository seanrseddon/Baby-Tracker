package com.example.data.repository

import com.example.data.api.SyncClient
import com.example.data.api.SyncRequest
import com.example.data.api.toDto
import com.example.data.api.toEntity
import com.example.data.local.BabyActivityDao
import com.example.data.model.BabyActivity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class SyncResult(
    val serverSyncTime: Long,
    val dbInstanceId: String
)

class BabyRepository(private val dao: BabyActivityDao) {

    val allActiveActivities: Flow<List<BabyActivity>> = dao.getAllActiveActivities()

    suspend fun insertActivity(activity: BabyActivity) {
        dao.insertActivity(activity)
    }

    suspend fun insertActivities(activities: List<BabyActivity>) {
        dao.insertActivities(activities)
    }

    suspend fun softDeleteActivity(id: String) {
        dao.softDeleteActivity(id, System.currentTimeMillis())
    }

    suspend fun deleteAllLocalActivities() {
        dao.deleteAllActivities()
    }

    suspend fun eraseAllServerActivities(serverUrl: String) {
        if (serverUrl.isBlank()) return
        val apiService = SyncClient.getApiService(serverUrl)
        val response = apiService.eraseAllServerActivities()
        if (!response.isSuccessful) {
            throw Exception(response.errorBody()?.string() ?: "Failed to delete server activities")
        }
    }

    /**
     * Performs a bidirectional sync with the self-hosted Unraid/Docker backend.
     * Returns a SyncResult with the new server sync time and actual database ID.
     */
    suspend fun syncWithServer(serverUrl: String, lastSyncTime: Long, expectedDbId: String): SyncResult {
        if (serverUrl.isBlank()) throw IllegalArgumentException("Server URL is not set")

        var actualLastSync = lastSyncTime
        // 1. Get all activities that have been updated since our last sync
        var clientUpdates = dao.getActivitiesUpdatedSince(actualLastSync)
        var clientDtos = clientUpdates.map { it.toDto() }

        // 2. Perform the sync network request
        val apiService = SyncClient.getApiService(serverUrl)
        var request = SyncRequest(
            lastSyncTime = actualLastSync,
            clientActivities = clientDtos
        )

        var response = apiService.syncActivities(request)
        val returnedDbId = response.dbInstanceId ?: ""

        val isDbIdMismatch = expectedDbId.isNotEmpty() && returnedDbId.isNotEmpty() && returnedDbId != expectedDbId
        val isFirstTimeDbIdTracking = expectedDbId.isEmpty() && returnedDbId.isNotEmpty() && lastSyncTime > 0L

        if (isDbIdMismatch || isFirstTimeDbIdTracking) {
            // Force a full sync because the server database has been wiped, replaced, or we are tracking a new server.
            actualLastSync = 0L
            clientUpdates = dao.getActivitiesUpdatedSince(actualLastSync)
            clientDtos = clientUpdates.map { it.toDto() }
            request = SyncRequest(
                lastSyncTime = actualLastSync,
                clientActivities = clientDtos
            )
            response = apiService.syncActivities(request)
        }

        // 3. Save updates from the server to local database
        if (response.updates.isNotEmpty()) {
            val serverEntities = response.updates.map { it.toEntity() }
            dao.insertActivities(serverEntities)
        }

        // 4. Return result
        return SyncResult(
            serverSyncTime = response.serverSyncTime,
            dbInstanceId = returnedDbId
        )
    }
}
