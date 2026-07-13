package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "m3u_meta")
data class M3uMetaEntity(
    @PrimaryKey val url: String,
    val eTag: String?,
    val lastModified: String?,
    /**
     * The Cloudflare Worker's [lastChangeAt] timestamp from the last successful sync.
     * Stored locally so on the next launch we can append ?since=<lastChangeAt> to
     * the worker URL — the Worker returns { changed: false } if nothing has changed,
     * eliminating the full JSON download and local DB import entirely.
     */
    val lastChangeAt: Long = 0L
)
