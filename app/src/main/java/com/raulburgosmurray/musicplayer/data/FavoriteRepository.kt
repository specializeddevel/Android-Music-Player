package com.raulburgosmurray.musicplayer.data

import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val favoriteDao: FavoriteDao) {

    suspend fun addFavorite(mediaId: String) =
        favoriteDao.addFavorite(FavoriteBook(mediaId))

    suspend fun removeFavorite(mediaId: String) =
        favoriteDao.removeFavorite(mediaId)

    fun isFavorite(mediaId: String): Flow<Boolean> =
        favoriteDao.isFavorite(mediaId)

    fun getAllFavoriteIds(): Flow<List<String>> =
        favoriteDao.getAllFavoriteIds()
}
