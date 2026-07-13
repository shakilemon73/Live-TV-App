package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CategoryEntity::class, ChannelEntity::class, RecordingEntity::class, M3uMetaEntity::class, CachedLiveEventEntity::class, InterestedEventEntity::class, EpgProgramEntity::class], version = 15, exportSchema = false)
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

        /**
         * v13 → v14: Add lastChangeAt to m3u_meta so the ?since= fast-path works.
         * The Worker's lastChangeAt timestamp is persisted here after every successful sync;
         * on the next launch the app appends ?since=<lastChangeAt> to the worker URL and
         * the Worker returns { changed: false } if nothing has changed — skipping the full
         * JSON download and local DB import entirely.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE m3u_meta ADD COLUMN lastChangeAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v14 → v15: Add UNIQUE index on categories.name to fix duplicate categories.
         *
         * Previously CategoryEntity had no uniqueness constraint on `name`, so every sync
         * call to insertCategory() created a NEW row with a new auto-generated ID, resulting
         * in double (or triple) entries for each category shown in the UI.
         *
         * This migration:
         *   1. Creates a new categories_new table with the UNIQUE index
         *   2. Copies only one representative row per name (MIN id wins)
         *   3. Updates channels.categoryId to point to the surviving category ID
         *   4. Replaces the old table
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create the new table with the unique constraint on name
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        UNIQUE(name) ON CONFLICT REPLACE
                    )
                """.trimIndent())

                // 2. Insert only the lowest-id row for each name (deduplicate)
                db.execSQL("""
                    INSERT OR IGNORE INTO categories_new (id, name)
                    SELECT MIN(id), name FROM categories GROUP BY name
                """.trimIndent())

                // 3. Remap channels.categoryId to the surviving deduplicated category ID
                //    For each channel, find the MIN(id) for its category name and update
                db.execSQL("""
                    UPDATE channels SET categoryId = (
                        SELECT MIN(c.id) FROM categories c
                        INNER JOIN categories_new cn ON c.name = cn.name
                        WHERE c.id = channels.categoryId
                    ) WHERE categoryId IS NOT NULL
                """.trimIndent())

                // 4. Swap tables
                db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_new RENAME TO categories")

                // 5. Create index explicitly (SQLite may not carry it from the table DDL)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories (name)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            android.util.Log.i("AppDatabase", "getDatabase() requested. Checking instance...")
            return INSTANCE ?: synchronized(this) {
                android.util.Log.i("AppDatabase", "INSTANCE is null. Initializing database configuration...")
                val isTest = try {
                    android.os.Build.FINGERPRINT == "robolectric" ||
                    System.getProperty("robolectric.class") != null ||
                    System.getProperty("java.class.path")?.contains("junit") == true ||
                    System.getProperty("java.class.path")?.contains("robolectric") == true
                } catch (t: Throwable) {
                    false
                }
                android.util.Log.i("AppDatabase", "Environment check - Is JUnit/Robolectric Test: $isTest")

                if (!isTest) {
                    android.util.Log.i("AppDatabase", "SQLCipher initialization: Loading sqlcipher native library...")
                    try {
                        System.loadLibrary("sqlcipher")
                        android.util.Log.i("AppDatabase", "Successfully loaded sqlcipher library.")
                    } catch (t: Throwable) {
                        android.util.Log.e("AppDatabase", "Failed to load sqlcipher library", t)
                    }

                    try {
                        android.util.Log.i("AppDatabase", "SQLCipher initialization: Invoking loadLibs method...")
                        val sqliteDatabaseClass = Class.forName("net.sqlcipher.database.SQLiteDatabase")
                        val loadLibsMethod = sqliteDatabaseClass.getMethod("loadLibs", Context::class.java)
                        loadLibsMethod.invoke(null, context)
                        android.util.Log.i("AppDatabase", "SQLiteDatabase.loadLibs(context) executed successfully via reflection.")
                    } catch (t: Throwable) {
                        android.util.Log.i("AppDatabase", "SQLiteDatabase.loadLibs bypass or not present: ${t.message}")
                    }
                }

                val dbPassword = "SecureAppDatabaseSqlCipherKey_987654321"
                
                if (!isTest) {
                    val dbFile = context.getDatabasePath("livetv_database")
                    android.util.Log.i("AppDatabase", "Database file path: ${dbFile.absolutePath} | Exists: ${dbFile.exists()}")
                    if (dbFile.exists()) {
                        var deleteNeeded = false
                        // Check if it's an unencrypted plain SQLite database
                        try {
                            java.io.FileInputStream(dbFile).use { fis ->
                                val header = ByteArray(16)
                                val bytesRead = fis.read(header)
                                if (bytesRead == 16) {
                                    val sqliteHeader = "SQLite format 3\u0000".toByteArray(java.nio.charset.StandardCharsets.US_ASCII)
                                    deleteNeeded = header.contentEquals(sqliteHeader)
                                    android.util.Log.i("AppDatabase", "Database header check: Is raw plain SQLite: $deleteNeeded")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppDatabase", "Failed to read database header: ${e.message}")
                        }

                        if (!deleteNeeded) {
                            // If not unencrypted, try to open it with SQLCipher to verify key/integrity
                            android.util.Log.i("AppDatabase", "Verifying SQLCipher encryption key and integrity on the existing file...")
                            try {
                                val sqliteDatabaseClass = Class.forName("net.sqlcipher.database.SQLiteDatabase")
                                val openDatabaseMethod = sqliteDatabaseClass.getMethod(
                                    "openDatabase",
                                    String::class.java,
                                    String::class.java,
                                    Class.forName("net.sqlcipher.database.SQLiteDatabase\$CursorFactory"),
                                    Int::class.java
                                )
                                // Try opening read-only (1)
                                val dbObj = openDatabaseMethod.invoke(null, dbFile.absolutePath, dbPassword, null, 1)
                                val closeMethod = sqliteDatabaseClass.getMethod("close")
                                closeMethod.invoke(dbObj)
                                android.util.Log.i("AppDatabase", "Existing database verified successfully with SQLCipher encryption key.")
                            } catch (e: Exception) {
                                android.util.Log.e("AppDatabase", "Database encrypted open verification failed: ${e.message}. Deleting corrupted/locked database.")
                                deleteNeeded = true
                            }
                        }

                        if (deleteNeeded) {
                            android.util.Log.w("AppDatabase", "Database is incompatible or unencrypted. Deleting existing database files.")
                            try {
                                context.deleteDatabase("livetv_database")
                                android.util.Log.i("AppDatabase", "Successfully deleted incompatible database 'livetv_database'.")
                            } catch (t: Throwable) {
                                android.util.Log.e("AppDatabase", "Failed to delete incompatible database 'livetv_database'", t)
                            }
                        }
                    }
                }

                android.util.Log.i("AppDatabase", "Configuring Room database builder...")
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "livetv_database"
                )

                if (!isTest) {
                    var factoryApplied = false
                    
                    // Try net.sqlcipher.database.SupportFactory first (commonly used in older / standard SQLCipher)
                    try {
                        val factoryClass = Class.forName("net.sqlcipher.database.SupportFactory")
                        val constructor = factoryClass.getConstructor(ByteArray::class.java)
                        val factory = constructor.newInstance(dbPassword.toByteArray()) as androidx.sqlite.db.SupportSQLiteOpenHelper.Factory
                        builder.openHelperFactory(factory)
                        android.util.Log.i("AppDatabase", "SQLCipher SupportFactory (net.sqlcipher.database) applied successfully.")
                        factoryApplied = true
                    } catch (t: Throwable) {
                        android.util.Log.i("AppDatabase", "net.sqlcipher.database.SupportFactory not available: ${t.message}")
                    }

                    // Try net.zetetic.database.sqlcipher.SupportFactory as fallback
                    if (!factoryApplied) {
                        try {
                            val factoryClass = Class.forName("net.zetetic.database.sqlcipher.SupportFactory")
                            val constructor = factoryClass.getConstructor(ByteArray::class.java)
                            val factory = constructor.newInstance(dbPassword.toByteArray()) as androidx.sqlite.db.SupportSQLiteOpenHelper.Factory
                            builder.openHelperFactory(factory)
                            android.util.Log.i("AppDatabase", "SQLCipher SupportFactory (net.zetetic.database.sqlcipher) applied successfully.")
                            factoryApplied = true
                        } catch (t: Throwable) {
                            android.util.Log.e("AppDatabase", "Failed to apply SQLCipher SupportFactory via all package paths.", t)
                        }
                    }
                } else {
                    android.util.Log.i("AppDatabase", "Bypassing SQLCipher SupportFactory in unit/Robolectric test environment.")
                }

                // Add query callback to trace and log SQL query execution
                try {
                    builder.setQueryCallback(
                        { sqlQuery, bindArgs ->
                            android.util.Log.v("SQLTrace", "Executing SQL: $sqlQuery | Args: $bindArgs")
                        },
                        java.util.concurrent.Executors.newSingleThreadExecutor()
                    )
                    android.util.Log.i("AppDatabase", "Query execution trace callback registered successfully.")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Failed to register Query Trace Callback: ${e.message}")
                }

                val instance = builder
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_13_14, MIGRATION_14_15)
                    .fallbackToDestructiveMigration()
                    .build()
                android.util.Log.i("AppDatabase", "Room Database instance built successfully.")
                INSTANCE = instance
                instance
            }
        }
    }
}
