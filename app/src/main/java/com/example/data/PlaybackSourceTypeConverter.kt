package com.example.data

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class PlaybackSourceTypeConverter {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, PlaybackSource::class.java)
    private val adapter = moshi.adapter<List<PlaybackSource>>(listType)

    @TypeConverter
    fun fromString(value: String?): List<PlaybackSource>? {
        if (value == null) return null
        return try {
            adapter.fromJson(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun toString(list: List<PlaybackSource>?): String? {
        if (list == null) return null
        return adapter.toJson(list)
    }
}
