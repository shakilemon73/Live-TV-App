package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlaybackSource(
    val url: String,
    val name: String,
    val isBroken: Boolean = false
)
