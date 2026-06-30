package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val channelId: Int,
    val channelName: String,
    val channelLogoUrl: String,
    val filePath: String,
    val fileName: String,
    val recordedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0,
    val durationMs: Long = 0
)
