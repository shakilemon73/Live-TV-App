package com.example.data

data class StreamSource(
    val url: String,
    val subName: String,
    val isBroken: Boolean = false
)

data class GroupedChannel(
    val name: String,
    val logoUrl: String,
    val categoryId: Int,
    val description: String = "",
    val isFavorite: Boolean = false,
    val isBroken: Boolean = false,
    val streams: List<StreamSource>,
    val originalChannelIds: List<Int>
)
