package com.raulburgosmurray.musicplayer.data

data class BookTrack(
    val index: Int,
    val ino: String,
    val streamUrl: String,
    val startOffsetMs: Long,
    val durationMs: Long
)

data class MultiTrackInfo(
    val bookId: String,
    val totalDurationMs: Long,
    val tracks: List<BookTrack>
)

object MultiTrackHelper {

    fun buildMultiTrackInfo(
        bookId: String,
        audioFiles: List<AbsAudioFile>?,
        baseUrl: String,
        token: String
    ): MultiTrackInfo {
        if (audioFiles.isNullOrEmpty()) {
            val fallbackUrl = AudiobookshelfClient.buildAudioStreamUrl(baseUrl, token, bookId.removePrefix("abs_"), "0")
            val singleTrack = BookTrack(
                index = 0,
                ino = "0",
                streamUrl = fallbackUrl,
                startOffsetMs = 0L,
                durationMs = 0L
            )
            return MultiTrackInfo(bookId = bookId, totalDurationMs = 0L, tracks = listOf(singleTrack))
        }

        var cumulativeMs = 0L
        val tracks = audioFiles.mapIndexed { idx, af ->
            val trackDurMs = ((af.duration ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
            val ino = af.ino ?: idx.toString()
            val streamUrl = AudiobookshelfClient.buildAudioStreamUrl(baseUrl, token, bookId.removePrefix("abs_"), ino)
            val track = BookTrack(
                index = idx,
                ino = ino,
                streamUrl = streamUrl,
                startOffsetMs = cumulativeMs,
                durationMs = trackDurMs
            )
            cumulativeMs += trackDurMs
            track
        }

        return MultiTrackInfo(
            bookId = bookId,
            totalDurationMs = cumulativeMs,
            tracks = tracks
        )
    }

    fun globalPositionToTrack(info: MultiTrackInfo, globalPosMs: Long): Pair<Int, Long> {
        val tracks = info.tracks
        if (tracks.isEmpty()) return Pair(0, 0L)

        val clampedGlobal = globalPosMs.coerceIn(0L, info.totalDurationMs.coerceAtLeast(0L))

        for (track in tracks) {
            val trackEndMs = track.startOffsetMs + track.durationMs
            if (clampedGlobal < trackEndMs || track == tracks.last()) {
                val posInTrack = (clampedGlobal - track.startOffsetMs).coerceIn(0L, track.durationMs.coerceAtLeast(0L))
                return Pair(track.index, posInTrack)
            }
        }

        val lastTrack = tracks.last()
        return Pair(lastTrack.index, lastTrack.durationMs)
    }

    fun trackPositionToGlobal(info: MultiTrackInfo, trackIndex: Int, posInTrackMs: Long): Long {
        val tracks = info.tracks
        if (tracks.isEmpty()) return 0L

        val track = tracks.getOrNull(trackIndex.coerceIn(0, tracks.size - 1)) ?: return 0L
        val clampedPosInTrack = posInTrackMs.coerceIn(0L, track.durationMs.coerceAtLeast(0L))
        val globalPos = (track.startOffsetMs + clampedPosInTrack).coerceIn(0L, info.totalDurationMs.coerceAtLeast(0L))
        return globalPos
    }

    fun isBookCompleted(globalPosMs: Long, totalDurationMs: Long): Boolean {
        if (totalDurationMs <= 0L) return false
        val progressRatio = globalPosMs.toDouble() / totalDurationMs.toDouble()
        return progressRatio >= 0.99
    }
}
