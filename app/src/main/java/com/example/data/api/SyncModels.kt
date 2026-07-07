package com.example.data.api

import com.example.data.model.BabyActivity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncRequest(
    val lastSyncTime: Long,
    val clientActivities: List<BabyActivityDto>
)

@JsonClass(generateAdapter = true)
data class SyncResponse(
    val serverSyncTime: Long,
    val updates: List<BabyActivityDto>
)

@JsonClass(generateAdapter = true)
data class BabyActivityDto(
    val id: String,
    val type: String,
    val babyName: String,
    val timestamp: Long,
    val detailsJson: String,
    val notes: String,
    val updatedAt: Long,
    val isDeleted: Boolean
)

// Helper extension functions to map between Domain entity and API DTO
fun BabyActivity.toDto() = BabyActivityDto(
    id = id,
    type = type,
    babyName = babyName,
    timestamp = timestamp,
    detailsJson = detailsJson,
    notes = notes,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun BabyActivityDto.toEntity() = BabyActivity(
    id = id,
    type = type,
    babyName = babyName,
    timestamp = timestamp,
    detailsJson = detailsJson,
    notes = notes,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)
