package com.raulburgosmurray.musicplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AudiobookProgress::class,
        FavoriteBook::class,
        Bookmark::class,
        QueueItem::class,
        CachedBook::class
    ],
    version = 13,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun queueDao(): QueueDao
    abstract fun cachedBookDao(): CachedBookDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v10 → v11: added isRead column to track completed audiobooks
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE audiobook_progress ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // v11 → v12: added pitch column for playback pitch control
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE audiobook_progress ADD COLUMN pitch REAL NOT NULL DEFAULT 1.0"
                )
            }
        }

        // v12 → v13: added eqPresetName column for per-book equalizer preset
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL(
                        "ALTER TABLE audiobook_progress ADD COLUMN eqPresetName TEXT NOT NULL DEFAULT ''"
                    )
                } catch (e: android.database.sqlite.SQLiteException) {
                    // Column may already exist from a previous install; ignore
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "audiobook_database"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
