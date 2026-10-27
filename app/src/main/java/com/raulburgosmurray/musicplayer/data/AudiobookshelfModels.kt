package com.raulburgosmurray.musicplayer.data

import kotlinx.serialization.Serializable

@Serializable
data class AbsLoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class AbsUser(
    val id: String = "",
    val username: String = "",
    val token: String = "",
    val type: String = ""
)

@Serializable
data class AbsLoginResponse(
    val user: AbsUser? = null,
    val error: String? = null
)

@Serializable
data class AbsLibrary(
    val id: String,
    val name: String,
    val mediaType: String = ""
)

@Serializable
data class AbsLibrariesResponse(
    val libraries: List<AbsLibrary> = emptyList()
)

@Serializable
data class AbsMetadata(
    val title: String? = null,
    val authorName: String? = null,
    val narratorName: String? = null,
    val seriesName: String? = null,
    val publishedYear: String? = null,
    val genre: String? = null,
    val description: String? = null
)

@Serializable
data class AbsAudioFileMetadata(
    val filename: String? = null,
    val ext: String? = null,
    val size: Long? = null,
    val tagTitle: String? = null,
    val tagArtist: String? = null,
    val tagAlbum: String? = null,
    val tagComment: String? = null
)

@Serializable
data class AbsAudioFile(
    val index: Int? = null,
    val ino: String? = null,
    val duration: Double? = null,
    val mimeType: String? = null,
    val metadata: AbsAudioFileMetadata? = null
)

@Serializable
data class AbsAudioTrack(
    val index: Int? = null,
    val startOffset: Double? = null,
    val duration: Double? = null,
    val contentUrl: String? = null,
    val mimeType: String? = null
)

@Serializable
data class AbsMedia(
    val id: String? = null,
    val metadata: AbsMetadata? = null,
    val coverPath: String? = null,
    val duration: Double? = null,
    val size: Long? = null,
    val audioFiles: List<AbsAudioFile> = emptyList(),
    val tracks: List<AbsAudioTrack> = emptyList()
)

@Serializable
data class AbsLibraryItem(
    val id: String,
    val libraryId: String = "",
    val mediaType: String = "",
    val media: AbsMedia? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null
)

@Serializable
data class AbsLibraryItemsResponse(
    val results: List<AbsLibraryItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val limit: Int = 0
)

@Serializable
data class AbsProgressPayload(
    val currentTime: Double,
    val duration: Double,
    val progress: Double,
    val isFinished: Boolean
)
