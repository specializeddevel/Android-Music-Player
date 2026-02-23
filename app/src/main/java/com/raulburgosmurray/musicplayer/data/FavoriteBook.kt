package com.raulburgosmurray.musicplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["addedDate"])
    ]
)
data class FavoriteBook(
    @PrimaryKey val mediaId: String,
    val addedDate: Long = System.currentTimeMillis()
)
