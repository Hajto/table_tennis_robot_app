package com.tablebot.ui.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records cue invocations so the lead-in logic can be verified without real audio. */
private class FakeCue : StartCue {
    var ticks = 0
    var gos = 0
    var releases = 0
    override fun tick() { ticks++ }
    override fun go() { gos++ }
    override fun release() { releases++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StartCountdownTest {

    @Test fun `counts down N to 1 then clears and fires once`() = runTest {
        val cue = FakeCue()
        val published = mutableListOf<Int?>()
        var fired = 0

        runStartCountdown(10, cue, { published.add(it) }, { fired++ })

        assertEquals(listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1, null), published)
        assertEquals(1, fired)
        // Beep on each of the last 3 seconds; one distinct "go" tone at zero.
        assertEquals(3, cue.ticks)
        assertEquals(1, cue.gos)
    }

    @Test fun `short delay at or under three seconds beeps every second`() = runTest {
        val cue = FakeCue()
        val published = mutableListOf<Int?>()
        var fired = 0

        runStartCountdown(3, cue, { published.add(it) }, { fired++ })

        assertEquals(listOf(3, 2, 1, null), published)
        assertEquals(1, fired)
        assertEquals(3, cue.ticks)
        assertEquals(1, cue.gos)
    }

    @Test fun `zero duration fires immediately with no ticks or emissions`() = runTest {
        val cue = FakeCue()
        val published = mutableListOf<Int?>()
        var fired = 0

        runStartCountdown(0, cue, { published.add(it) }, { fired++ })

        assertTrue(published.isEmpty())
        assertEquals(1, fired)
        assertEquals(0, cue.ticks)
        assertEquals(0, cue.gos)
    }

    @Test fun `negative duration fires immediately`() = runTest {
        val cue = FakeCue()
        var fired = 0
        runStartCountdown(-3, cue, {}, { fired++ })
        assertEquals(1, fired)
        assertEquals(0, cue.ticks)
    }

    @Test fun `cancelling before zero stops emissions and never fires`() = runTest {
        val cue = FakeCue()
        val published = mutableListOf<Int?>()
        var fired = 0

        val job = launch {
            runStartCountdown(5, cue, { published.add(it) }, { fired++ })
        }
        advanceTimeBy(2500) // let 5, 4, 3 emit
        job.cancel()
        advanceUntilIdle()

        assertEquals(0, fired)
        assertFalse("no clear/null emission after cancel", published.contains(null))
        assertTrue("some seconds emitted before cancel", published.isNotEmpty())
        assertEquals(listOf(5, 4, 3), published)
    }
}
