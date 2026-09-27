package io.hkmario.monologue.domain

import kotlin.math.abs
import kotlin.math.max

/** One LRCLIB search result, reduced to what matching needs. */
data class LyricsCandidate(val id: Long, val track: String, val artist: String, val durationSec: Double, val synced: String?, val plain: String?, val instrumental: Boolean = false)
data class LyricsPick(val candidate: LyricsCandidate, val text: String, val offsetSec: Int)

/** Placeholder artist names written by taggers and Windows when the real artist is unknown. */
fun isUnknownArtist(artist: String)=normalize(artist) in setOf("","未知歌手","未知的演出者","未知演出者","unknown","unknown artist","<unknown>","不明なアーティスト","various artists")

private val creditSplit=Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring|with|vs\.?|×|x)\s+|\s*(?:cv|vo)\s*[.:：]\s*|[・,，&、;；/()（）\[\]【】]""")

/** Every name credited in an artist field: "sumijun feat. ほたる" → [sumijun, ほたる]; "涼風青葉(CV:高田憂希)" → [涼風青葉, 高田憂希]. */
fun creditedArtists(artist: String): List<String> =
    artist.split(creditSplit).map { it.trim() }.filter { it.isNotEmpty() && !isUnknownArtist(it) }.distinct()

/** The first credit, which most catalogues list: "niki feat. X" → "niki". */
fun primaryArtist(artist: String): String = creditedArtists(artist).firstOrNull() ?: artist.trim()

/** Levenshtein similarity in 0..1. */
fun similarity(a: String, b: String): Double {
    if(a==b) return 1.0
    if(a.isEmpty() || b.isEmpty()) return 0.0
    var prev=IntArray(b.length+1) { it }
    for(i in 1..a.length) {
        val cur=IntArray(b.length+1); cur[0]=i
        for(j in 1..b.length) cur[j]=minOf(prev[j]+1,cur[j-1]+1,prev[j-1]+if(a[i-1]==b[j-1]) 0 else 1)
        prev=cur
    }
    return 1.0-prev[b.length].toDouble()/max(a.length,b.length)
}

private fun nameKey(name: String) = normalize(name).replace(Regex("""[\s\p{Punct}・。、]"""), "")
private fun titleKey(title: String) = normalize(title).replace(Regex("""\s*[(（\[【].*?[)）\]】]\s*"""), " ").replace(Regex("""[\s\p{Punct}。、！？「」♪☆★]"""), "")

/** Same name, or close enough (≥85% similar) for names long enough that a near miss is a spelling variant. */
fun sameName(a: String, b: String): Boolean {
    val x=nameKey(a); val y=nameKey(b)
    if(x.isEmpty() || y.isEmpty()) return false
    if(x==y) return true
    return minOf(x.length,y.length)>=4 && similarity(x,y)>=0.85
}
fun sameTitle(a: String, b: String): Boolean {
    val x=titleKey(a); val y=titleKey(b)
    if(x.isEmpty() || y.isEmpty()) return false
    return x==y || minOf(x.length,y.length)>=6 && similarity(x,y)>=0.9
}

/**
 * Picks lyrics by title and artist: the title must match and one of the song's credited artists
 * (or a known alias such as the romanised name) must match one of the result's credits.
 * Length never rejects a result; it only prefers the closest version. Instrumental versions are skipped.
 */
fun pickLyrics(title: String, artists: Collection<String>, durationSec: Int, candidates: List<LyricsCandidate>): LyricsPick? {
    val ours=artists.filter { it.isNotBlank() }
    return candidates.asSequence()
        .filter { !it.instrumental && !Regex("(?i)off vocal|instrumental|karaoke|カラオケ").containsMatchIn(it.track) }
        .filter { sameTitle(it.track,title) }
        .filter { c -> ours.isEmpty() || creditedArtists(c.artist).ifEmpty { listOf(c.artist) }.any { theirs -> ours.any { sameName(it,theirs) } } }
        .mapNotNull { c ->
            val text=c.synced?.takeIf { it.isNotBlank() } ?: c.plain?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LyricsPick(c,text,if(durationSec>0 && c.durationSec>0) abs(c.durationSec-durationSec).toInt() else 0)
        }
        .sortedWith(compareBy<LyricsPick> { if(it.candidate.synced.isNullOrBlank()) 1 else 0 }.thenBy { it.offsetSec })
        .firstOrNull()
}

/** "Minase, Inori" (a MusicBrainz sort name) → both "Inori Minase" and "Minase Inori". */
fun sortNameVariants(sortName: String): List<String> {
    val parts=sortName.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return if(parts.size==2) listOf("${parts[1]} ${parts[0]}","${parts[0]} ${parts[1]}") else listOf(sortName.trim())
}
