package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import androidx.media3.session.MediaController
import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.BookmarkDao
import com.raulburgosmurray.musicplayer.data.FavoriteDao
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import app.cash.turbine.test
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Field

import android.util.Log
import kotlinx.coroutines.flow.flowOf
import io.mockk.*
import androidx.lifecycle.viewModelScope

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {

    private val application = mockk<Application>(relaxed = true)
    private val favoriteDao = mockk<FavoriteDao>(relaxed = true)
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val progressDao = mockk<com.raulburgosmurray.musicplayer.data.ProgressDao>(relaxed = true)
    private val queueDao = mockk<com.raulburgosmurray.musicplayer.data.QueueDao>(relaxed = true)
    private val cachedBookDao = mockk<com.raulburgosmurray.musicplayer.data.CachedBookDao>(relaxed = true)
    private val database = mockk<AppDatabase> {
        every { favoriteDao() } returns favoriteDao
        every { bookmarkDao() } returns bookmarkDao
        every { progressDao() } returns progressDao
        every { queueDao() } returns queueDao
        every { cachedBookDao() } returns cachedBookDao
    }
    private val controller = mockk<MediaController>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mocking Log
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        // Mocking Database static call using mockkObject
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getDatabase(any<android.content.Context>()) } returns database
        
        // Flow returns for DAOs
        every { favoriteDao.isFavorite(any()) } returns flowOf(false)
        every { bookmarkDao.getBookmarksForMedia(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `setPlaybackSpeed updates state and calls controller`() = runTest(testDispatcher) {
        // Given
        val viewModel = PlaybackViewModel(application)
        
        // Inyectamos el controlador manualmente usando reflexión ya que es privado 
        // y se inicializa de forma asíncrona normalmente.
        val controllerField: Field = PlaybackViewModel::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(viewModel, controller)

        val speed = 1.5f

        // When
        viewModel.setPlaybackSpeed(speed)

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(speed, state.playbackSpeed)
            cancelAndIgnoreRemainingEvents()
        }
        
        verify { controller.setPlaybackSpeed(speed) }
    }
    
    @Test
    fun `togglePlayPause calls pause when playing`() = runTest(testDispatcher) {
        // Given
        val viewModel = PlaybackViewModel(application)
        val controllerField: Field = PlaybackViewModel::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(viewModel, controller)
        
        every { controller.isPlaying } returns true

        // When
        viewModel.togglePlayPause()

        // Then
        verify { controller.pause() }
    }

    @Test
    fun `togglePlayPause calls play when not playing`() = runTest(testDispatcher) {
        // Given
        val viewModel = PlaybackViewModel(application)
        val controllerField: Field = PlaybackViewModel::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(viewModel, controller)
        
        every { controller.isPlaying } returns false

        // When
        viewModel.togglePlayPause()

        // Then
        verify { controller.play() }
    }
}
