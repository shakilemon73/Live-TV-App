package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["channelId"]),
        Index(value = ["startTime"]),
        Index(value = ["endTime"])
    ]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val channelId: String, // Matches tvgId or channel name
    val title: String,
    val description: String = "",
    val startTime: Long,   // Epoch timestamp in milliseconds
    val endTime: Long,     // Epoch timestamp in milliseconds
    val category: String = ""
)
