package io.hkmario.monologue.data

import androidx.room.withTransaction

/**
 * Up to 0.4.14 a listening row was written every half second, so a few weeks of listening left tens of thousands of
 * rows that the statistics re-read on every write. Joins back-to-back rows of the same play (same instance, each
 * starting within a second of the last one's end) into one row covering them; plays, counted rows and listening time
 * stay the same. Returns the joined rows and the ids of the rows they replace.
 */
fun joinListeningSlices(events: List<ListenEvent>): Pair<List<ListenEvent>, List<String>> {
    val joined = mutableListOf<ListenEvent>(); val replaced = mutableListOf<String>()
    for((_, play) in events.filterNot { it.counted }.groupBy { it.instanceId }) {
        var run = mutableListOf<ListenEvent>()
        fun finish() {
            if(run.size >= 2) {
                joined += run.first().copy(endMs = run.last().endMs, listenedMs = run.sumOf { it.listenedMs })
                replaced += run.drop(1).map { it.id }
            }
            run = mutableListOf()
        }
        for(event in play.sortedBy { it.startMs }) {
            val last = run.lastOrNull()
            if(last != null && event.startMs - last.endMs !in -1000L..1000L) finish()
            run += event
        }
        finish()
    }
    return joined to replaced
}

/**
 * Joins the half-second rows once; later listening is written one row per stretch of playing. Only rows that ended
 * before [before] (this launch) are touched, so the stretch being written now is left alone.
 */
suspend fun compactListeningEvents(db: MusicDatabase, settings: SettingsRepository, before: Long) {
    if(settings.snapshot().bool("listeningCompacted")) return
    val dao = db.dao()
    val (joined, replaced) = joinListeningSlices(dao.events().filter { it.endMs < before })
    db.withTransaction {
        joined.forEach { dao.putEvent(it) }
        replaced.chunked(500).forEach { dao.deleteEvents(it) }
    }
    settings.set("listeningCompacted", "true")
}
