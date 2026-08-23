package com.example.cloudrive.data.local.music

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun get(): QueueStateEntity?

    @Upsert
    suspend fun save(state: QueueStateEntity)
}
