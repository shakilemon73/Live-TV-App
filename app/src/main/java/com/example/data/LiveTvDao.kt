package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveTvDao {
    // --- Categories ---
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Int): CategoryEntity?

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    // --- Channels ---
    @Query("SELECT * FROM channels ORDER BY name ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isBroken = 0 ORDER BY name ASC")
    fun getActiveChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getChannelsByCategory(categoryId: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT category FROM channels WHERE isBroken = 0 AND category != '' ORDER BY category ASC")
    fun getDistinctActiveCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun deleteAllChannels()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Delete
    suspend fun deleteChannel(channel: ChannelEntity)

    @Delete
    suspend fun deleteChannels(channels: List<ChannelEntity>)

    @Update
    suspend fun updateChannel(channel: ChannelEntity)

    @Update
    suspend fun updateChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun updateFavoriteStatus(channelId: Int, isFavorite: Boolean)

    @Query("UPDATE channels SET isBroken = :isBroken, lastChecked = :lastChecked WHERE id = :channelId")
    suspend fun updateChannelBrokenStatus(channelId: Int, isBroken: Boolean, lastChecked: Long)

    @Query("UPDATE channels SET isBroken = :isBroken, lastChecked = :lastChecked WHERE id IN (:channelIds)")
    suspend fun updateChannelsBrokenStatusQuery(channelIds: List<Int>, isBroken: Boolean, lastChecked: Long)

    @Query("UPDATE channels SET channelHealth = :health WHERE id IN (:channelIds)")
    suspend fun updateChannelsHealthQuery(channelIds: List<Int>, health: String)

    @Transaction
    suspend fun updateChannelsBrokenStatuses(brokenIds: List<Int>, workingIds: List<Int>, lastChecked: Long) {
        if (brokenIds.isNotEmpty()) {
            updateChannelsBrokenStatusQuery(brokenIds, true, lastChecked)
            updateChannelsHealthQuery(brokenIds, "Offline")
        }
        if (workingIds.isNotEmpty()) {
            updateChannelsBrokenStatusQuery(workingIds, false, lastChecked)
            updateChannelsHealthQuery(workingIds, "Excellent")
        }
    }

    @Transaction
    suspend fun clearAndInsertUnifiedChannels(
        categories: List<CategoryEntity>,
        channels: List<ChannelEntity>
    ) {
        deleteAllChannels()
        deleteAllCategories()

        // Insert categories and capture their auto-generated IDs
        val categoryIdMap = mutableMapOf<String, Int>()
        for (cat in categories) {
            val newId = insertCategory(cat)
            categoryIdMap[cat.name] = newId.toInt()
        }

        // Map channels to their new local category IDs
        val mappedChannels = channels.map { ch ->
            val resolvedCatId = categoryIdMap[ch.category] ?: ch.categoryId
            ch.copy(categoryId = resolvedCatId)
        }

        if (mappedChannels.isNotEmpty()) {
            insertChannels(mappedChannels)
        }
    }

    // --- M3U Meta ---
    @Query("SELECT * FROM m3u_meta WHERE url = :url")
    suspend fun getM3uMeta(url: String): M3uMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertM3uMeta(meta: M3uMetaEntity)

    // --- Recordings ---
    @Query("SELECT * FROM recordings ORDER BY recordedAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)
}
