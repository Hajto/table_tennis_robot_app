package com.tablebot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pure-JVM coverage for the delayed-start prefs. Persistence itself is backed by Android
 * SharedPreferences (exercised on-device), so here we test the parts that stand alone: the
 * inline StateFlow defaults and the clamp used by [AppPrefs.setStartDelaySec] / init.
 */
class StartDelayPrefsTest {

    @Test fun `defaults are immediate and ten seconds`() {
        assertFalse("start delay defaults to immediate", AppPrefs.startDelayed.value)
        assertEquals(10, AppPrefs.DEFAULT_START_DELAY_SEC)
        assertEquals(AppPrefs.DEFAULT_START_DELAY_SEC, AppPrefs.startDelaySec.value)
    }

    @Test fun `clamp keeps values inside the 3 to 60 range`() {
        assertEquals(3, AppPrefs.MIN_START_DELAY_SEC)
        assertEquals(60, AppPrefs.MAX_START_DELAY_SEC)
        // in range → unchanged
        assertEquals(10, AppPrefs.clampStartDelaySec(10))
        assertEquals(3, AppPrefs.clampStartDelaySec(3))
        assertEquals(60, AppPrefs.clampStartDelaySec(60))
        // below → floored
        assertEquals(3, AppPrefs.clampStartDelaySec(2))
        assertEquals(3, AppPrefs.clampStartDelaySec(0))
        assertEquals(3, AppPrefs.clampStartDelaySec(-5))
        // above → capped
        assertEquals(60, AppPrefs.clampStartDelaySec(61))
        assertEquals(60, AppPrefs.clampStartDelaySec(600))
    }
}
