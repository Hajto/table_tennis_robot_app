package com.tablebot.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

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
 * Real [StartCue] that synthesises its own sine tones with [AudioTrack]. Unlike `ToneGenerator`
 * (fixed preset pitches) this gives exact frequency control, so the "go" tone can sit a perfect
 * fifth (×3:2) above the ticks.
 *
 * Each tone plays on its own short-lived thread that releases the track once playback finishes, so
 * it can't be clipped by a caller releasing the cue right after [go]. All audio work is wrapped in
 * try/catch: a failure (silent mode, resource exhaustion, …) must never block the countdown or the
 * drill — it just means no sound.
 */
class AndroidStartCue : StartCue {
    override fun tick() = playTone(TICK_HZ, TICK_MS)

    /** A perfect fifth above the tick, so zero reads as a clear, higher "go". */
    override fun go() = playTone(TICK_HZ * FIFTH_RATIO, GO_MS)

    /** Tones self-release on their own threads; nothing persistent to free here. */
    override fun release() = Unit

    private fun playTone(freqHz: Double, durationMs: Int) {
        Thread {
            var track: AudioTrack? = null
            try {
                val samples = synth(freqHz, durationMs)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 60)
            } catch (e: Exception) {
                Log.w(TAG, "start tone failed", e)
            } finally {
                try { track?.release() } catch (e: Exception) { Log.w(TAG, "track release failed", e) }
            }
        }.apply { isDaemon = true }.start()
    }

    /** A [durationMs] sine wave at [freqHz] with short linear fades to avoid click artifacts. */
    private fun synth(freqHz: Double, durationMs: Int): ShortArray {
        val count = SAMPLE_RATE * durationMs / 1000
        val fade = (SAMPLE_RATE * FADE_MS / 1000).coerceIn(1, count / 2)
        val out = ShortArray(count)
        for (i in 0 until count) {
            val env = when {
                i < fade -> i.toDouble() / fade
                i >= count - fade -> (count - i).toDouble() / fade
                else -> 1.0
            }
            val s = sin(2.0 * PI * i * freqHz / SAMPLE_RATE)
            out[i] = (s * env * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private companion object {
        const val TAG = "AndroidStartCue"
        const val SAMPLE_RATE = 44_100
        const val TICK_HZ = 880.0        // A5
        const val FIFTH_RATIO = 1.5      // perfect fifth (3:2) → go tone ≈ 1320 Hz (E6)
        const val TICK_MS = 150
        const val GO_MS = 450
        const val FADE_MS = 8
        const val AMPLITUDE = 0.6
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
