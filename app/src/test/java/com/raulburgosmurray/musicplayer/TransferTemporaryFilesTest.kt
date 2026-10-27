package com.raulburgosmurray.musicplayer

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TransferTemporaryFilesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val context = mockk<Context>()
    private var pending = emptySet<String>()

    @Before
    fun setup() {
        TransferTemporaryFiles::class.java.getDeclaredField("startupCleaned").apply {
            isAccessible = true
            setBoolean(null, false)
        }
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getStringSet(any(), any()) } answers { pending.toSet() }
        every { prefs.edit() } returns editor
        every { editor.putStringSet(any(), any()) } answers {
            pending = secondArg<Set<String>>().toSet()
            editor
        }
        every { editor.commit() } returns true
    }

    @Test
    fun `startup deletes registered local temporary but preserves completed audio`() {
        val partial = temporaryFolder.newFile("litera-transfer-old.part")
        val complete = temporaryFolder.newFile("book.mp3")
        pending = setOf(partial.absolutePath, complete.absolutePath)
        TransferTemporaryFiles.cleanupOnStartup(context)
        assertEquals(false, partial.exists())
        assertEquals(true, complete.exists())
        assertEquals(emptySet<String>(), pending)
    }

    @Test
    fun `registration cleans stale entries without deleting new active transfer`() {
        val stale = temporaryFolder.newFile("litera-transfer-stale.part")
        val active = temporaryFolder.newFile("litera-transfer-active.part")
        pending = setOf(stale.absolutePath)
        TransferTemporaryFiles.register(context, active.absolutePath)
        TransferTemporaryFiles.cleanupOnStartup(context)
        assertEquals(false, stale.exists())
        assertEquals(true, active.exists())
        assertEquals(setOf(active.absolutePath), pending)
        TransferTemporaryFiles.forget(context, active.absolutePath)
        assertEquals(emptySet<String>(), pending)
    }

    @Test
    fun `missing temporary is removed from registry`() {
        pending = setOf(File(temporaryFolder.root, "litera-transfer-missing.part").absolutePath)
        TransferTemporaryFiles.cleanupOnStartup(context)
        assertEquals(emptySet<String>(), pending)
    }
    @Test
    fun `cleanup removes deleted entries and retains failures`() {
        val remaining = TransferTemporaryFiles.cleanEntries(setOf("deleted", "failed")) {
            it == "deleted"
        }
        assertEquals(setOf("failed"), remaining)
    }

    @Test
    fun `cleanup retains entries when storage throws`() {
        assertEquals(setOf("denied"), TransferTemporaryFiles.cleanEntries(setOf("denied")) {
            throw SecurityException("Permission revoked")
        })
    }

    @Test
    fun `temporary name excludes completed audio and unrelated part files`() {
        assertEquals(true, TransferTemporaryFiles.isTemporaryName("litera-transfer-123.part"))
        assertEquals(false, TransferTemporaryFiles.isTemporaryName("book.mp3"))
        assertEquals(false, TransferTemporaryFiles.isTemporaryName("other.part"))
    }
}
