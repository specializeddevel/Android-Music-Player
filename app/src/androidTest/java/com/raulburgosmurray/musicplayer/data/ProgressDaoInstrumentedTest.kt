package com.raulburgosmurray.musicplayer.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressDaoInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ProgressDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = database.progressDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun partialUpdatesCreateAndPreserveOneRecord() = runBlocking {
        dao.applyUpdate("new-book", ProgressUpdate(playbackSpeed = 1.5f))
        dao.applyUpdate("new-book", ProgressUpdate(pitch = 0.9f))
        dao.applyUpdate("new-book", ProgressUpdate(lastPosition = 42_000L, duration = 90_000L))

        val stored = dao.getProgress("new-book")!!
        assertEquals(42_000L, stored.lastPosition)
        assertEquals(90_000L, stored.duration)
        assertEquals(1.5f, stored.playbackSpeed)
        assertEquals(0.9f, stored.pitch)
    }

    @Test
    fun concurrentUpdatesDoNotLoseIndependentFields() = runBlocking {
        dao.applyUpdate(
            "book",
            ProgressUpdate(eqPresetName = "VOICE", isRead = true, lastPauseTimestamp = 123L)
        )

        listOf(
            async(Dispatchers.IO) { dao.applyUpdate("book", ProgressUpdate(lastPosition = 50_000L, duration = 100_000L)) },
            async(Dispatchers.IO) { dao.applyUpdate("book", ProgressUpdate(playbackSpeed = 1.75f)) },
            async(Dispatchers.IO) { dao.applyUpdate("book", ProgressUpdate(pitch = 0.85f)) }
        ).awaitAll()

        val stored = dao.getProgress("book")!!
        assertEquals(50_000L, stored.lastPosition)
        assertEquals(100_000L, stored.duration)
        assertEquals(1.75f, stored.playbackSpeed)
        assertEquals(0.85f, stored.pitch)
        assertEquals("VOICE", stored.eqPresetName)
        assertTrue(stored.isRead)
        assertEquals(123L, stored.lastPauseTimestamp)
    }
}
