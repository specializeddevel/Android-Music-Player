package com.raulburgosmurray.musicplayer

import android.media.audiofx.Equalizer
import android.util.Log
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EqualizerManagerTest {
    private val effect = mockk<Equalizer>(relaxed = true)
    private val factory = mockk<(Int) -> Equalizer>()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any<Throwable>()) } returns 0
        every { factory(any()) } returns effect
        every { effect.numberOfBands } returns 6
        every { effect.bandLevelRange } returns shortArrayOf(-1_500, 1_500)
    }

    @After fun cleanup() = unmockkAll()

    @Test fun `unset session never creates a global equalizer`() {
        val manager = EqualizerManager(factory)
        manager.attach(0)
        manager.attach(-1)
        verify(exactly = 0) { factory(any()) }
        assertEquals(false, manager.isAvailable)
    }

    @Test fun `valid session enables effect and attaches only once`() {
        val manager = EqualizerManager(factory)
        manager.attach(42)
        manager.attach(42)
        verify(exactly = 1) { factory(42) }
        verify(exactly = 1) { effect.setEnabled(true) }
        assertEquals(true, manager.isAvailable)
    }

    @Test fun `invalidated session releases the old effect`() {
        val manager = EqualizerManager(factory)
        manager.attach(42)
        manager.attach(0)
        verify { effect.release() }
        assertEquals(false, manager.isAvailable)
    }

    @Test fun `failed creation can retry on same session`() {
        every { factory(42) } throws IllegalStateException("Unavailable") andThen effect
        val manager = EqualizerManager(factory)
        manager.attach(42)
        assertEquals(false, manager.isAvailable)
        manager.attach(42)
        assertEquals(true, manager.isAvailable)
    }

    @Test fun `failed enabling releases effect instead of reporting available`() {
        every { effect.setEnabled(true) } returns -1
        val manager = EqualizerManager(factory)
        manager.attach(42)
        verify { effect.release() }
        assertEquals(false, manager.isAvailable)
    }

    @Test fun `session change retains selected preset`() {
        val manager = EqualizerManager(factory)
        manager.applyPreset(EqPreset.BASS_CUT)
        manager.attach(42)
        manager.attach(43)
        verify { effect.release(); factory(43) }
        assertEquals(EqPreset.BASS_CUT, manager.currentPreset)
    }

    @Test fun `voice boost raises the intended mid bands`() {
        val manager = EqualizerManager(factory)
        manager.attach(42)
        clearMocks(effect, answers = false)

        manager.applyPreset(EqPreset.VOICE_BOOST)

        verify { effect.setBandLevel(1, 100) }
        verify { effect.setBandLevel(2, 150) }
        verify { effect.setBandLevel(3, 200) }
        verify { effect.setBandLevel(4, 150) }
    }

    @Test fun `clarity clamps boosts to the equalizer supported range`() {
        every { effect.bandLevelRange } returns shortArrayOf(-150, 150)
        val manager = EqualizerManager(factory)
        manager.attach(42)
        clearMocks(effect, answers = false)

        manager.applyPreset(EqPreset.CLARITY)

        verify { effect.setBandLevel(3, 150) }
        verify { effect.setBandLevel(4, 150) }
        verify { effect.setBandLevel(5, 150) }
    }

    @Test fun `preset ignores bands that do not exist on the device`() {
        every { effect.numberOfBands } returns 5
        val manager = EqualizerManager(factory)
        manager.attach(42)
        clearMocks(effect, answers = false)

        manager.applyPreset(EqPreset.CLARITY)

        verify(exactly = 0) { effect.setBandLevel(5, any()) }
    }
}
