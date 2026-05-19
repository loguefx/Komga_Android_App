package com.komga.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationStateDao {

    @Query("SELECT * FROM notification_state WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getBySeriesId(seriesId: String): NotificationStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: NotificationStateEntity)

    @Query("DELETE FROM notification_state WHERE seriesId = :seriesId")
    suspend fun deleteBySeriesId(seriesId: String)
}
