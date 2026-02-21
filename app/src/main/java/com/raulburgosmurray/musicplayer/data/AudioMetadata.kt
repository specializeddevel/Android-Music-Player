package com.raulburgosmurray.musicplayer.data

import kotlinx.serialization.Serializable

@Serializable
data class AudioMetadata(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val year: String?,
    val genre: String?,
    val trackNumber: Int?,
    val comment: String?,
    val artUri: String?,
    val duration: Long,
    val fileName: String,
    val extractedAt: Long = System.currentTimeMillis(),
    val userEditedFields: Set<String> = emptySet()
) {
    companion object {
        const val FIELD_TITLE = "title"
        const val FIELD_ARTIST = "artist"
        const val FIELD_ALBUM = "album"
        const val FIELD_YEAR = "year"
        const val FIELD_GENRE = "genre"
        const val FIELD_TRACK_NUMBER = "trackNumber"
        const val FIELD_COMMENT = "comment"
        const val FIELD_ART_URI = "artUri"
    }

    fun withUserEditedField(field: String): AudioMetadata {
        return copy(userEditedFields = userEditedFields + field)
    }

    fun isUserEdited(field: String): Boolean {
        return userEditedFields.contains(field)
    }
}