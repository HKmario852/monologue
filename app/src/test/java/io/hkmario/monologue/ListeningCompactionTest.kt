package io.hkmario.monologue

import io.hkmario.monologue.data.*
import org.junit.Assert.*
import org.junit.Test

class ListeningCompactionTest {
    private fun slice(id: String, instance: String, start: Long, ms: Long) = ListenEvent(id, instance, "song", start, start + ms, ms, false)

    @Test fun halfSecondRowsOfOnePlayBecomeOne() {
        // One play in 500 ms slices with a few ms of clock drift, a pause (10 s gap), then more; a counted row; another play.
        val play = (0 until 6).map { slice("a$it", "p1", 1_000L + it * 500 + it % 2, 500) } +
            (0 until 4).map { slice("b$it", "p1", 14_000L + it * 500, 500) }
        val counted = ListenEvent("count:p1", "p1", "song", 2_000, 2_000, 0, true)
        val other = (0 until 3).map { slice("c$it", "p2", 4_000L + it * 500, 500) }
        val (joined, replaced) = joinListeningSlices(play + counted + other)
        assertEquals(setOf("a0", "b0", "c0"), joined.map { it.id }.toSet())
        assertEquals(3_000L, joined.first { it.id == "a0" }.listenedMs)
        assertEquals(1_000L + 5 * 500 + 1 + 500, joined.first { it.id == "a0" }.endMs)
        assertEquals(2_000L, joined.first { it.id == "b0" }.listenedMs)
        // Every other slice is replaced; the counted row is never touched.
        assertEquals(10 + 3 - 3, replaced.size)
        assertFalse("count:p1" in replaced)
    }

    @Test fun singleRowsStayAsTheyAre() {
        val (joined, replaced) = joinListeningSlices(listOf(slice("x", "p", 0, 30_000), slice("y", "p", 60_000, 30_000)))
        assertTrue(joined.isEmpty()); assertTrue(replaced.isEmpty())
    }
}
