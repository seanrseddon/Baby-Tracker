package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BabyActivity
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyActivityDao {

    @Query("SELECT * FROM baby_activities WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllActiveActivities(): Flow<List<BabyActivity>>

    @Query("SELECT * FROM baby_activities ORDER BY timestamp DESC")
    fun getAllRawActivities(): Flow<List<BabyActivity>>

    @Query("SELECT * FROM baby_activities WHERE id = :id LIMIT 1")
    suspend fun getActivityById(id: String): BabyActivity?

    @Query("SELECT * FROM baby_activities WHERE updatedAt > :since")
    suspend fun getActivitiesUpdatedSince(since: Long): List<BabyActivity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: BabyActivity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<BabyActivity>)

    @Query("UPDATE baby_activities SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteActivity(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM baby_activities")
    suspend fun deleteAllActivities()
}
