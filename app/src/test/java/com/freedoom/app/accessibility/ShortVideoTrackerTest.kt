package com.freedoom.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortVideoTrackerTest {
    private val pkg = "com.instagram.android"
    private val started = mutableListOf<Long>()
    private val ended = mutableListOf<Pair<Long, Long>>()
    private val resumed = mutableListOf<Long>()
    private val tracker = ShortVideoTracker(
        gestureGapMs = 400,
        exitGraceMs = 1_500,
        maxSilentGapMs = 60_000,
        resumeWindowMs = 180_000,
        openSettleMs = 400,
        listener = object : ShortVideoTracker.Listener {
            override fun onVideoStarted(packageName: String, at: Long, reason: String) { started += at }
            override fun onVideoEnded(packageName: String, startedAt: Long, endedAt: Long) { ended += startedAt to endedAt }
            override fun onResumed(packageName: String, at: Long, reason: String) { resumed += at }
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
        // Volver poco después es el mismo reel: reanudación, no cuenta otra vez.
        tracker.onScreenState(pkg, true, 30_000)
        assertEquals(1, started.size)
        assertEquals(listOf(30_000L), resumed)
    }

    @Test
    fun longSilenceClosesAtLastEvent() {
        tracker.onAnyEvent(pkg, 0)
        tracker.onScreenState(pkg, true, 0)
        tracker.onAnyEvent(pkg, 5_000)
        tracker.onAnyEvent(pkg, 500_000)
        assertEquals(listOf(0L to 5_000L), ended)
    }

    @Test
    fun leavingForAnotherAppClosesAtExitTime() {
        tracker.onAnyEvent(pkg, 0)
        tracker.onScreenState(pkg, true, 0)
        // El vigilante ve otra app delante y, pasada la gracia, cierra en la hora de salida.
        tracker.onScreenState(pkg, false, 6_000)
        tracker.onScreenState(pkg, false, 7_600)
        // Media hora después se apaga la pantalla: el reel no se alarga.
        tracker.stop(1_800_000)
        assertEquals(listOf(0L to 6_000L), ended)
    }

    @Test
    fun stopClosesAtLastSeenMoment() {
        tracker.onAnyEvent(pkg, 0)
        tracker.onScreenState(pkg, true, 0)
        tracker.onScreenState(pkg, true, 4_000) // el vigilante sigue viendo el visor
        tracker.stop(9_000)
        assertEquals(listOf(0L to 4_000L), ended)
    }

    @Test
    fun screenOffAndBackDoesNotRecount() {
        tracker.onScreenState(pkg, true, 0)
        tracker.stop(5_000)
        tracker.onScreenState(pkg, true, 10_000)
        assertEquals(1, started.size)
        assertEquals(listOf(10_000L), resumed)
        // El siguiente deslizamiento sí es un vídeo nuevo.
        tracker.onPagerScrolled(pkg, -1, -1, 11_000)
        assertEquals(listOf(0L, 11_000L), started)
    }

    @Test
    fun reenterAfterResumeWindowCounts() {
        tracker.onScreenState(pkg, true, 0)
        tracker.onScreenState(pkg, true, 5_000)
        tracker.stop(5_000)
        tracker.onScreenState(pkg, true, 5_000 + 180_001)
        assertEquals(2, started.size)
        assertEquals(emptyList<Long>(), resumed)
    }

    @Test
    fun reenterOnDifferentIndexCounts() {
        tracker.onScreenState(pkg, true, 0)
        tracker.onPagerScrolled(pkg, 3, 3, 1_000) // referencia: índice 3
        tracker.stop(5_000)
        tracker.onScreenState(pkg, true, 10_000, pageIndex = 7)
        assertEquals(2, started.size)
        tracker.stop(12_000)
        tracker.onScreenState(pkg, true, 15_000, pageIndex = 7) // mismo índice: reanudación
        assertEquals(2, started.size)
    }

    @Test
    fun swipeAfterResumeCountsNewIndex() {
        tracker.onScreenState(pkg, true, 0)
        tracker.onPagerScrolled(pkg, 3, 3, 1_000)
        tracker.stop(5_000)
        tracker.onScreenState(pkg, true, 10_000) // reanudación: recupera el índice 3
        tracker.onPagerScrolled(pkg, 3, 3, 10_100) // el pager repite su posición: no cuenta
        tracker.onPagerScrolled(pkg, 3, 4, 12_000)
        tracker.onPagerScrolled(pkg, 4, 4, 12_100)
        assertEquals(listOf(0L, 12_100L), started)
    }

    @Test
    fun openingAnotherReelRightAfterLeavingCounts() {
        tracker.onScreenState(pkg, true, 0)
        tracker.onPagerScrolled(pkg, 12, 12, 1_000)
        tracker.stop(5_000)
        // Un minuto después abre un reel desde un mensaje: el pager se coloca en otro índice.
        tracker.onScreenState(pkg, true, 60_000)
        tracker.onPagerScrolled(pkg, 10, 10, 60_020)
        tracker.onPagerScrolled(pkg, 10, 11, 60_036)
        tracker.onPagerScrolled(pkg, 11, 11, 60_150)
        assertEquals(listOf(0L, 60_020L), started)
    }

    @Test
    fun pagerRepositioningRightAfterOpeningCountsOnce() {
        tracker.onScreenState(pkg, true, 0)
        tracker.onPagerScrolled(pkg, 10, 10, 20)
        tracker.onPagerScrolled(pkg, 10, 11, 36)
        tracker.onPagerScrolled(pkg, 11, 11, 150)
        assertEquals(1, started.size)
        tracker.onPagerScrolled(pkg, 12, 12, 2_000)
        assertEquals(2, started.size)
    }

    @Test
    fun entryByUnindexedScrollGetsDistinctStartTimes() {
        tracker.onPagerScrolled(pkg, -1, -1, 5_000)
        assertEquals(listOf(5_000L, 5_001L), started)
        assertEquals(listOf(5_000L to 5_000L), ended)
    }

    @Test
    fun videosInTheSameMillisecondGetDistinctStartTimes() {
        tracker.onScreenState(pkg, true, 0)
        // Fuera de la ventana de apertura, tres índices procesados en el mismo milisegundo.
        tracker.onPagerScrolled(pkg, 1, 1, 1_000)
        tracker.onPagerScrolled(pkg, 2, 2, 1_000)
        tracker.onPagerScrolled(pkg, 3, 3, 1_000)
        assertEquals(listOf(0L, 1_000L, 1_001L), started)
    }

    @Test
    fun clockGoingBackDoesNotChainStartTimes() {
        tracker.onScreenState(pkg, true, 100_000)
        tracker.onPagerScrolled(pkg, 1, 1, 101_000)
        tracker.onPagerScrolled(pkg, 2, 2, 102_000)
        // El reloj retrocede una hora: se sigue contando y con la hora nueva.
        tracker.onPagerScrolled(pkg, 3, 3, 102_000 - 3_600_000)
        assertEquals(listOf(100_000L, 102_000L, 102_000L - 3_600_000L), started)
    }

    @Test
    fun anotherViewerAtSameIndexCounts() {
        // Tres reels abiertos desde un chat: cada uno es un visor nuevo que empieza en el índice 0.
        tracker.onScreenState(pkg, true, 0, viewer = 1)
        tracker.onPagerScrolled(pkg, 0, 0, 1_000, viewer = 1)
        tracker.onScreenState(pkg, false, 15_000)
        tracker.onScreenState(pkg, false, 16_600)
        tracker.onScreenState(pkg, true, 35_000, viewer = 2)
        tracker.onPagerScrolled(pkg, 0, 0, 35_100, viewer = 2)
        tracker.onScreenState(pkg, false, 50_000)
        tracker.onScreenState(pkg, false, 51_600)
        tracker.onScreenState(pkg, true, 70_000, viewer = 3)
        assertEquals(listOf(0L, 35_000L, 70_000L), started)
    }

    @Test
    fun sameViewerAfterScreenOffResumes() {
        tracker.onScreenState(pkg, true, 0, viewer = 1)
        tracker.onPagerScrolled(pkg, 0, 0, 1_000, viewer = 1)
        tracker.stop(5_000)
        tracker.onScreenState(pkg, true, 30_000, viewer = 1)
        assertEquals(listOf(0L), started)
        assertEquals(listOf(30_000L), resumed)
    }

    @Test
    fun partialSwipeRightAfterResumeDoesNotCount() {
        tracker.onScreenState(pkg, true, 0)
        tracker.onPagerScrolled(pkg, 5, 5, 1_000)
        tracker.stop(5_000)
        tracker.onScreenState(pkg, true, 10_000)
        tracker.onPagerScrolled(pkg, 5, 6, 10_100)
        tracker.onPagerScrolled(pkg, 5, 5, 10_300)
        assertEquals(listOf(0L), started)
    }

    @Test
    fun fullSwipeRightAfterResumeCountsOnce() {
        tracker.onScreenState(pkg, true, 0)
        tracker.onPagerScrolled(pkg, 5, 5, 1_000)
        tracker.stop(5_000)
        tracker.onScreenState(pkg, true, 10_000)
        tracker.onPagerScrolled(pkg, 5, 6, 10_100)
        tracker.onPagerScrolled(pkg, 6, 6, 10_300)
        tracker.onPagerScrolled(pkg, 6, 6, 11_000)
        assertEquals(listOf(0L, 10_300L), started)
    }

    @Test
    fun newViewerBeforeExitGraceCountsAsNewReel() {
        // Reel 1 de un chat, atrás y reel 2 en menos de lo que tarda en confirmarse la salida.
        tracker.onScreenState(pkg, true, 0, viewer = 1)
        tracker.onPagerScrolled(pkg, 0, 0, 20, viewer = 1)
        tracker.onScreenState(pkg, false, 10_500)
        tracker.onScreenState(pkg, false, 11_500) // aún dentro de la gracia
        tracker.onScreenState(pkg, true, 11_800, viewer = 2)
        tracker.onPagerScrolled(pkg, 0, 0, 11_820, viewer = 2)
        assertEquals(listOf(0L, 11_800L), started)
        assertEquals(listOf(0L to 10_500L), ended)
    }

    @Test
    fun viewerValueFlickerWithoutLeavingDoesNotCount() {
        tracker.onScreenState(pkg, true, 0, viewer = 1)
        tracker.onScreenState(pkg, true, 1_000, viewer = 2)
        tracker.onScreenState(pkg, true, 2_000, viewer = 1)
        assertEquals(listOf(0L), started)
    }

    @Test
    fun backToReelsTabAfterAnotherViewerResumes() {
        // Reels (visor 1) → perfil → reel del perfil (visor 2) → perfil → Reels otra vez.
        tracker.onScreenState(pkg, true, 0, viewer = 1)
        tracker.onPagerScrolled(pkg, 5, 5, 1_000, viewer = 1)
        tracker.onScreenState(pkg, false, 10_000)
        tracker.onScreenState(pkg, false, 11_600)
        tracker.onScreenState(pkg, true, 20_000, viewer = 2)
        tracker.onScreenState(pkg, false, 30_000)
        tracker.onScreenState(pkg, false, 31_600)
        tracker.onScreenState(pkg, true, 40_000, viewer = 1)
        assertEquals(listOf(0L, 20_000L), started)
    }

    @Test
    fun firstSwipeAfterResumeWithoutKnownIndexCounts() {
        tracker.onScreenState(pkg, true, 0)
        tracker.stop(5_000)
        tracker.onScreenState(pkg, true, 10_000)
        tracker.onPagerScrolled(pkg, 0, 1, 10_100)
        tracker.onPagerScrolled(pkg, 1, 1, 10_300)
        assertEquals(listOf(0L, 10_300L), started)
    }

    @Test
    fun seededExitAfterProcessRestartResumes() {
        tracker.seedExit(pkg, viewer = 7, at = 1_000)
        tracker.onScreenState(pkg, true, 20_000, viewer = 7)
        assertEquals(emptyList<Long>(), started)
        assertEquals(listOf(20_000L), resumed)
    }

    @Test
    fun forgetExitsMakesTheNextEntryCount() {
        tracker.onScreenState(pkg, true, 0, viewer = 1)
        tracker.stop(5_000)
        tracker.forgetExits()
        tracker.onScreenState(pkg, true, 30_000, viewer = 1)
        assertEquals(listOf(0L, 30_000L), started)
    }
}
