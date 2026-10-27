package com.raulburgosmurray.musicplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOrientationPolicyTest {
    @Test
    fun `phones remain portrait only`() {
        assertFalse(allowsLandscapeLayout(0))
        assertFalse(allowsLandscapeLayout(360))
        assertFalse(allowsLandscapeLayout(599))
    }

    @Test
    fun `tablets allow landscape from 600dp`() {
        assertTrue(allowsLandscapeLayout(600))
        assertTrue(allowsLandscapeLayout(720))
    }
}
