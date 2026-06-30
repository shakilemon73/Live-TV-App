package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "m3u_meta")
data class M3uMetaEntity(
    @PrimaryKey val url: String,
    val eTag: String?,
    val lastModified: String?
)
