package com.apexforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerTest {
    private var clock = 0L
    private val timer = RestTimer { clock }

    @Test fun countsDown() {
        timer.start(90)
        assertEquals(90_000L, timer.remainingMs())
        clock += 30_000
        assertEquals(60_000L, timer.remainingMs())
        assertFalse(timer.isFinished)
    }

    @Test fun finishesAndClampsAtZero() {
        timer.start(10)
        clock += 25_000
        assertEquals(0L, timer.remainingMs())
        assertTrue(timer.isFinished)
    }

    @Test fun pauseFreezesTimeAndResumeContinues() {
        timer.start(60)
        clock += 20_000
        timer.pause()
        clock += 100_000
        assertEquals(40_000L, timer.remainingMs())
        assertTrue(timer.isPaused)
        timer.resume()
        clock += 10_000
        assertEquals(30_000L, timer.remainingMs())
        assertFalse(timer.isPaused)
    }

    @Test fun resetRestartsFromOriginalDuration() {
        timer.start(60)
        clock += 45_000
        timer.reset()
        assertEquals(60_000L, timer.remainingMs())
    }

    @Test fun adjustAddsAndRemovesTimeButNeverGoesNegative() {
        timer.start(60)
        timer.adjust(30)
        assertEquals(90_000L, timer.remainingMs())
        timer.adjust(-200)
        assertEquals(0L, timer.remainingMs())
        assertTrue(timer.totalMs >= 1000L)
    }

    @Test fun adjustWhilePausedChangesPausedRemaining() {
        timer.start(60)
        clock += 10_000
        timer.pause()
        timer.adjust(15)
        assertEquals(65_000L, timer.remainingMs())
    }

    @Test fun stopDeactivates() {
        timer.start(30)
        timer.stop()
        assertFalse(timer.isActive)
        assertEquals(0L, timer.remainingMs())
    }

    @Test fun inactiveTimerIgnoresControls() {
        timer.pause(); timer.reset(); timer.adjust(10)
        assertFalse(timer.isActive)
        assertEquals(0L, timer.remainingMs())
    }
}
