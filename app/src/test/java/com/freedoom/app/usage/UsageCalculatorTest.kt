package com.freedoom.app.usage

import com.freedoom.app.usage.UsageCalculator.ACTIVITY_PAUSED
import com.freedoom.app.usage.UsageCalculator.ACTIVITY_RESUMED
import com.freedoom.app.usage.UsageCalculator.ACTIVITY_STOPPED
import com.freedoom.app.usage.UsageCalculator.DEVICE_SHUTDOWN
import com.freedoom.app.usage.UsageCalculator.DEVICE_STARTUP
import com.freedoom.app.usage.UsageCalculator.KEYGUARD_HIDDEN
import com.freedoom.app.usage.UsageCalculator.KEYGUARD_SHOWN
import com.freedoom.app.usage.UsageCalculator.SCREEN_INTERACTIVE
import com.freedoom.app.usage.UsageCalculator.SCREEN_NON_INTERACTIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCalculatorTest {
    private fun ev(t: Long, type: Int, pkg: String = "", cls: String? = null) = RawUsageEvent(t, type, pkg, cls)

    @Test
    fun sumsForegroundTimeAndUnlocks() {
        val events = listOf(
            ev(1_000, KEYGUARD_HIDDEN),
            ev(2_000, ACTIVITY_RESUMED, "ig", "Main"),
            ev(62_000, ACTIVITY_PAUSED, "ig", "Main"),
            ev(62_000, ACTIVITY_RESUMED, "yt", "Main"),
            ev(92_000, ACTIVITY_PAUSED, "yt", "Main"),
            ev(100_000, KEYGUARD_HIDDEN),
        )
        val r = UsageCalculator.compute(events, 0, 200_000, emptySet(), unlocksSupported = true)
        assertEquals(listOf("ig" to 60_000L, "yt" to 30_000L), r.perApp)
        assertEquals(90_000L, r.totalMs)
        assertEquals(2, r.unlocks)
        assertFalse(r.unlocksAreScreenOns)
    }

    @Test
    fun switchingActivitiesInsideAnAppIsContinuous() {
        val events = listOf(
            ev(0, ACTIVITY_RESUMED, "ig", "A"),
            ev(10_000, ACTIVITY_RESUMED, "ig", "B"),
            ev(10_100, ACTIVITY_PAUSED, "ig", "A"),
            ev(20_000, ACTIVITY_PAUSED, "ig", "B"),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), true)
        assertEquals(listOf("ig" to 20_000L), r.perApp)
    }

    @Test
    fun appOpenSinceBeforeMidnightCountsFromDayStart() {
        val events = listOf(
            ev(-60_000, ACTIVITY_RESUMED, "ig", "A"),
            ev(5_000, ACTIVITY_PAUSED, "ig", "A"),
            ev(9_000, ACTIVITY_RESUMED, "yt", "A"),
        )
        val r = UsageCalculator.compute(events, 0, 15_000, emptySet(), true)
        assertEquals(listOf("yt" to 6_000L, "ig" to 5_000L), r.perApp)
    }

    @Test
    fun resumedBeforeMidnightAndStillOpenCountsUntilNow() {
        val events = listOf(ev(-60_000, ACTIVITY_RESUMED, "ig", "A"))
        val r = UsageCalculator.compute(events, 0, 30_000, emptySet(), true)
        assertEquals(listOf("ig" to 30_000L), r.perApp)
        assertEquals(30_000L, r.totalMs)
    }

    @Test
    fun orphanPausedAddsNothing() {
        // Ventana PiP de Maps que vuelve a verse al desbloquear: PAUSED sin RESUMED en todo el periodo.
        val events = listOf(ev(8 * 3_600_000L, ACTIVITY_PAUSED, "maps", "Nav"))
        val r = UsageCalculator.compute(events, 0, 9 * 3_600_000L, emptySet(), true)
        assertEquals(emptyList<Pair<String, Long>>(), r.perApp)
        assertEquals(0L, r.totalMs)
    }

    @Test
    fun unlocksBeforeDayStartDoNotCount() {
        val events = listOf(
            ev(-5_000, KEYGUARD_HIDDEN),
            ev(5_000, KEYGUARD_HIDDEN),
        )
        val r = UsageCalculator.compute(events, 0, 10_000, emptySet(), true)
        assertEquals(1, r.unlocks)
    }

    @Test
    fun deviceStartupDiscardsWhatWasOpenBefore() {
        val events = listOf(
            ev(-60_000, ACTIVITY_RESUMED, "ig", "A"),
            ev(10_000, DEVICE_STARTUP),
            ev(20_000, SCREEN_NON_INTERACTIVE),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), true)
        assertEquals(emptyList<Pair<String, Long>>(), r.perApp)
    }

    @Test
    fun screenOffClosesAndLaunchersAreExcluded() {
        val events = listOf(
            ev(0, ACTIVITY_RESUMED, "launcher", "Home"),
            ev(5_000, ACTIVITY_PAUSED, "launcher", "Home"),
            ev(5_000, ACTIVITY_RESUMED, "ig", "A"),
            ev(15_000, SCREEN_NON_INTERACTIVE),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, setOf("launcher"), unlocksSupported = false)
        assertEquals(listOf("ig" to 10_000L), r.perApp)
        assertEquals(10_000L, r.totalMs)
        assertEquals(null, r.unlocks)
    }

    @Test
    fun shutdownClosesOpenApps() {
        val events = listOf(
            ev(0, ACTIVITY_RESUMED, "ig", "A"),
            ev(7_000, DEVICE_SHUTDOWN),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), true)
        assertEquals(listOf("ig" to 7_000L), r.perApp)
    }

    @Test
    fun splitScreenDoesNotDoubleTheTotal() {
        val events = listOf(
            ev(0, ACTIVITY_RESUMED, "ig", "A"),
            ev(10_000, ACTIVITY_RESUMED, "yt", "A"),
            ev(40_000, ACTIVITY_PAUSED, "yt", "A"),
            ev(60_000, ACTIVITY_PAUSED, "ig", "A"),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), true)
        assertEquals(listOf("ig" to 60_000L, "yt" to 30_000L), r.perApp)
        assertEquals(60_000L, r.totalMs)
    }

    @Test
    fun totalOnlyCountsAppsShownInTheList() {
        val events = listOf(
            ev(0, ACTIVITY_RESUMED, "launcher", "Home"),
            ev(10_000, ACTIVITY_PAUSED, "launcher", "Home"),
            ev(20_000, ACTIVITY_RESUMED, "tiny", "A"),
            ev(20_500, ACTIVITY_PAUSED, "tiny", "A"),
            ev(30_000, ACTIVITY_RESUMED, "ig", "A"),
            ev(35_000, ACTIVITY_PAUSED, "ig", "A"),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, setOf("launcher"), true)
        assertEquals(listOf("ig" to 5_000L), r.perApp)
        assertEquals(5_000L, r.totalMs)
    }

    @Test
    fun crashedAppIsClosedByStopped() {
        // Instagram se cuelga en pantalla: no hay PAUSED, solo STOPPED (el sistema guarda así el DESTROYED).
        val events = listOf(
            ev(0, ACTIVITY_RESUMED, "ig", "A"),
            ev(10_000, ACTIVITY_RESUMED, "wa", "M"),
            ev(10_000, ACTIVITY_STOPPED, "ig", "A"),
            ev(40_000, SCREEN_NON_INTERACTIVE),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), true)
        assertEquals(listOf("wa" to 30_000L, "ig" to 10_000L), r.perApp)
        assertEquals(40_000L, r.totalMs)
    }

    @Test
    fun stoppedAfterPausedChangesNothing() {
        val events = listOf(
            ev(0, ACTIVITY_RESUMED, "ig", "A"),
            ev(10_000, ACTIVITY_PAUSED, "ig", "A"),
            ev(10_500, ACTIVITY_STOPPED, "ig", "A"),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), true)
        assertEquals(listOf("ig" to 10_000L), r.perApp)
    }

    @Test
    fun stoppedOfPreviousInstanceOfSameClassKeepsAppOpen() {
        // Instagram abre otra ModalActivity encima de la primera: PAUSED A, RESUMED A, STOPPED A.
        val events = listOf(
            ev(0, ACTIVITY_RESUMED, "ig", "A"),
            ev(10_000, ACTIVITY_PAUSED, "ig", "A"),
            ev(10_000, ACTIVITY_RESUMED, "ig", "A"),
            ev(12_000, ACTIVITY_STOPPED, "ig", "A"),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), true)
        assertEquals(listOf("ig" to 100_000L), r.perApp)
    }

    @Test
    fun withoutLockScreenUnlocksAreScreenOns() {
        val events = listOf(
            ev(1_000, SCREEN_INTERACTIVE),
            ev(5_000, SCREEN_NON_INTERACTIVE),
            ev(50_000, SCREEN_INTERACTIVE),
            ev(60_000, SCREEN_NON_INTERACTIVE),
            ev(90_000, SCREEN_INTERACTIVE),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), true)
        assertEquals(3, r.unlocks)
        assertTrue(r.unlocksAreScreenOns)
    }

    @Test
    fun lookingAtTheLockScreenIsNotAnUnlock() {
        val events = listOf(
            ev(1_000, SCREEN_INTERACTIVE),
            ev(1_000, KEYGUARD_SHOWN),
            ev(5_000, SCREEN_NON_INTERACTIVE),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), true)
        assertEquals(0, r.unlocks)
        assertFalse(r.unlocksAreScreenOns)
    }

    @Test
    fun keyguardSeenBeforeDayStartMeansThereIsALockScreen() {
        // Encendido justo después de medianoche, dentro del plazo antes de que salte el bloqueo.
        val events = listOf(
            ev(-3_600_000L, KEYGUARD_SHOWN),
            ev(-3_000_000L, KEYGUARD_HIDDEN),
            ev(5_000, SCREEN_INTERACTIVE),
        )
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), true)
        assertEquals(0, r.unlocks)
        assertFalse(r.unlocksAreScreenOns)
    }

    @Test
    fun screenOnsAreNotUsedWhenUnlocksAreUnsupported() {
        val events = listOf(ev(1_000, SCREEN_INTERACTIVE))
        val r = UsageCalculator.compute(events, 0, 100_000, emptySet(), unlocksSupported = false)
        assertNull(r.unlocks)
        assertFalse(r.unlocksAreScreenOns)
    }

    @Test
    fun foregroundMsIsClippedToTheInterval() {
        val events = listOf(
            ev(1_000, ACTIVITY_RESUMED, "ig", "A"),
            ev(5_000, ACTIVITY_PAUSED, "ig", "A"),
            ev(5_000, ACTIVITY_RESUMED, "wa", "M"),
            ev(9_000, ACTIVITY_PAUSED, "wa", "M"),
            ev(10_000, ACTIVITY_RESUMED, "ig", "A"),
        )
        assertEquals(14_000L, UsageCalculator.foregroundMs(events, "ig", 0, 20_000))
        assertEquals(4_000L, UsageCalculator.foregroundMs(events, "ig", 3_000, 12_000))
        assertEquals(4_000L, UsageCalculator.foregroundMs(events, "wa", 0, 20_000))
        assertEquals(0L, UsageCalculator.foregroundMs(events, "yt", 0, 20_000))
        assertEquals(0L, UsageCalculator.foregroundMs(events, "ig", 20_000, 20_000))
    }

    @Test
    fun foregroundMsCountsAppOpenBeforeFromAndStopsAtScreenOff() {
        val events = listOf(
            ev(-60_000, ACTIVITY_RESUMED, "ig", "A"),
            ev(30_000, SCREEN_NON_INTERACTIVE),
            ev(40_000, ACTIVITY_PAUSED, "ig", "A"),
        )
        assertEquals(30_000L, UsageCalculator.foregroundMs(events, "ig", 0, 100_000))
    }

    @Test
    fun foregroundMsIgnoresOrphanPausedAndWhatWasOpenBeforeStartup() {
        val events = listOf(
            ev(5_000, ACTIVITY_PAUSED, "ig", "A"),
            ev(10_000, ACTIVITY_RESUMED, "ig", "A"),
            ev(20_000, DEVICE_STARTUP),
        )
        assertEquals(0L, UsageCalculator.foregroundMs(events, "ig", 0, 100_000))
    }

    @Test
    fun lastResumeAfterReturnsLatestResumeOfThatApp() {
        val events = listOf(
            ev(1_000, ACTIVITY_RESUMED, "ig", "A"),
            ev(2_000, ACTIVITY_RESUMED, "wa", "M"),
            ev(3_000, ACTIVITY_RESUMED, "ig", "B"),
            ev(4_000, ACTIVITY_PAUSED, "ig", "B"),
        )
        assertEquals(3_000L, UsageCalculator.lastResumeAfter(events, "ig", 0))
        assertEquals(3_000L, UsageCalculator.lastResumeAfter(events, "ig", 2_500))
        assertNull(UsageCalculator.lastResumeAfter(events, "ig", 3_000))
        assertNull(UsageCalculator.lastResumeAfter(events, "yt", 0))
        assertNull(UsageCalculator.lastResumeAfter(emptyList(), "ig", 0))
    }
}
