package io.hkmario.monologue.domain

import kotlin.math.*

/**
 * Keyframes of the 3-second startup intro, in seconds. The picture ([io.hkmario.monologue.ui.renderIntro]) and the
 * jingle ([synthesizeIntroJingle]) both read these, so every sound lands on the motion it belongs to.
 */
object IntroTimeline {
    const val DURATION = 3.0f
    /** Paper and grain fade in. */
    const val PAPER_END = 0.25f
    /** The record is already falling in view on the first frame; it lands (thump) and settles after a small bounce. */
    const val DROP_START = 0.0f
    const val LAND = 0.40f
    const val SETTLE = 0.55f
    /** Each groove ring starts drawing here, with one note of the arpeggio. */
    val GROOVES = floatArrayOf(0.65f, 0.85f, 1.05f)
    const val GROOVE_DRAW = 0.35f
    /** The label comes down and hits the record (chord). */
    const val STAMP_START = 1.15f
    const val STAMP = 1.25f
    /** The spindle hole punches through (tick) and a highlight sweeps across. */
    const val PUNCH = 1.55f
    /** The record lifts and the wordmark writes in (held note). */
    const val LIFT_START = 1.75f
    const val LIFT_END = 2.35f
    /** Everything fades into the app. */
    const val FADE_START = 2.60f
    /** The jingle is silent from here on. */
    const val SOUND_END = 2.80f
}

private fun hz(note: Int) = 440.0 * 2.0.pow((note - 69) / 12.0)

/**
 * The intro jingle as mono samples in -1..1, [IntroTimeline.DURATION] seconds long: vinyl crackle under the paper,
 * a thump when the record lands, a D-major arpeggio as the grooves draw, a warm chord on the label stamp, a tick on the
 * punch and a held note under the wordmark. Everything is computed here; nothing is loaded from files.
 */
fun synthesizeIntroJingle(sampleRate: Int = 44100): FloatArray {
    val t = IntroTimeline
    val out = FloatArray((t.DURATION * sampleRate).toInt())
    fun add(start: Float, seconds: Float, sample: (dt: Double) -> Double) {
        val from = (start * sampleRate).toInt(); val to = min(out.size, ((start + seconds) * sampleRate).toInt())
        for(i in from until to) out[i] += sample((i - from).toDouble() / sampleRate).toFloat()
    }
    // A soft electric-piano tone: three partials, a 6 ms attack and an exponential decay.
    fun tone(start: Float, note: Int, amp: Double, decay: Double, length: Float = 1.2f) = add(start, length) { dt ->
        val f = hz(note)
        val env = min(1.0, dt / 0.006) * exp(-dt * decay)
        amp * env * (sin(2 * PI * f * dt) + 0.35 * sin(4 * PI * f * dt) + 0.12 * sin(6 * PI * f * dt))
    }

    // Crackle: sparse clicks and a little hiss while the paper and the record appear, gone by 0.9 s.
    val random = java.util.Random(48)
    var click = 0.0
    for(i in 0 until (0.9f * sampleRate).toInt()) {
        val sec = i.toDouble() / sampleRate
        val env = if(sec < 0.5) 1.0 else 1.0 - (sec - 0.5) / 0.4
        if(random.nextDouble() < 0.0012 * env) click = (random.nextDouble() - 0.5) * 0.5
        click *= 0.86
        out[i] += (click + (random.nextDouble() - 0.5) * 0.012 * env).toFloat()
    }
    // Thump: a falling low sine when the record lands.
    var phase = 0.0
    add(t.LAND, 0.35f) { dt ->
        phase += 2 * PI * (40 + 70 * exp(-dt * 8)) / sampleRate
        0.8 * exp(-dt * 14) * sin(phase)
    }
    // Arpeggio: D4, F#4, A4, one per groove.
    intArrayOf(62, 66, 69).forEachIndexed { i, note -> tone(t.GROOVES[i], note, 0.32, 4.5) }
    // Chord on the stamp: D3, A3, D4, F#4.
    intArrayOf(50, 57, 62, 66).forEach { tone(t.STAMP, it, 0.16, 2.2, 1.4f) }
    // Tick on the punch: a short high blip with a burst of noise.
    add(t.PUNCH, 0.08f) { dt -> 0.7 * exp(-dt * 45) * sin(2 * PI * 2400 * dt) + 0.4 * exp(-dt * 90) * (random.nextDouble() - 0.5) }
    // Held D5 under the wordmark: swells for 0.25 s, then fades out by SOUND_END, with a slight vibrato.
    val holdLength = t.SOUND_END - t.LIFT_START
    add(t.LIFT_START, holdLength) { dt ->
        val env = if(dt < 0.25) dt / 0.25 else max(0.0, 1 - (dt - 0.25) / (holdLength - 0.25))
        val f = hz(74) * (1 + 0.003 * sin(2 * PI * 5 * dt))
        0.2 * env * (sin(2 * PI * f * dt) + 0.2 * sin(4 * PI * f * dt))
    }

    // Gentle limiting, then scale so the loudest moment peaks at 0.8.
    for(i in out.indices) out[i] = tanh(out[i].toDouble()).toFloat()
    val peak = out.maxOf { abs(it) }.takeIf { it > 0 } ?: 1f
    for(i in out.indices) out[i] = out[i] / peak * 0.8f
    for(i in (t.SOUND_END * sampleRate).toInt() until out.size) out[i] = 0f
    return out
}
