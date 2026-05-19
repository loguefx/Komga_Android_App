package com.komga.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks the chapter count at which we last notified the user for each series.
 * Only when the current count exceeds [lastNotifiedCount] do we fire a new notification.
 */
@Entity(tableName = "notification_state")
data class NotificationStateEntity(
    @PrimaryKey val seriesId: String,
    val seriesTitle: String = "",
    /** Chapter count at the time of the last notification (or 0 if never notified). */
    val lastNotifiedCount: Int = 0
)
