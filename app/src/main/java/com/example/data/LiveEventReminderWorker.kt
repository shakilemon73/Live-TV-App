package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.LiveTvApplication
import java.util.concurrent.TimeUnit

class LiveEventReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        Log.d(TAG, "LiveEventReminderWorker triggered for event ID: $eventId")

        return try {
            val app = applicationContext as LiveTvApplication
            val repository = app.repository

            // 1. Fetch interested event from database
            val interestedEvent = repository.getInterestedEventById(eventId)
            if (interestedEvent == null) {
                Log.e(TAG, "Event not found in interested events: $eventId")
                return Result.success()
            }

            if (interestedEvent.isNotified) {
                Log.i(TAG, "Event already notified: $eventId")
                return Result.success()
            }

            // 2. Map to GroupedEvent
            val groupedEvent = interestedEvent.toGroupedEvent()
            val primaryFeed = groupedEvent.feeds.firstOrNull()

            if (primaryFeed == null) {
                Log.e(TAG, "No feeds available for interested event: $eventId")
                return Result.success()
            }

            // 3. Post system notification
            sendNotification(groupedEvent, primaryFeed)

            // 4. Update notify state in database
            repository.updateInterestedEventNotified(eventId, true)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing LiveEventReminderWorker", e)
            Result.retry()
        }
    }

    private fun sendNotification(event: GroupedEvent, feed: EventFeed) {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create high-priority notification channel for Android Oreo+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Sports Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you when sports matches and live broadcast events you marked as interested are about to start."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build Intent to open MainActivity and trigger live playback
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("com.example.extra.PLAY_STREAM", true)
            putExtra("com.example.extra.EVENT_TITLE", event.title)
            putExtra("com.example.extra.EVENT_CATEGORY", event.sportCategory)
            putExtra("com.example.extra.EVENT_LOGO", event.logoUrl)
            putExtra("com.example.extra.FEED_URL", feed.streamUrl)
            putExtra("com.example.extra.FEED_PROVIDER", feed.provider)
            putExtra("com.example.extra.FEED_LANGUAGE", feed.language)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            intent,
            pendingIntentFlags
        )

        // Premium sports-themed notification styling
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Live Match Starting! 🏆")
            .setContentText("${event.title} is starting now on ${feed.provider}! Tap to watch live stream.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_play,
                "WATCH NOW",
                pendingIntent
            )
            .build()

        notificationManager.notify(event.id.hashCode(), notification)
        Log.i(TAG, "Notification successfully posted for: ${event.title}")
    }

    companion object {
        private const val TAG = "LiveEventReminderWorker"
        private const val CHANNEL_ID = "live_event_reminders_channel"
        const val KEY_EVENT_ID = "event_id"

        fun scheduleReminder(context: Context, eventId: String, delayMillis: Long) {
            val data = Data.Builder()
                .putString(KEY_EVENT_ID, eventId)
                .build()

            // Schedule with delay using WorkManager
            val workRequest = OneTimeWorkRequestBuilder<LiveEventReminderWorker>()
                .setInputData(data)
                .setInitialDelay(Math.max(0L, delayMillis), TimeUnit.MILLISECONDS)
                .addTag("Reminder_$eventId")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.i(TAG, "Scheduled reminder for event $eventId in $delayMillis ms")
        }

        fun cancelReminder(context: Context, eventId: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag("Reminder_$eventId")
            Log.i(TAG, "Cancelled reminder for event $eventId")
        }

        fun showInstantNotification(context: Context, title: String, message: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Live Sports Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts you when sports matches and live broadcast events you marked as interested are about to start."
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                pendingIntentFlags
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
