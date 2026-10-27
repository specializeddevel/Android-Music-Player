package com.raulburgosmurray.musicplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferUIStateTest {
    @Test
    fun `receiver entry clears previous error progress and result`() {
        val old = TransferUIState(error = "Old error", transferStatus = "Old status",
            downloadProgress = 0.8f, receiveSuccess = true, showConflictDialog = true,
            pendingBookTitle = "Old book", targetIp = "old:50001", localIp = "192.168.1.2")
        assertEquals(TransferUIState(transferStatus = "Ready", localIp = "192.168.1.2"),
            old.forReceiverEntry("Ready"))
    }

    @Test
    fun `receiver entry preserves an active download`() {
        val active = TransferUIState(isDownloading = true, downloadProgress = 0.5f,
            transferStatus = "Receiving", targetIp = "sender:50001")
        assertEquals(active, active.forReceiverEntry("Ready"))
    }
}
