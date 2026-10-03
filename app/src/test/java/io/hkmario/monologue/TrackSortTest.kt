package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test

class TrackSortTest {
    private fun track(id: String, title: String, added: Long) = Track(id, title, uri = "u", addedMs = added)

    @Test fun addedSortsNewestFirstAndUnknownLast() {
        val tracks = listOf(track("a", "Old", 1_000), track("b", "Zeta", 0), track("c", "New", 5_000), track("d", "Alpha", 0))
        assertEquals(listOf("c", "a", "d", "b"), sortLibrary(tracks, "added").map { it.id })
        assertEquals(listOf("d", "c", "a", "b"), sortLibrary(tracks, "title").map { it.id })
    }

    @Test fun oldestFirstStillPutsUnknownLast() {
        val tracks = listOf(track("a", "Old", 1_000), track("b", "Zeta", 0), track("c", "New", 5_000))
        assertEquals(listOf("a", "c", "b"), sortLibrary(tracks, "addedAsc").map { it.id })
    }

    @Test fun choosingTheSameSortAgainFlipsIt() {
        assertEquals("addedAsc", nextSort("added", "added"))
        assertEquals("added", nextSort("addedAsc", "added"))
        assertEquals("sizeAsc", nextSort("size", "size"))
        assertEquals("size", nextSort("name", "size"))
        assertEquals("added", nextSort("sizeAsc", "added"))
        assertEquals("title", nextSort("title", "title"))
        assertEquals("added", baseSort("addedAsc")); assertEquals("title", baseSort("title"))
    }

    @Test fun driveTimesParse() {
        assertEquals(1714566896789L, driveTimeMs("2024-05-01T12:34:56.789Z"))
        assertEquals(0L, driveTimeMs(""))
        assertEquals(0L, driveTimeMs("not a date"))
        assertEquals(0L, driveTimeMs(null))
    }
}
