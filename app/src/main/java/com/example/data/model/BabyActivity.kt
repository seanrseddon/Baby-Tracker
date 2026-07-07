package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "baby_activities")
data class BabyActivity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String, // "FEEDING", "SLEEP", "NAPPY"
    val babyName: String = "Baby",
    val timestamp: Long = System.currentTimeMillis(),
    val detailsJson: String, // Holds JSON representation of activity-specific details
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
