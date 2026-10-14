package com.freedoom.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortVideoTrackerTest {
    private val pkg = "com.instagram.android"
    private val started = mutableListOf<Long>()
    private val ended = mutableListOf<Pair<Long, Long>>()
    private val tracker = ShortVideoTracker(
        gestureGapMs = 400,
        exitGraceMs = 1_500,
        maxSilentGapMs = 60_000,
        listener = object : ShortVideoTracker.Listener {
            override fun onVideoStarted(packageName: String, at: Long, reason: String) { started += at }
            override fun onVideoEnded(packageName: String, startedAt: Long, endedAt: Long) { ended += startedAt to endedAt }
        },
    )

    @Test
    fun enteringCountsTheVisibleVideo() {
        tracker.onScreenState(pkg, true, 1_000)
        tracker.onScreenState(pkg, true, 2_000)
        assertEquals(1, started.size)
    }

    @Test
    fun indexedSwipeCountsOncePerSettledIndex() {
        tracker.onScreenState(pkg, true, 0)
        // Un gesto: eventos a mitad (0→1) y luego asentado en 1.
        tracker.onPagerScrolled(pkg, 0, 1, 1_000)
        tracker.onPagerScrolled(pkg, 0, 1, 1_050)
        tracker.onPagerScrolled(pkg, 1, 1, 1_100)
        tracker.onPagerScrolled(pkg, 1, 1, 1_120)
        // Deslizamiento muy rápido: 1→2→3 sin pausa.
        tracker.onPagerScrolled(pkg, 2, 2, 1_200)
        tracker.onPagerScrolled(pkg, 3, 3, 1_250)
        assertEquals(4, started.size)
        assertEquals(listOf(0L to 1_100L, 1_100L to 1_200L, 1_200L to 1_250L), ended)
    }

    @Test
    fun partialSwipeThatBouncesBackDoesNotCount() {
        tracker.onScreenState(pkg, true, 0)
        tracker.onPagerScrolled(pkg, 4, 5, 1_000)
        tracker.onPagerScrolled(pkg, 4, 4, 1_200)
        assertEquals(1, started.size)
    }

    @Test
    fun withoutIndicesEachBurstIsOneVideo() {
        tracker.onScreenState(pkg, true, 0)
        // Primer gesto: ráfaga de eventos.
        for (t in 1_000L..1_300L step 50) tracker.onPagerScrolled(pkg, -1, -1, t)
        // Segundo gesto tras una pausa.
        for (t in 2_000L..2_200L step 50) tracker.onPagerScrolled(pkg, -1, -1, t)
        assertEquals(3, started.size)
    }

    @Test
    fun exitNeedsGracePeriod() {
        tracker.onScreenState(pkg, true, 0)
        tracker.onScreenState(pkg, false, 10_000)
        tracker.onScreenState(pkg, true, 10_500) // parpadeo: sigue dentro
        tracker.onScreenState(pkg, false, 20_000)
        tracker.onScreenState(pkg, false, 21_600)
        assertEquals(1, started.size)
        assertEquals(listOf(0L to 20_000L), ended)
        tracker.onScreenState(pkg, true, 30_000)
        assertEquals(2, started.size)
    }

    @Test
    fun longSilenceClosesAtLastEvent() {
        tracker.onAnyEvent(pkg, 0)
        tracker.onScreenState(pkg, true, 0)
        tracker.onAnyEvent(pkg, 5_000)
        tracker.onAnyEvent(pkg, 500_000)
        assertEquals(listOf(0L to 5_000L), ended)
    }
}
