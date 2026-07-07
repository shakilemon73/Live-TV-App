package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "cached_live_events")
data class CachedLiveEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sportCategory: String,
    val logoUrl: String,
    val feedsJson: String,
    val cachedAt: Long = System.currentTimeMillis()
) {
    companion object {
        private val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        private val feedsType = Types.newParameterizedType(List::class.java, EventFeed::class.java)
        private val adapter = moshi.adapter<List<EventFeed>>(feedsType)

        fun fromGroupedEvent(event: GroupedEvent): CachedLiveEventEntity {
            val json = try {
                adapter.toJson(event.feeds)
            } catch (e: Exception) {
                "[]"
            }
            return CachedLiveEventEntity(
                id = event.id,
                title = event.title,
                sportCategory = event.sportCategory,
                logoUrl = event.logoUrl,
                feedsJson = json
            )
        }
    }

    fun toGroupedEvent(): GroupedEvent {
        val feedsList = try {
            adapter.fromJson(feedsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return GroupedEvent(
            id = id,
            title = title,
            sportCategory = sportCategory,
            logoUrl = logoUrl,
            feeds = feedsList
        )
    }
}
