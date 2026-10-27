package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import androidx.media3.session.MediaController
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.lang.reflect.Field

import android.util.Log
import kotlinx.coroutines.flow.flowOf
import io.mockk.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

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
        
        // Mock application filesDir
        every { application.filesDir } returns java.io.File(System.getProperty("java.io.tmpdir") ?: ".")

        // Flow returns for DAOs
        every { favoriteDao.isFavorite(any()) } returns flowOf(false)
        every { bookmarkDao.getBookmarksForMedia(any()) } returns flowOf(emptyList())
        every { cachedBookDao.getAllBooks() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `equalizer observes session assigned after player becomes ready`() = runTest(testDispatcher) {
        val app = mockk<com.raulburgosmurray.musicplayer.ApplicationClass>(relaxed = true)
        val sessions = MutableStateFlow(0)
        every { app.audioSessionIds } returns sessions
        every { app.audioSessionId } answers { sessions.value }
        val manager = mockk<com.raulburgosmurray.musicplayer.EqualizerManager>(relaxed = true)
        var available = false
        every { manager.isAvailable } answers { available }
        every { manager.attach(any()) } answers { available = firstArg<Int>() > 0 }
        every { controller.playbackState } returns Player.STATE_READY
        val viewModel = PlaybackViewModel(app)
        PlaybackViewModel::class.java.getDeclaredField("equalizerManager").apply {
            isAccessible = true
            set(viewModel, manager)
        }
        PlaybackViewModel::class.java.getDeclaredField("controller").apply {
            isAccessible = true
            set(viewModel, controller)
        }
        runCurrent()
        assertEquals(false, viewModel.uiState.value.eqAvailable)
        sessions.value = 42
        runCurrent()
        verify { manager.attach(42) }
        assertEquals(true, viewModel.uiState.value.eqAvailable)
        sessions.value = 0
        runCurrent()
        assertEquals(false, viewModel.uiState.value.eqAvailable)
        viewModel.viewModelScope.cancel()
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
        every { controller.currentMediaItem } returns MediaItem.Builder().setMediaId("new-book").build()

        // When
        viewModel.setPlaybackSpeed(speed)

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(speed, state.playbackSpeed)
            cancelAndIgnoreRemainingEvents()
        }
        
        verify { controller.setPlaybackSpeed(speed) }
        coVerify(timeout = 1_000) {
            progressDao.applyUpdate(
                "new-book",
                match { it.playbackSpeed == speed },
                any()
            )
        }
    }
    
    @Test
    fun `togglePlayPause calls pause when playing`() = runTest(testDispatcher) {
        // Given
        val viewModel = PlaybackViewModel(application)
        val controllerField: Field = PlaybackViewModel::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(viewModel, controller)
        
        every { controller.playWhenReady } returns true

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
        
        every { controller.playWhenReady } returns false

        // When
        viewModel.togglePlayPause()

        // Then
        verify { controller.play() }
    }

    @Test
    fun `togglePlayPause cancels playback while player is buffering`() = runTest(testDispatcher) {
        val viewModel = PlaybackViewModel(application)
        val controllerField = PlaybackViewModel::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        controllerField.set(viewModel, controller)
        every { controller.playWhenReady } returns true
        every { controller.isPlaying } returns false

        viewModel.togglePlayPause()

        verify { controller.pause() }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `local audio buffering timeout pauses playback and exposes recoverable error`() = runTest(testDispatcher) {
        val mediaItem = localMediaItem("damaged-book")
        val listener = slot<Player.Listener>()
        every { controller.addListener(capture(listener)) } just Runs
        every { controller.currentMediaItem } returns null
        every { controller.currentMediaItemIndex } returns 0
        every { controller.currentPosition } returns 123_000L
        every { controller.duration } returns 3_600_000L
        every { controller.playWhenReady } returns true
        every { controller.playbackState } returns Player.STATE_BUFFERING
        every { controller.playbackParameters } returns PlaybackParameters.DEFAULT

        val viewModel = PlaybackViewModel(application)
        injectControllerAndSetup(viewModel)
        every { controller.currentMediaItem } returns mediaItem

        listener.captured.onPlaybackStateChanged(Player.STATE_BUFFERING)
        advanceTimeBy(15_000L)
        runCurrent()

        verify { controller.pause() }
        assertEquals(false, viewModel.uiState.value.isBuffering)
        assertEquals("LOCAL_FILE_BUFFERING_TIMEOUT", viewModel.uiState.value.playbackError)
    }

    @Test
    fun `buffering watchdog is cancelled when playback leaves buffering`() = runTest(testDispatcher) {
        val mediaItem = localMediaItem("slow-book")
        val listener = slot<Player.Listener>()
        every { controller.addListener(capture(listener)) } just Runs
        every { controller.currentMediaItem } returns null
        every { controller.currentMediaItemIndex } returns 0
        every { controller.currentPosition } returns 123_000L
        every { controller.duration } returns 3_600_000L
        every { controller.playWhenReady } returns true
        every { controller.playbackState } returns Player.STATE_READY
        every { controller.playbackParameters } returns PlaybackParameters.DEFAULT

        val viewModel = PlaybackViewModel(application)
        injectControllerAndSetup(viewModel)
        every { controller.currentMediaItem } returns mediaItem

        listener.captured.onPlaybackStateChanged(Player.STATE_BUFFERING)
        every { controller.playWhenReady } returns false
        listener.captured.onPlaybackStateChanged(Player.STATE_IDLE)
        advanceTimeBy(15_000L)
        runCurrent()

        verify(exactly = 0) { controller.pause() }
        assertEquals(null, viewModel.uiState.value.playbackError)
    }

    @Test
    fun `history is isolated per book`() = runTest(testDispatcher) {
        val storage = mutableMapOf<String, String>()
        val prefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val editor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { prefs.edit() } returns editor
        every { prefs.getInt(any(), any()) } returns 100
        every { prefs.getString(any(), any()) } answers { storage[firstArg<String>()] }
        every { editor.putString(any(), any()) } answers {
            storage[firstArg<String>()] = secondArg<String>()
            editor
        }

        val viewModel = PlaybackViewModel(application)
        PlaybackViewModel::class.java.getDeclaredField("prefs").apply {
            isAccessible = true
            set(viewModel, prefs)
        }
        PlaybackViewModel::class.java.getDeclaredField("historyLimit").apply {
            isAccessible = true
            set(viewModel, 100)
        }

        val uiStateField = PlaybackViewModel::class.java.getDeclaredField("_uiState")
        uiStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val uiStateFlow = uiStateField.get(viewModel) as MutableStateFlow<PlaybackUiState>
        uiStateFlow.value = uiStateFlow.value.copy(
            currentMusicDetails = com.raulburgosmurray.musicplayer.Music(
                id = "book-a", title = "A", album = "A", artist = "A", path = "path/a", artUri = null
            )
        )

        // Log action directly via logAction for book-a
        val logActionMethod = PlaybackViewModel::class.java.getDeclaredMethod("logAction", String::class.java)
        logActionMethod.isAccessible = true
        logActionMethod.invoke(viewModel, "Action A")

        val loadHistoryMethod = PlaybackViewModel::class.java.getDeclaredMethod("loadHistoryForMedia", String::class.java)
        loadHistoryMethod.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val historyBookA = loadHistoryMethod.invoke(viewModel, "book-a") as List<com.raulburgosmurray.musicplayer.HistoryAction>
        assertEquals(1, historyBookA.size)
        assertEquals("Action A", historyBookA.first().label)

        @Suppress("UNCHECKED_CAST")
        val historyBookB = loadHistoryMethod.invoke(viewModel, "book-b") as List<*>
        assertEquals(0, historyBookB.size)
    }

    private fun injectControllerAndSetup(viewModel: PlaybackViewModel) {
        PlaybackViewModel::class.java.getDeclaredField("controller").apply {
            isAccessible = true
            set(viewModel, controller)
        }
        PlaybackViewModel::class.java.getDeclaredMethod("setupController").apply {
            isAccessible = true
            invoke(viewModel)
        }
    }

    private fun localMediaItem(mediaId: String): MediaItem {
        val uri = mockk<android.net.Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://$mediaId"
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(mediaId)
            .build()
    }
}
