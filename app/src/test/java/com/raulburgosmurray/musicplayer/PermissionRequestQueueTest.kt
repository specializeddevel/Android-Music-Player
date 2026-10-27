package com.raulburgosmurray.musicplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionRequestQueueTest {

    @Test
    fun `first request becomes current immediately`() {
        val queue = PermissionRequestQueue<String>()

        queue.enqueue("audio")

        assertEquals("audio", queue.current)
    }

    @Test
    fun `requests are presented in insertion order`() {
        val queue = PermissionRequestQueue<String>()
        queue.enqueue("audio")
        queue.enqueue("notifications")
        queue.enqueue("camera")

        assertEquals("notifications", queue.advance())
        assertEquals("camera", queue.advance())
        assertNull(queue.advance())
    }

    @Test
    fun `clear removes the current and pending requests`() {
        val queue = PermissionRequestQueue<String>()
        queue.enqueue("audio")
        queue.enqueue("notifications")

        queue.clear()

        assertNull(queue.current)
        assertNull(queue.advance())
    }
}
