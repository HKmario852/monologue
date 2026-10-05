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
private const val creditLabels = """original|circle|vocals?|vo|lyrics?|arrange(?:ment)?|music|composer?|translat(?:ion|or|ed by)|""" +
    """作詞|作曲|編曲|原曲|社團|社団|歌手|演唱|歌|唄|中文翻譯|中譯|翻譯|翻译|譯者|譯|線上試聽|試聽|出處|來源"""
/** Credit lines ("Original：…", "作詞．作曲：…", "歌：…", "線上試聽：…") that posts put before the lyrics. */
private val creditLine = Regex("""^(?:(?:$creditLabels)[\s．・.／/、&＆]*)+[:：]""", RegexOption.IGNORE_CASE)
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

/** Where the lyrics start after a header near the top: title lines, then credits ("作詞：…", "歌：…", "線上試聽：…"). */
private fun afterCredits(lines: List<String>): Int {
    val first = lines.take(8).indexOfFirst { creditLine.containsMatchIn(it) }
    if(first < 0) return 0
    var end = first
    while(end + 1 < lines.size && creditLine.containsMatchIn(lines[end + 1])) end++
    return end + 1
}

/** Kana in one script, so a reading in hiragana compares with katakana in the lyric. */
private fun hiragana(c: Char) = if(c in 'ァ'..'ヶ') c - 0x60 else c
private fun isKana(c: Char) = c in '぀'..'ヿ' && c != 'ー' && c != '・'

/** Whether [reading] is how [lyric] is read: its romaji, or its kana keeping the lyric's own kana in order. */
private fun isReadingOf(lyric: String, reading: String): Boolean {
    if(hanCount(reading) > 0 || kanaCount(lyric) + hanCount(lyric) == 0) return false
    if(kanaCount(reading) == 0) return isRomajiLine(reading)
    // A kana-only lyric line is its own reading; otherwise the reading must cover the kanji too.
    if(hanCount(lyric) == 0) return looseTitleKey(lyric).map(::hiragana) == looseTitleKey(reading).map(::hiragana)
    val kana = reading.filter(::isKana).map(::hiragana).joinToString("")
    var at = 0
    for(c in lyric.filter(::isKana).map(::hiragana)) at = kana.indexOf(c, at).takeIf { it >= 0 }?.plus(1) ?: return false
    return kana.length > kanaCount(lyric)
}

/**
 * The lyric lines of a run of original lines that also gives each line's kana reading and/or romaji, as some posts
 * write "日本語 / reading / romaji / 中文" per line (or a stanza, then its readings). Null when it is not laid out so.
 */
private fun withoutReadings(run: List<String>): List<String>? {
    for(k in listOf(3, 2)) {
        if(run.size % k != 0) continue
        val size = run.size / k
        val groups = run.chunked(k)
        if(groups.all { g -> g.drop(1).all { isReadingOf(g[0], it) } }) return groups.map { it[0] }
        val lyric = run.take(size)
        if((1 until k).all { b -> lyric.indices.all { isReadingOf(lyric[it], run[b * size + it]) } }) return lyric
    }
    return null
}

/** Furigana written into the line after a kanji: "俯(うつむ)く" → "俯く". */
private val inlineFurigana = Regex("""([一-鿿々〆])[(（][぀-ヿ]+[)）]""")

/**
 * Fan translation posts (such as on 巴哈姆特) put each stanza, or each line, of the original lyrics before its
 * Chinese translation. Lines are grouped into runs of original and Chinese lines, and a run of original lines is
 * paired with the Chinese run right after it when both have the same number of lines; kana readings and romaji
 * written under each line are left out. Credits at the top, or before a "歌詞" heading, are skipped. When any run
 * cannot be paired the translation is left out rather than misaligned.
 * Posts that paste an LRC keep their timestamps. Returns null when the post has too few lyric lines to be lyrics.
 */
fun splitBilingualLyrics(text: String): BilingualLyrics? {
    // End markers ("END", "完") close many posts; they are not lyrics.
    val all = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !endMarker.matches(normalize(it)) }
    splitTimedBilingual(all)?.let { return it }
    val heading = all.indexOfFirst { it.trimEnd('：', ':', ' ') == "歌詞" }
    val lines = all.drop(if(heading >= 0) heading + 1 else afterCredits(all)).filterNot { creditLine.containsMatchIn(it) }
        .map { it.replace(inlineFurigana, "$1") }.dropWhile { !isOriginalLine(it) }
    // Runs of original (true) or Chinese (false) lines; symbols and similar lines stay in the run they are in.
    val runs = mutableListOf<Pair<Boolean, MutableList<String>>>()
    for(line in lines) {
        val kind = when { isOriginalLine(line) -> true; isChineseLine(line) -> false; else -> null }
        val last = runs.lastOrNull()
        if(last != null && (kind == null || kind == last.first)) last.second += line
        else if(kind != null) runs += kind to mutableListOf(line)
    }
    val originalRuns = runs.withIndex().filter { it.value.first }.map { (i, run) ->
        // Readings are left out only when that is what lets the run pair with its translation.
        val chinese = runs.getOrNull(i + 1)?.takeIf { !it.first }?.second?.size ?: 0
        IndexedValue(i, run.second.takeIf { it.size == chinese } ?: withoutReadings(run.second)?.takeIf { it.size <= chinese } ?: run.second)
    }
    val original = originalRuns.flatMap { it.value }
    if(original.size < 4) return null
    val translation = mutableListOf<String>()
    for((i, run) in originalRuns) {
        val next = runs.getOrNull(i + 1)?.takeIf { !it.first }?.second
        val isLast = i == originalRuns.last().index
        // The last Chinese run may carry the translator's notes after the lyrics.
        if(next == null || next.size < run.size || next.size > run.size && !isLast) return BilingualLyrics(original.joinToString("\n"), null)
        translation += next.take(run.size)
    }
    return BilingualLyrics(original.joinToString("\n"), translation.joinToString("\n"))
}

/** A lyric line reduced to its letters and digits, to find the same line in another source's lyrics. */
private fun lineKey(s: String) = normalize(s).filter { it.isLetterOrDigit() }.map(::hiragana).joinToString("")

/** The translations of source lines that, joined, make up [key]; null when no run of lines does. */
private fun joinedTranslation(pairs: List<Pair<String, String>>, key: String): String? {
    for(i in pairs.indices) {
        if(!key.startsWith(pairs[i].first)) continue
        var joined = ""; val parts = mutableListOf<String>()
        for(j in i until pairs.size) {
            joined += pairs[j].first; parts += pairs[j].second
            if(joined == key && parts.size >= 2) return parts.joinToString(" ")
            if(!key.startsWith(joined)) break
        }
    }
    return null
}

/**
 * Puts a translation from another source onto [target] lyrics by matching the original lines' text, so a fan
 * translation of plain-text lyrics can show under synced lyrics. A target line may be two source lines joined, or
 * the start of a longer source line (which then carries on over the next target lines). Returns the translation laid
 * out like [target] — synced lyrics get their own timestamps, plain lyrics one line each — or null when fewer than
 * 70 % of the lines are found.
 */
fun borrowTranslation(target: String, sourceOriginal: String, sourceTranslation: String): String? {
    val pairs = Lrc.align(Lrc.parse(sourceOriginal), Lrc.parse(sourceTranslation))
        .mapNotNull { line -> line.translation?.let { lineKey(line.text) to it } }.filter { it.first.isNotEmpty() }
    val lines = Lrc.parse(target)
    if(pairs.isEmpty() || lines.size < 4) return null
    val found = arrayOfNulls<String>(lines.size); var covered = 0
    var rest = ""
    for((n, line) in lines.withIndex()) {
        val key = lineKey(line.text)
        when {
            key.isEmpty() -> covered++
            rest.isNotEmpty() && rest.startsWith(key) -> { rest = rest.removePrefix(key); covered++ }
            else -> {
                rest = ""
                val whole = pairs.firstOrNull { it.first == key }?.second ?: joinedTranslation(pairs, key)
                val start = if(whole == null) pairs.firstOrNull { it.first.length > key.length && it.first.startsWith(key) } else null
                found[n] = whole ?: start?.second
                if(start != null) rest = start.first.removePrefix(key)
                if(found[n] != null) covered++
            }
        }
    }
    if(covered < lines.size * 0.7) return null
    if(lines.any { it.timeMs == null }) return if(found.all { it != null }) found.joinToString("\n") else null
    return lines.indices.filter { found[it] != null }.joinToString("\n") { n ->
        val ms = lines[n].timeMs!!
        String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]%s", ms / 60000, ms / 1000 % 60, ms % 1000 / 10, found[n])
    }
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
