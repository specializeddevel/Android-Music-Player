package com.raulburgosmurray.musicplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks WHERE mediaId = :mediaId ORDER BY position ASC")
    fun getBookmarksForMedia(mediaId: String): Flow<List<Bookmark>>

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Int)

    @Query("UPDATE bookmarks SET note = :newNote WHERE id = :bookmarkId")
    suspend fun updateBookmarkNote(bookmarkId: Int, newNote: String)
}
