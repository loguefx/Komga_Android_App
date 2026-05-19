package com.komga.android.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.komga.android.widget.KomgaWidget
import com.komga.android.widget.WIDGET_KEY_BOOKS
import com.komga.android.widget.WIDGET_PREFS
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.komga.android.MainActivity
import com.komga.android.R
import com.komga.android.data.local.FavoriteDao
import com.komga.android.data.local.NotificationStateDao
import com.komga.android.data.local.NotificationStateEntity
import com.komga.android.data.local.PreferencesDataStore
import com.komga.android.data.repository.KomgaRepository
import com.komga.android.data.repository.Result as RepoResult
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

        for (favorite in favorites) {
            val seriesId = favorite.seriesId
            val seriesTitle = favorite.title

            // Fetch current chapter count for this series from Komga
            val countResult = repository.getSeriesById(seriesId)
            if (countResult !is RepoResult.Success) continue

            val currentCount = countResult.data.booksCount

            // Look up the count at which we last notified
            val state = notificationStateDao.getBySeriesId(seriesId)
            val lastNotifiedCount = state?.lastNotifiedCount ?: 0

            val newChapters = currentCount - lastNotifiedCount

            if (newChapters > 0 && lastNotifiedCount > 0) {
                // Genuine new chapters since last notification — notify once
                showNotification(
                    notificationManager = notificationManager,
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    newChapterCount = newChapters,
                    totalChapterCount = currentCount
                )
            }

            // Always persist the latest known count so future runs have a baseline.
            // On the very first run lastNotifiedCount == 0, so we silently record the
            // current count without showing a notification.
            notificationStateDao.upsert(
                NotificationStateEntity(
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    lastNotifiedCount = currentCount
                )
            )
        }

        // Update widget with current On Deck books
        updateWidget(context)

        return Result.success()
    }

    private suspend fun updateWidget(context: Context) {
        try {
            val onDeckResult = repository.getBooksOnDeck(size = 5)
            if (onDeckResult is RepoResult.Success) {
                val titles = onDeckResult.data.map { book ->
                    if (book.seriesTitle.isNotBlank()) {
                        "${book.seriesTitle} #${book.number.let { n ->
                            if (n == n.toLong().toFloat()) n.toLong().toString() else n.toString()
                        }}"
                    } else book.name
                }
                // Persist for GlanceWidget to read
                context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(WIDGET_KEY_BOOKS, titles.joinToString("\n"))
                    .apply()

                // Trigger widget refresh
                val manager = GlanceAppWidgetManager(context)
                manager.getGlanceIds(KomgaWidget::class.java).forEach { id ->
                    KomgaWidget().update(context, id)
                }
            }
        } catch (_: Exception) { /* Widget update failures are non-critical */ }
    }

    private fun showNotification(
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(seriesTitle)
            .setContentText("$newChapterCount new $chapterWord available  •  $totalChapterCount total")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "$newChapterCount new $chapterWord available. " +
                        "Series now has $totalChapterCount chapters."
                    )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_CHANNEL_NEW_CHAPTERS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Each series gets its own notification slot (based on seriesId hashCode)
        notificationManager.notify(seriesId.hashCode(), notification)
    }
}
