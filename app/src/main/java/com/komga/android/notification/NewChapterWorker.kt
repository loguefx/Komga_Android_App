package com.komga.android.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.komga.android.MainActivity
import com.komga.android.R
import com.komga.android.data.local.NotificationStateDao
import com.komga.android.data.local.NotificationStateEntity
import com.komga.android.data.local.FavoriteDao
import com.komga.android.data.local.PreferencesDataStore
import com.komga.android.data.repository.KomgaRepository
import com.komga.android.data.repository.Result
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

const val NOTIFICATION_CHANNEL_NEW_CHAPTERS = "new_chapters"

@HiltWorker
class NewChapterWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: KomgaRepository,
    private val favoriteDao: FavoriteDao,
    private val notificationStateDao: NotificationStateDao,
    private val preferencesDataStore: PreferencesDataStore
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Skip if not logged in
        if (!preferencesDataStore.isLoggedIn().first()) return Result.success()

        val favorites = favoriteDao.getAllFavorites().first()
        if (favorites.isEmpty()) return Result.success()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        var notifiedCount = 0

        for (favorite in favorites) {
            val seriesId = favorite.seriesId
            val seriesTitle = favorite.title

            // Fetch current chapter count from Komga
            val booksResult = repository.getBooksBySeries(seriesId, size = 1)
            // We only need the total count; fetch the actual total from a larger call
            val countResult = repository.getSeriesById(seriesId)
            if (countResult !is com.komga.android.data.repository.Result.Success) continue

            val currentCount = countResult.data.booksCount

            // Look up what count we last notified about
            val state = notificationStateDao.getBySeriesId(seriesId)
            val lastNotifiedCount = state?.lastNotifiedCount ?: 0

            val newChapters = currentCount - lastNotifiedCount

            if (newChapters > 0 && lastNotifiedCount > 0) {
                // There are genuinely NEW chapters since the last notification
                showNotification(
                    context = context,
                    notificationManager = notificationManager,
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    newChapterCount = newChapters,
                    totalChapterCount = currentCount
                )
                notifiedCount++
            }

            // Always sync the stored count to current (even on first run, no notification)
            notificationStateDao.upsert(
                NotificationStateEntity(
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    lastNotifiedCount = currentCount
                )
            )
        }

        return Result.success()
    }

    private fun showNotification(
        context: Context,
        notificationManager: NotificationManager,
        seriesId: String,
        seriesTitle: String,
        newChapterCount: Int,
        totalChapterCount: Int
    ) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            seriesId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val chapterWord = if (newChapterCount == 1) "chapter" else "chapters"
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_NEW_CHAPTERS)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(seriesTitle)
            .setContentText("$newChapterCount new $chapterWord available  •  $totalChapterCount total")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$newChapterCount new $chapterWord available. Series now has $totalChapterCount chapters.")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_CHANNEL_NEW_CHAPTERS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Use seriesId hashCode as unique notification ID so each series gets its own notification
        notificationManager.notify(seriesId.hashCode(), notification)
    }
}
