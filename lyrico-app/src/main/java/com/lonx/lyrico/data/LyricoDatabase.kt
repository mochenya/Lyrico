package com.lonx.lyrico.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.model.SongDao

@Database(
    entities = [SongEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 2, to = 3)]
)
abstract class LyricoDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}
