package com.example.cloudrive.data.local.music

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        TrackFts::class,
        QueueStateEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        DownloadEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun queueDao(): QueueDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile private var instance: MusicDatabase? = null

        fun getInstance(context: Context): MusicDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music.db"
                )
                    // Pre-release schema; no installed base to migrate from yet.
                    .fallbackToDestructiveMigration(true)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Fires on a fresh install AND after a destructive-migration wipe —
                            // either way the DB is empty, so the delta-sync cursor must reset too,
                            // or sync() will ask the server for "changes since" a stale timestamp
                            // and get back nothing, leaving the library empty forever.
                            MusicPrefs(context.applicationContext).lastSyncAt = 0L
                        }
                    })
                    .build().also { instance = it }
            }
    }
}
