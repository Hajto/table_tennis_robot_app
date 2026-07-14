package com.tablebot.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Audio cue for the delayed-start lead-in. Abstracted behind an interface so the countdown logic
 * can be unit-tested without touching real audio hardware.
 *
 *  - [tick] — a short beep, played on each of the last 5 seconds.
 *  - [go]   — a distinct "go" tone, played once when the countdown reaches zero.
 *  - [release] — free any underlying resources; safe to call repeatedly.
 */
interface StartCue {
    fun tick()
    fun go()
    fun release()
}

/**
 * Real [StartCue] backed by Android's [ToneGenerator]. All audio calls are wrapped in try/catch:
 * a failure to obtain the generator (silent mode, resource exhaustion, …) must never block the
 * countdown or the drill — it just means no sound.
 */
class AndroidStartCue : StartCue {
    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    } catch (e: RuntimeException) {
        Log.w(TAG, "ToneGenerator unavailable; delayed-start beeps disabled", e)
        null
    }

    override fun tick() {
        try {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: RuntimeException) {
            Log.w(TAG, "tick tone failed", e)
        }
    }

    override fun go() {
        try {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        } catch (e: RuntimeException) {
            Log.w(TAG, "go tone failed", e)
        }
    }

    override fun release() {
        try {
            tone?.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "tone release failed", e)
        }
    }

    private companion object {
        const val TAG = "AndroidStartCue"
    }
}

/**
 * Pure lead-in countdown, extracted so it can be unit-tested with virtual time.
 *
 * From [delaySec] it publishes remaining whole seconds `delaySec … 1` via [publish], beeps through
 * [cue] on each of the last 5 seconds, then at zero clears the display (`publish(null)`), plays the
 * distinct "go" tone, and invokes [onFire] exactly once.
 *
 * A [delaySec] of 0 or less is treated as immediate: [onFire] runs at once with no ticks and no
 * emissions. Cancelling the enclosing coroutine (via [delay]) stops all emissions and never fires.
 */
suspend fun runStartCountdown(
    delaySec: Int,
    cue: StartCue,
    publish: (Int?) -> Unit,
    onFire: () -> Unit,
) {
    if (delaySec < 1) {
        onFire()
        return
    }
    for (remaining in delaySec downTo 1) {
        publish(remaining)
        if (remaining <= 5) cue.tick()
        delay(1000)
    }
    cue.go()
    publish(null)
    onFire()
}
