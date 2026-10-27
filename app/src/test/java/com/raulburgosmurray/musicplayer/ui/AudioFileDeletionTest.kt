package com.raulburgosmurray.musicplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFileDeletionTest {

    @Test
    fun `MediaStore content URI is classified separately from SAF`() {
        assertEquals(
            AudioFileLocation.MEDIA_STORE,
            classifyAudioFileLocation("content://media/external/audio/media/42")
        )
    }

    @Test
    fun `SAF content URI remains a document URI`() {
        assertEquals(
            AudioFileLocation.DOCUMENT,
            classifyAudioFileLocation("content://com.android.externalstorage.documents/document/primary%3ABooks%2Fone.mp3")
        )
    }

    @Test
    fun `plain and file paths are classified as files`() {
        assertEquals(AudioFileLocation.FILE, classifyAudioFileLocation("/storage/emulated/0/one.mp3"))
        assertEquals(AudioFileLocation.FILE, classifyAudioFileLocation("file:///storage/emulated/0/one.mp3"))
    }
}
