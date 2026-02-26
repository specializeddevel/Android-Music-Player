package com.raulburgosmurray.musicplayer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE audiobook_progress ADD COLUMN lastPauseTimestamp INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE audiobook_progress ADD COLUMN playbackSpeed REAL NOT NULL DEFAULT 1.0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS playback_queue (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mediaId TEXT NOT NULL, orderIndex INTEGER NOT NULL)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE cached_books ADD COLUMN fileName TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_books_title ON cached_books(title)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_books_artist ON cached_books(artist)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_books_album ON cached_books(album)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS index_audiobook_progress_lastPauseTimestamp ON audiobook_progress(lastPauseTimestamp)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_audiobook_progress_lastUpdated ON audiobook_progress(lastUpdated)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_addedDate ON favorites(addedDate)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_mediaId ON bookmarks(mediaId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_timestamp ON bookmarks(timestamp)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_playback_queue_orderIndex ON playback_queue(orderIndex)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_playback_queue_mediaId ON playback_queue(mediaId)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE audiobook_progress ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11
)