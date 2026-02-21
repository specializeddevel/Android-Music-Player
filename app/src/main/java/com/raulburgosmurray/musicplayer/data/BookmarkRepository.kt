package com.raulburgosmurray.musicplayer.data

import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    suspend fun addBookmark(mediaId: String, position: Long, note: String) =
        bookmarkDao.insertBookmark(Bookmark(mediaId = mediaId, position = position, note = note))

    fun getBookmarksForMedia(mediaId: String): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksForMedia(mediaId)

    suspend fun deleteBookmark(bookmarkId: Int) =
        bookmarkDao.deleteBookmark(bookmarkId)

    suspend fun updateBookmarkNote(bookmarkId: Int, newNote: String) =
        bookmarkDao.updateBookmarkNote(bookmarkId, newNote)
}
