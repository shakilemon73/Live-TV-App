package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "channels",
    indices = [Index(value = ["category"])]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val streamUrl: String,
    val logoUrl: String,
    val categoryId: Int,
    val category: String = "",
    val description: String = "",
    val isFavorite: Boolean = false,
    val isBroken: Boolean = false,
    val tvgId: String = "",
    val tvgName: String = "",
    val lastChecked: Long = 0L,
    val playbackSources: List<PlaybackSource> = emptyList(),
    val playlistUrl: String = "",
    val channelHealth: String = "Unknown"
)
