package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit

class ChannelValidationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "WorkManager periodic channel validation & sync has been triggered in the background!")
        return try {
            val app = applicationContext as com.example.LiveTvApplication
            val repository = app.repository
            val syncManager = app.syncManager

            val prefs = applicationContext.getSharedPreferences("live_tv_prefs", Context.MODE_PRIVATE)
            val cloudGistUrl = prefs.getString("cloud_gist_url", "https://github.com/abusaeeidx/Mrgify-BDIX-IPTV/raw/main/playlist.m3u") ?: "https://github.com/abusaeeidx/Mrgify-BDIX-IPTV/raw/main/playlist.m3u"
            val autoSync = prefs.getBoolean("auto_sync_on_launch", true)

            val success = if (autoSync && cloudGistUrl.isNotBlank()) {
                Log.d(TAG, "Auto-sync is enabled. Fetching playlist and verifying streams: $cloudGistUrl")
                syncManager.syncWithCloudGistSuspend(cloudGistUrl)
            } else {
                Log.d(TAG, "Auto-sync disabled or empty URL. Only verifying existing channels.")
                syncManager.verifyAllChannelsSuspend()
            }

            if (success) {
                Log.d(TAG, "WorkManager channel sync & validation finished with SUCCESS.")
                Result.success()
            } else {
                Log.e(TAG, "WorkManager channel sync & validation finished with failure/retry.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager channel validation execution failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "ChannelValidationWorker"
        private const val UNIQUE_WORK_NAME = "PeriodicChannelValidationWork"

        fun schedulePeriodicWork(context: Context, forceReplace: Boolean = false) {
            val prefs = context.applicationContext.getSharedPreferences("live_tv_prefs", Context.MODE_PRIVATE)
            val unmeteredOnly = prefs.getBoolean("unmetered_sync_only", false)
            val networkType = if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

            // Run periodically (every 12 hours) to validate links and stay updated
            val periodicWorkRequest = PeriodicWorkRequestBuilder<ChannelValidationWorker>(
                12, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            val policy = if (forceReplace) {
                ExistingPeriodicWorkPolicy.REPLACE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                policy,
                periodicWorkRequest
            )
            Log.i(TAG, "Scheduled periodic channel validation & sync work (every 12 hours, unmeteredOnly=$unmeteredOnly, batteryNotLow=true, policy=$policy).")
        }

        fun forceOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val oneTimeWorkRequest = androidx.work.OneTimeWorkRequestBuilder<ChannelValidationWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
            Log.i(TAG, "Enqueued custom one-time WorkManager channel sync & validation (batteryNotLow=true).")
        }
    }
}
