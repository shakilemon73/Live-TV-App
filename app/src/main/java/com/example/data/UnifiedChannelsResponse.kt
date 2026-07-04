package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UnifiedCategory(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class UnifiedStream(
    @Json(name = "url") val url: String,
    @Json(name = "name") val name: String,
    @Json(name = "isBroken") val isBroken: Boolean = false
)

@JsonClass(generateAdapter = true)
data class UnifiedChannel(
    @Json(name = "name") val name: String,
    @Json(name = "logoUrl") val logoUrl: String?,
    @Json(name = "category") val category: String,
    @Json(name = "categoryId") val categoryId: Int,
    @Json(name = "description") val description: String?,
    @Json(name = "tvgId") val tvgId: String?,
    @Json(name = "tvgName") val tvgName: String?,
    @Json(name = "streams") val streams: List<UnifiedStream>
)

@JsonClass(generateAdapter = true)
data class UnifiedChannelsResponse(
    @Json(name = "updatedAt") val updatedAt: Long,
    @Json(name = "categories") val categories: List<UnifiedCategory>,
    @Json(name = "channels") val channels: List<UnifiedChannel>
)
