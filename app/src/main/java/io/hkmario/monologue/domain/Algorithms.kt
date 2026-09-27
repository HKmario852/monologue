package io.hkmario.monologue.domain

import kotlinx.collections.immutable.*
import java.text.Normalizer
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

/** Session-scoped presentation clock. Never depends on a track identifier. */
class VinylClock(initialAngle: Double = 0.0) {
    var state = VinylPresentationState(angle = initialAngle.mod(360.0)); private set
    fun angle(nowNanos: Long): Double = if (state.anchorNanos == null) state.angle else
        (state.angle + (nowNanos - state.anchorNanos!!).coerceAtLeast(0) / 1e9 * state.degreesPerSecond).mod(360.0)
    @Synchronized fun configure(now: Long, playing: Boolean = state.enginePlaying, visible: Boolean = state.visible, allowed: Boolean = state.allowed) {
        val angle = angle(now)
        state = state.copy(angle = angle, enginePlaying = playing, visible = visible, allowed = allowed,
            anchorNanos = if (playing && visible && allowed) now else null)
    }
    fun restore(angle: Double) { state = state.copy(angle = angle.mod(360.0), anchorNanos = null, visible = false) }
}
fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
fun search(tracks: List<Track>, request: SearchRequest): LibrarySearchUiState {
    val q = normalize(request.query)
    if (request.tab == LibraryTab.Tracks) {
        val results = tracks.filter { q in normalize("${it.title} ${it.artist} ${it.album}") }.toPersistentList()
        return LibrarySearchUiState(request, if(results.isEmpty()) Phase.Empty else Phase.Ready, tracks = results)
    }
    val groups = tracks.groupBy { when(request.tab) { LibraryTab.Artists -> it.artist; LibraryTab.Albums -> it.album; else -> it.folder } }
        .filterKeys { q in normalize(it) }.map { (name, items) -> GroupItem("${request.tab}:$name", name, items.toPersistentList()) }.sortedBy { normalize(it.title) }.toPersistentList()
    return LibrarySearchUiState(request, if(groups.isEmpty()) Phase.Empty else Phase.Ready, groups = groups)
}
fun recordSearch(history: List<String>, query: String): PersistentList<String> = if(query.isBlank()) history.toPersistentList() else
    (listOf(query.trim()) + history.filterNot { normalize(it) == normalize(query) }).take(10).toPersistentList()

object Lrc {
    private val time = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    fun parse(text: String): PersistentList<LyricLine> {
        val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val lines = mutableListOf<LyricLine>()
        text.lineSequence().forEachIndexed { index, raw ->
            val tags = time.findAll(raw).toList()
            val words = raw.replace(time, "").replace(Regex("\\[[a-zA-Z]+:.*?]"), "").trim()
            if (words.isNotEmpty()) {
                if(tags.isEmpty()) lines += LyricLine("$index", null, words)
                else tags.forEachIndexed { tagIndex, m ->
                    val ms = m.groupValues[1].toLong()*60000 + m.groupValues[2].toLong()*1000 + m.groupValues[3].padEnd(3,'0').take(3).toLong()
                    lines += LyricLine("$index:$tagIndex", (ms+offset).coerceAtLeast(0), words)
                }
            }
        }
        return lines.sortedWith(compareBy<LyricLine> { it.timeMs ?: Long.MAX_VALUE }).toPersistentList()
    }
    /** Attaches romanised lines by the same one-to-one timestamp rule as translations. */
    fun alignRomaji(original: List<LyricLine>, romaji: List<LyricLine>, tolerance: Long = 400): PersistentList<LyricLine> =
        align(original.map { it.copy(translation = null) }, romaji, tolerance).mapIndexed { i, l -> original[i].copy(romaji = l.translation) }.toPersistentList()
    /** Only one-to-one timestamp matches inside a small tolerance. Never align by list index. */
    fun align(original: List<LyricLine>, translated: List<LyricLine>, tolerance: Long = 400): PersistentList<LyricLine> {
        // Plain (untimed) lyrics have nothing else to go by: pair lines in order, but only when both sides are
        // entirely untimed and have the same number of lines, as with a line-by-line machine translation.
        if(original.isNotEmpty() && original.all { it.timeMs == null } && translated.all { it.timeMs == null } && original.size == translated.size)
            return original.mapIndexed { i, line -> line.copy(translation = translated[i].text) }.toPersistentList()
        val used = mutableSetOf<String>()
        return original.map { line ->
            val candidates = if(line.timeMs == null) emptyList() else translated.filter { it.timeMs != null && it.id !in used && abs(it.timeMs - line.timeMs) <= tolerance }
            val match = candidates.singleOrNull()
            if(match != null) { used += match.id; line.copy(translation = match.text) } else line
        }.toPersistentList()
    }
}
data class TimeRange(val start: Instant, val endExclusive: Instant)
fun periodRange(period: Period, offset: Int, zone: ZoneId, now: Instant): TimeRange {
    val date = now.atZone(zone).toLocalDate()
    val start = when(period) {
        Period.Week -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
        Period.Month -> date.withDayOfMonth(1).plusMonths(offset.toLong())
        Period.All -> LocalDate.of(1970, 1, 1)
    }
    val end = when(period) { Period.Week -> start.plusWeeks(1); Period.Month -> start.plusMonths(1); Period.All -> LocalDate.of(9999, 1, 1) }
    return TimeRange(start.atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant())
}
fun overlapMs(start: Long, end: Long, rangeStart: Long, rangeEnd: Long) = (minOf(end, rangeEnd) - maxOf(start, rangeStart)).coerceAtLeast(0)
fun countThreshold(duration: Long): Long = if(duration > 0) minOf(30_000L, duration / 2) else 30_000L
fun listenBrainzThreshold(duration: Long): Long = if(duration > 0) minOf(240_000L, duration / 2) else 240_000L
data class ListeningSlice(val start: Long, val end: Long, val elapsed: Long)
/** Wall clock anchors events; monotonic clock measures actual playing time. Seek never adds media-position delta. */
class ListeningMeter {
    private var anchor: Long? = null
    private var wallAnchor: Long = 0
    var totalMs: Long = 0; private set
    var counted: Boolean = false; private set
    fun update(nowMono: Long, nowWall: Long, wasPlaying: Boolean): ListeningSlice? {
        val previous = anchor
        val slice = if(previous != null && wasPlaying) {
            val elapsed = (nowMono - previous).coerceAtLeast(0)
            totalMs += elapsed
            ListeningSlice(wallAnchor, wallAnchor + elapsed, elapsed)
        } else null
        anchor = nowMono; wallAnchor = nowWall
        return slice
    }
    fun markCount(duration: Long): Boolean = if(!counted && totalMs >= countThreshold(duration)) { counted = true; true } else false
    fun reset() { anchor = null; totalMs = 0; counted = false }
}
object DownloadMachine {
    fun afterItem(pending: Int, pauseBetween: Boolean, cancelled: Boolean = false): DownloadPhase = when {
        cancelled -> DownloadPhase.Cancelled
        pending == 0 -> DownloadPhase.Complete
        pauseBetween -> DownloadPhase.Waiting
        else -> DownloadPhase.Running
    }
    fun toggle(state: DownloadPhase, enabled: Boolean) = state // toggling a preference never starts work
    fun retry(items: List<DownloadItem>) = items.map { if(it.status == DownloadStatus.Failed) it.copy(status=DownloadStatus.Queued, bytes=0, error=null) else it }.toPersistentList()
}

fun lyricOffsetKey(id: String?) = "lyricOffset." + java.security.MessageDigest.getInstance("SHA-256").digest((id ?: "").toByteArray()).take(12).joinToString("") {"%02x".format(it)}
