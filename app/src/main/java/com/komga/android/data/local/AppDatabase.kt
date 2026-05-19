package com.komga.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoriteEntity::class, NotificationStateEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun notificationStateDao(): NotificationStateDao

    companion object {
        /** Adds the notification_state table without touching favorites data. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS notification_state (
                        seriesId TEXT NOT NULL PRIMARY KEY,
                        seriesTitle TEXT NOT NULL DEFAULT '',
                        lastNotifiedCount INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }
    }
}
