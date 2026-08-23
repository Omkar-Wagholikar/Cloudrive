package com.example.cloudrive.data.local.music

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE fileId = :fileId")
    fun observe(fileId: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE fileId = :fileId")
    suspend fun get(fileId: Long): DownloadEntity?

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE fileId = :fileId")
    suspend fun delete(fileId: Long)

    @Query("SELECT fileId FROM downloads WHERE state = :state")
    suspend fun idsWithState(state: String): List<Long>
}
