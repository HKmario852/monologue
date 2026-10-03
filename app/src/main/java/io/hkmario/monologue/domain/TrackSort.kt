package io.hkmario.monologue.domain

/**
 * 媒體庫 song order for the setting `sort`: "title", "artist", "duration" (longest first), "added" (newest first) or
 * "addedAsc" (oldest first); songs without a known date come last, by title.
 */
fun sortLibrary(tracks: List<Track>, sort: String): List<Track> = when(sort) {
    "artist" -> tracks.sortedBy { normalize(it.artist) }
    "duration" -> tracks.sortedByDescending { it.durationMs }
    "added" -> tracks.sortedWith(compareByDescending<Track> { it.addedMs }.thenBy { normalize(it.title) })
    // Oldest first; songs without a date still come last.
    "addedAsc" -> tracks.sortedWith(compareBy<Track> { it.addedMs == 0L }.thenBy { it.addedMs }.thenBy { normalize(it.title) })
    else -> tracks.sortedBy { normalize(it.title) }
}

/** Google Drive's `createdTime` ("2024-05-01T12:34:56.789Z") in epoch milliseconds; 0 when missing or unreadable. */
fun driveTimeMs(value: String?): Long = value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

/** Sorts that flip direction when chosen again: the default id and its reversed id. */
val reversibleSorts = mapOf("size" to "sizeAsc", "added" to "addedAsc")

/** The sort after choosing [picked] while [current] is on: choosing the sort that is already on flips its direction. */
fun nextSort(current: String, picked: String): String {
    val reversed = reversibleSorts[picked] ?: return picked
    return if(current == picked) reversed else picked
}

/** The menu entry a sort belongs to: "addedAsc" → "added". */
fun baseSort(id: String): String = reversibleSorts.entries.firstOrNull { it.value == id }?.key ?: id
