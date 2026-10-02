package io.hkmario.monologue.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A song in 聆聽回顧's history, with when it was last heard (null: never). */
data class HistoryEntry(val track: Track, val lastPlayedMs: Long?)

/**
 * Every song in [tracks], most recently heard first; songs never heard come last, by title.
 * [lastPlayed] maps a track ID to the end of its latest listening.
 */
fun listeningHistory(tracks: List<Track>, lastPlayed: Map<String, Long>): List<HistoryEntry> {
    val (heard, never) = tracks.distinctBy { it.id }.map { HistoryEntry(it, lastPlayed[it.id]) }.partition { it.lastPlayedMs != null }
    return heard.sortedByDescending { it.lastPlayedMs } + never.sortedBy { normalize(it.track.title) }
}

/** "剛剛", "5 分鐘前", "3 小時前", "2 日前", then the date; "從未播放" when never heard. */
fun lastPlayedLabel(lastPlayedMs: Long?, nowMs: Long, zone: ZoneId): String {
    lastPlayedMs ?: return "從未播放"
    val minutes = (nowMs - lastPlayedMs).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "剛剛"
        minutes < 60 -> "$minutes 分鐘前"
        minutes < 24 * 60 -> "${minutes / 60} 小時前"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} 日前"
        else -> Instant.ofEpochMilli(lastPlayedMs).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
    }
}
