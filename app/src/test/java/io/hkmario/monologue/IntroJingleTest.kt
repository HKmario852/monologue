package io.hkmario.monologue

import io.hkmario.monologue.domain.IntroTimeline
import io.hkmario.monologue.domain.synthesizeIntroJingle
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class IntroJingleTest {
    private val rate = 44100
    private val jingle = synthesizeIntroJingle(rate)
    private fun rms(from: Float, to: Float): Double {
        val a = (from * rate).toInt(); val b = (to * rate).toInt()
        return sqrt((a until b).sumOf { jingle[it].toDouble() * jingle[it] } / (b - a))
    }

    @Test fun lastsAsLongAsThePictureAndStaysInRange() {
        assertEquals((IntroTimeline.DURATION * rate).toInt(), jingle.size)
        assertTrue(jingle.all { abs(it) <= 0.8001f })
        assertEquals(0.0, rms(IntroTimeline.SOUND_END, IntroTimeline.DURATION), 1e-9)
    }

    @Test fun eachSoundStartsOnItsMotion() {
        // Right after each keyframe is clearly louder than just before it.
        val onsets = listOf(IntroTimeline.LAND, IntroTimeline.STAMP, IntroTimeline.PUNCH) + IntroTimeline.GROOVES.toList()
        onsets.forEach { k -> assertTrue("sound at $k s", rms(k, k + 0.04f) > 1.5 * rms(k - 0.05f, k - 0.01f)) }
    }
}
