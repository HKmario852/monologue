package io.hkmario.monologue.domain

/**
 * 媒體庫 song order for the setting `sort`: "title", "artist", "duration" (longest first) or "added" (newest first;
 * songs without a known date come last, by title).
 */
fun sortLibrary(tracks: List<Track>, sort: String): List<Track> = when(sort) {
    "artist" -> tracks.sortedBy { normalize(it.artist) }
    "duration" -> tracks.sortedByDescending { it.durationMs }
    "added" -> tracks.sortedWith(compareByDescending<Track> { it.addedMs }.thenBy { normalize(it.title) })
    else -> tracks.sortedBy { normalize(it.title) }
}

/** Google Drive's `createdTime` ("2024-05-01T12:34:56.789Z") in epoch milliseconds; 0 when missing or unreadable. */
fun driveTimeMs(value: String?): Long = value?.takeIf { it.isNotBlank() }?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
