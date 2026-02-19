package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.AudiobookProgress
import com.raulburgosmurray.musicplayer.data.ProgressDao
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

import com.google.android.gms.auth.api.signin.GoogleSignIn

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    private val application = mockk<Application>(relaxed = true)
    private val database = mockk<AppDatabase>(relaxed = true)
    private val progressDao = mockk<ProgressDao>(relaxed = true)
    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mocking GoogleSignIn
        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns null

        // Mocking Database static call
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getDatabase(any()) } returns database
        every { database.progressDao() } returns progressDao
        
        // Mocking SharedPrefs
        every { application.getSharedPreferences(any(), any()) } returns sharedPrefs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `mergeProgress upserts cloud progress when it is newer than local`() = runTest {
        // Given
        val viewModel = SyncViewModel(application)
        val mediaId = "book1"
        
        val local = listOf(
            AudiobookProgress(mediaId, lastPosition = 100L, duration = 1000L, lastUpdated = 1000L)
        )
        val cloud = listOf(
            AudiobookProgress(mediaId, lastPosition = 500L, duration = 1000L, lastUpdated = 2000L) // Más reciente
        )

        // When
        viewModel.mergeProgress(local, cloud)

        // Then
        coVerify { progressDao.upsertAll(match { list -> 
            list.size == 1 && list[0].lastPosition == 500L && list[0].mediaId == mediaId
        }) }
    }

    @Test
    fun `mergeProgress does not upsert when local progress is newer than cloud`() = runTest {
        // Given
        val viewModel = SyncViewModel(application)
        val mediaId = "book1"
        
        val local = listOf(
            AudiobookProgress(mediaId, lastPosition = 1000L, duration = 5000L, lastUpdated = 3000L) // Más reciente
        )
        val cloud = listOf(
            AudiobookProgress(mediaId, lastPosition = 500L, duration = 5000L, lastUpdated = 2000L)
        )

        // When
        viewModel.mergeProgress(local, cloud)

        // Then
        coVerify(exactly = 0) { progressDao.upsertAll(any()) }
    }

    @Test
    fun `mergeProgress upserts cloud progress when it does not exist locally`() = runTest {
        // Given
        val viewModel = SyncViewModel(application)
        val mediaId = "new_book"
        
        val local = emptyList<AudiobookProgress>()
        val cloud = listOf(
            AudiobookProgress(mediaId, lastPosition = 300L, duration = 2000L, lastUpdated = 1500L)
        )

        // When
        viewModel.mergeProgress(local, cloud)

        // Then
        coVerify { progressDao.upsertAll(match { list -> 
            list.size == 1 && list[0].mediaId == mediaId 
        }) }
    }
}
