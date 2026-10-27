package com.raulburgosmurray.musicplayer.data

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DescriptionExtractorTest {

    @Test
    fun `test extractFromLocalFile on downloaded Conclave m4a`() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        val path = "C:\\Users\\raulb\\Downloads\\Cónclave.m4a"
        if (File(path).exists()) {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(File(path))
            val tag = audioFile.tag
            assertNotNull("Tag should not be null", tag)
            var foundComment = false
            val fields = tag!!.fields
            while (fields.hasNext()) {
                val f = fields.next()
                if (f.id.contains("cmt") || f.id.contains("des") || f.id.contains("COMMENT") || f.id.contains("ldes")) {
                    foundComment = true
                }
            }
            assertTrue("Should find comment field", foundComment)
            val result = DescriptionExtractor.extractFromLocalFile(path)
            assertNotNull("Synopsis should be extracted from Conclave.m4a", result)
        }
    }
}
