package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.LiveTvRepository
import com.example.data.BackgroundSyncManager

class LiveTvApplication : Application() {
    
    lateinit var database: AppDatabase
    lateinit var repository: LiveTvRepository
    lateinit var syncManager: BackgroundSyncManager

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        repository = LiveTvRepository(database.liveTvDao(), this)
        syncManager = BackgroundSyncManager(repository, this)
    }
}
