package io.hkmario.monologue.domain

/** Lyrics and their Chinese translation read from a fan translation post; the translation pairs with the original line by line. */
data class BilingualLyrics(val original: String, val translation: String?)

private fun kanaCount(s: String) = s.count { it in '぀'..'ヿ' && it != 'ー' && it != '・' }
private fun hanCount(s: String) = s.count { it in '一'..'鿿' }

private val endMarker = Regex("""^[\s\-—=~～*＊・.。]*(end|fin|the end|完|終|おわり|終わり)[\s\-—=~～*＊・.。]*$""")

/** A lyric line in the song's language: Japanese (kana), or Latin letters without Chinese characters (English songs). */
private fun isOriginalLine(s: String) = kanaCount(s) > 0 || hanCount(s) == 0 && s.count { it.isLetter() } >= 3
private fun isChineseLine(s: String) = kanaCount(s) == 0 && hanCount(s) > 0

private val lrcLine = Regex("""^((?:\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?])+)(.*)$""")
/** Credit lines ("Original：…", "Vocal：…", "作詞：…") that timed posts put before the lyrics. */
private val creditLine = Regex("""^(original|circle|vocal|vo|lyrics?|arrange(ment)?|music|composer?|作詞|作曲|編曲|原曲|社團|社団|歌手|演唱)\s*[:：]""", RegexOption.IGNORE_CASE)
private fun isHeading(text: String) = text.isEmpty() || creditLine.containsMatchIn(text) || text.first() in "「『【" && text.last() in "」』】"

/**
 * Timed posts (an LRC pasted into the post) write "[00:35.76]日本語 / 中文" per line, or give the Chinese line its own
 * copy of the timestamp. Returns synced lyrics and, when most lines carry one, a synced translation.
 */
private fun splitTimedBilingual(lines: List<String>): BilingualLyrics? {
    val timed = lines.mapNotNull { line -> lrcLine.find(line)?.destructured?.let { (time, text) -> time to text.trim() } }
    if(timed.count { it.second.isNotEmpty() } < 8) return null
    val body = timed.dropWhile { isHeading(it.second) }.filterNot { creditLine.containsMatchIn(it.second) }
    val original = StringBuilder(); val translation = StringBuilder(); var pairs = 0; var lyricLines = 0
    for((time, text) in body) {
        if(text.isEmpty()) { original.append(time).append('\n'); continue }
        lyricLines++
        val cut = Regex("""\s+[/／]\s+""").findAll(text).lastOrNull()
        val left = cut?.let { text.substring(0, it.range.first).trim() }
        val right = cut?.let { text.substring(it.range.last + 1).trim() }
        if(left != null && right != null && isOriginalLine(left) && isChineseLine(right)) {
            original.append(time).append(left).append('\n'); translation.append(time).append(right).append('\n'); pairs++
        } else original.append(time).append(text).append('\n')
    }
    if(lyricLines < 4) return null
    if(pairs >= lyricLines * 0.6) return BilingualLyrics(original.toString().trim(), translation.toString().trim())
    // Chinese on its own line under the same timestamp.
    splitEmbeddedTranslation(original.toString())?.let { (o, t) -> return BilingualLyrics(o.trim(), t.trim()) }
    return BilingualLyrics(original.toString().trim(), null)
}

/**
 * Fan translation posts (such as on 巴哈姆特) put each stanza, or each line, of the original lyrics before its
 * Chinese translation. Lines are grouped into runs of original and Chinese lines, and a run of original lines is
 * paired with the Chinese run right after it when both have the same number of lines. Credits before a "歌詞"
 * heading are skipped. When any run cannot be paired the translation is left out rather than misaligned.
 * Posts that paste an LRC keep their timestamps. Returns null when the post has too few lyric lines to be lyrics.
 */
fun splitBilingualLyrics(text: String): BilingualLyrics? {
    // End markers ("END", "完") close many posts; they are not lyrics.
    val all = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !endMarker.matches(normalize(it)) }
    splitTimedBilingual(all)?.let { return it }
    val heading = all.indexOfFirst { it.trimEnd('：', ':', ' ') == "歌詞" }
    val lines = (if(heading >= 0) all.drop(heading + 1) else all).dropWhile { !isOriginalLine(it) }
    // Runs of original (true) or Chinese (false) lines; symbols and similar lines stay in the run they are in.
    val runs = mutableListOf<Pair<Boolean, MutableList<String>>>()
    for(line in lines) {
        val kind = when { isOriginalLine(line) -> true; isChineseLine(line) -> false; else -> null }
        val last = runs.lastOrNull()
        if(last != null && (kind == null || kind == last.first)) last.second += line
        else if(kind != null) runs += kind to mutableListOf(line)
    }
    val originalRuns = runs.withIndex().filter { it.value.first }
    val original = originalRuns.flatMap { it.value.second }
    if(original.size < 4) return null
    val translation = mutableListOf<String>()
    for((i, run) in originalRuns) {
        val next = runs.getOrNull(i + 1)?.takeIf { !it.first }?.second
        val isLast = i == originalRuns.last().index
        // The last Chinese run may carry the translator's notes after the lyrics.
        if(next == null || next.size < run.second.size || next.size > run.second.size && !isLast) return BilingualLyrics(original.joinToString("\n"), null)
        translation += next.take(run.second.size)
    }
    return BilingualLyrics(original.joinToString("\n"), translation.joinToString("\n"))
}

/** Title text reduced for comparison: case, spaces, punctuation and decorations such as ♡ removed. */
fun looseTitleKey(s: String) = normalize(s).replace(Regex("""[\s\p{Punct}。、！？「」『』♪☆★・♡♥❤]"""), "")
private val translationWords = Regex("""(中文|中日|日中|中英|英中)?(歌詞)?(翻譯|翻译|中譯|歌詞)$""")

/** The pieces of a post title that may be the song title: "【東方Vocal】FELT｜Time and again (中文翻譯)" → FELT, Time and again, … */
fun postTitleParts(postTitle: String): List<String> {
    val cleaned = withoutBracketNotes(postTitle)
    return (cleaned.split(Regex("""[｜|／/－—–~～]|\s-\s""")).map { it.trim().replace(translationWords, "").trim() } + cleaned).filter { it.isNotBlank() }
}

/** Whether a post title such as "【東方Vocal】FELT｜Time and again (中文翻譯)" is about the song [title]. */
fun postTitleMentionsSong(postTitle: String, title: String): Boolean {
    if(postTitleParts(postTitle).any { sameTitle(it, title) || looseTitleKey(it) == looseTitleKey(title) }) return true
    val key = looseTitleKey(withoutBracketNotes(title))
    return key.length >= 4 && looseTitleKey(withoutBracketNotes(postTitle)).contains(key)
}

/**
 * Titles to search for, from how a file is tagged. Rips often put the album and track number before the song:
 * "[Color&Color+] 虹色キャンバス #02 - ♡Second Love♡二人の唇" also gives "Second Love♡二人の唇".
 */
fun songTitleVariants(title: String, album: String = ""): List<String> {
    val out = mutableListOf(title)
    searchTitleWithoutNotes(title)?.let { out += it }
    val parts = withoutBracketNotes(title).split(Regex("""\s[-－–—]\s"""))
    if(parts.size >= 2) {
        val head = parts.dropLast(1).joinToString(" - ").trim()
        val numbered = Regex("""(#|No\.?\s*)?\d{1,3}\.?$|^\d{1,3}[.\s]""", RegexOption.IGNORE_CASE).containsMatchIn(head)
        val albumFirst = album.isNotBlank() && looseTitleKey(album).length >= 2 && looseTitleKey(head).contains(looseTitleKey(album))
        if(numbered || albumFirst) out += parts.last()
    }
    return out.map { it.trim().trim('♡', '♥', '❤', '☆', '★', '♪', ' ') }.filter { it.isNotBlank() }.distinct()
}
