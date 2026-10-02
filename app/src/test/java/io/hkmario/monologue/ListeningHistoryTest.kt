package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class ListeningHistoryTest {
    private fun track(id: String, title: String) = Track(id, title, uri = "u")

    @Test fun mostRecentFirstAndNeverPlayedLast() {
        val tracks = listOf(track("a", "Banana"), track("b", "Apple"), track("c", "Cherry"), track("d", "Date"))
        val history = listeningHistory(tracks, mapOf("c" to 3_000L, "a" to 9_000L))
        assertEquals(listOf("a", "c", "b", "d"), history.map { it.track.id })
        assertEquals(listOf(9_000L, 3_000L, null, null), history.map { it.lastPlayedMs })
    }

    @Test fun labelsReadNaturally() {
        val zone = ZoneId.of("Asia/Hong_Kong"); val now = 1_000_000_000_000L
        assertEquals("從未播放", lastPlayedLabel(null, now, zone))
        assertEquals("剛剛", lastPlayedLabel(now - 30_000, now, zone))
        assertEquals("5 分鐘前", lastPlayedLabel(now - 5 * 60_000, now, zone))
        assertEquals("3 小時前", lastPlayedLabel(now - 3 * 3_600_000, now, zone))
        assertEquals("2 日前", lastPlayedLabel(now - 2 * 86_400_000L, now, zone))
        assertEquals("2001/08/30", lastPlayedLabel(now - 10 * 86_400_000L, now, zone))
    }
}
