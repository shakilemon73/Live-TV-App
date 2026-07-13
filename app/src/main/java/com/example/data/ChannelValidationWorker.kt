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
            val defaultUrl = "https://iptv-api-worker.shakilemon71.workers.dev/api/channels"
            val cloudGistUrl = prefs.getString("cloud_gist_url", defaultUrl) ?: defaultUrl
            val autoSync = prefs.getBoolean("auto_sync_on_launch", true)

            // Guard: skip if the app itself did a sync in the last 30 minutes
            // (prevents double-work when the WorkManager fires right after app launch)
            val lastSyncTime = prefs.getLong("last_sync_time", 0L)
            val msSinceLastSync = System.currentTimeMillis() - lastSyncTime
            if (msSinceLastSync < 30 * 60 * 1000L) {
                Log.d(TAG, "WorkManager: skipping — app synced ${msSinceLastSync / 1000}s ago (< 30 min).")
                return Result.success()
            }

            val success = if (autoSync && cloudGistUrl.isNotBlank()) {
                Log.d(TAG, "Auto-sync is enabled. Fetching playlist via Worker sync: $cloudGistUrl")
                syncManager.syncWithCloudGistSuspend(cloudGistUrl)
            } else {
                Log.d(TAG, "Auto-sync disabled. Re-validating broken channels via Worker batch API.")
                syncManager.verifyBrokenChannelsViaWorker()
                true
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

            // Run periodically (every 12 hours) to validate links and stay updated.
            // setInitialDelay(30 min) ensures this NEVER fires simultaneously with the
            // ViewModel's startup sync on the very first install — avoiding the double-sync
            // that was causing the 10-minute first-open delay.
            val periodicWorkRequest = PeriodicWorkRequestBuilder<ChannelValidationWorker>(
                12, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES)
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
            Log.i(TAG, "Scheduled periodic channel validation & sync work (every 12 hours, initialDelay=30min, unmeteredOnly=$unmeteredOnly, batteryNotLow=true, policy=$policy).")
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
