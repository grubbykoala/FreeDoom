package com.freedoom.app.usage

import com.freedoom.app.usage.UsageCalculator.ACTIVITY_PAUSED
import com.freedoom.app.usage.UsageCalculator.ACTIVITY_RESUMED
import com.freedoom.app.usage.UsageCalculator.KEYGUARD_HIDDEN
import com.freedoom.app.usage.UsageCalculator.SCREEN_NON_INTERACTIVE
import org.junit.Assert.assertEquals
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
    fun appOpenAtMidnightAndStillOpenNow() {
        val events = listOf(
            ev(5_000, ACTIVITY_PAUSED, "ig", "A"),
            ev(9_000, ACTIVITY_RESUMED, "yt", "A"),
        )
        val r = UsageCalculator.compute(events, 0, 15_000, emptySet(), true)
        assertEquals(listOf("yt" to 6_000L, "ig" to 5_000L), r.perApp)
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
        assertEquals(null, r.unlocks)
    }
}
