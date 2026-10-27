package com.raulburgosmurray.musicplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataEditorScreenTest {

    @Test
    fun `save is enabled when required fields contain text and editor is idle`() {
        assertTrue(isMetadataSaveEnabled("The Hobbit", "J. R. R. Tolkien", isSaving = false))
    }

    @Test
    fun `save is disabled when title or author is blank`() {
        assertFalse(isMetadataSaveEnabled("", "Author", isSaving = false))
        assertFalse(isMetadataSaveEnabled("Title", "   ", isSaving = false))
    }

    @Test
    fun `save is disabled while metadata is being saved`() {
        assertFalse(isMetadataSaveEnabled("Title", "Author", isSaving = true))
    }
}
