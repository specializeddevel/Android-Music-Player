package com.raulburgosmurray.musicplayer.data

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

data class CloudProgress(
    val bookKey: String,
    val lastPosition: Long,
    val duration: Long,
    val lastUpdated: Long,
    val lastPauseTimestamp: Long,
    val playbackSpeed: Float,
    val pitch: Float,
    val eqPresetName: String,
    val isRead: Boolean
)

fun portableBookKey(book: CachedBook): String {
    val stableName = book.fileName.takeIf { it.isNotBlank() }
        ?: "${book.title}|${book.artist}"
    val identity = listOf(
        normalizeIdentityPart(stableName),
        book.duration.toString(),
        book.fileSize.toString()
    ).joinToString("|")
    return MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

fun AudiobookProgress.toCloudProgress(book: CachedBook) = CloudProgress(
    bookKey = portableBookKey(book),
    lastPosition = lastPosition,
    duration = duration,
    lastUpdated = lastUpdated,
    lastPauseTimestamp = lastPauseTimestamp,
    playbackSpeed = playbackSpeed,
    pitch = pitch,
    eqPresetName = eqPresetName,
    isRead = isRead
)

fun CloudProgress.toLocalProgress(mediaId: String) = AudiobookProgress(
    mediaId = mediaId,
    lastPosition = lastPosition,
    duration = duration,
    lastUpdated = lastUpdated,
    lastPauseTimestamp = lastPauseTimestamp,
    playbackSpeed = playbackSpeed,
    pitch = pitch,
    eqPresetName = eqPresetName,
    isRead = isRead
)

private fun normalizeIdentityPart(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
