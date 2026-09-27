package io.hkmario.monologue.domain

import kotlin.math.abs

/** One LRCLIB search result, reduced to what matching needs. */
data class LyricsCandidate(val id: Long, val track: String, val artist: String, val durationSec: Double, val synced: String?, val plain: String?, val instrumental: Boolean = false)
data class LyricsPick(val candidate: LyricsCandidate, val text: String, val offsetSec: Int)

/** Placeholder artist names written by taggers and Windows when the real artist is unknown. */
fun isUnknownArtist(artist: String)=normalize(artist) in setOf("","未知歌手","未知的演出者","未知演出者","unknown","unknown artist","<unknown>","不明なアーティスト")

/** "niki feat. X", "A・B", "A & B", "A, B" → "niki" / "A": the name most catalogues list first. */
fun primaryArtist(artist: String): String =
    artist.split(Regex("""(?i)\s+(feat\.?|ft\.?|featuring|with|×|x)\s+|[・,，&、;；/]""")).first().trim()

private fun titleKey(title: String) = normalize(title).replace(Regex("""\s*[(（\[【].*?[)）\]】]\s*"""), " ").replace(Regex("""[\s\p{Punct}。、！？「」]"""), "")

/**
 * Picks lyrics for a song from search results by title and artist only: same title, overlapping artist,
 * a vocal (not instrumental) version. Length is never a reason to reject; it only prefers the closest version.
 */
fun pickLyrics(title: String, artist: String, durationSec: Int, candidates: List<LyricsCandidate>): LyricsPick? {
    val want=titleKey(title); val who=normalize(primaryArtist(artist))
    return candidates.asSequence()
        .filter { !it.instrumental && !Regex("(?i)off vocal|instrumental|karaoke|カラオケ").containsMatchIn(it.track) }
        .filter { titleKey(it.track)==want }
        .filter { c -> val a=normalize(c.artist); who.isBlank() || a.contains(who) || who.contains(normalize(primaryArtist(c.artist))) }
        .mapNotNull { c ->
            val text=c.synced?.takeIf { it.isNotBlank() } ?: c.plain?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val diff=if(durationSec>0 && c.durationSec>0) abs(c.durationSec-durationSec).toInt() else 0
            LyricsPick(c,text,diff)
        }
        .sortedWith(compareBy<LyricsPick> { if(it.candidate.synced.isNullOrBlank()) 1 else 0 }.thenBy { it.offsetSec })
        .firstOrNull()
}
