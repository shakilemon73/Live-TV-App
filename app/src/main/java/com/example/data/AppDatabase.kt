package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CategoryEntity::class, ChannelEntity::class, RecordingEntity::class, M3uMetaEntity::class, CachedLiveEventEntity::class, InterestedEventEntity::class, EpgProgramEntity::class], version = 13, exportSchema = false)
@TypeConverters(PlaybackSourceTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun liveTvDao(): LiveTvDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add playbackSources column
                db.execSQL("ALTER TABLE channels ADD COLUMN playbackSources TEXT NOT NULL DEFAULT '[]'")

                // Populate playbackSources from existing streamUrl column
                db.execSQL("""
                    UPDATE channels 
                    SET playbackSources = '[{"url":"' || REPLACE(REPLACE(streamUrl, '\', '\\'), '"', '\"') || '","name":"Source 1","isBroken":' || (CASE WHEN isBroken = 1 THEN 'true' ELSE 'false' END) || '}]'
                    WHERE streamUrl IS NOT NULL AND streamUrl != ''
                """)
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_category` ON `channels` (`category`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN playlistUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "livetv_database"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
