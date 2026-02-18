package com.raulburgosmurray.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteBook(
    @PrimaryKey val mediaId: String,
    val addedDate: Long = System.currentTimeMillis()
)
