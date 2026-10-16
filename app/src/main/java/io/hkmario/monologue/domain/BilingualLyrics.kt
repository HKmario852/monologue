package io.hkmario.monologue.domain

/** Lyrics and their Chinese translation read from a fan translation post; the translation pairs with the original line by line. */
data class BilingualLyrics(val original: String, val translation: String?)

private fun kanaCount(s: String) = s.count { it in '぀'..'ヿ' && it != 'ー' && it != '・' }
private fun hanCount(s: String) = s.count { it in '一'..'鿿' }

private val endMarker = Regex("""^[\s\-—=~～*＊・.。]*(end|fin|the end|完|終|おわり|終わり)[\s\-—=~～*＊・.。]*$""")

/**
 * A Chinese line that keeps a name in katakana ("幼時便在一起的ジュリーヌ妹妹"): no hiragana, which Japanese lines
 * nearly always have, more Chinese characters than kana, and the katakana in one stretch of two or more. Lyrics
 * written with katakana endings ("救イノ無イ世界") have them scattered between the kanji, so they stay Japanese.
 */
private fun chineseWithKatakanaName(s: String) = s.none { it in 'ぁ'..'ゟ' } && hanCount(s) > 0 && hanCount(s) >= kanaCount(s) &&
    katakanaStretch.findAll(s).toList().let { it.size == 1 && it[0].value.count { c -> c != 'ー' && c != '・' } >= 2 }
private val katakanaStretch = Regex("""[ァ-ヺ][ァ-ヿ]*""")
/** A lyric line in the song's language: Japanese (kana), or Latin letters without Chinese characters (English songs). */
private fun isOriginalLine(s: String) = kanaCount(s) > 0 && !chineseWithKatakanaName(s) || hanCount(s) == 0 && s.count { it.isLetter() } >= 3
private fun isChineseLine(s: String) = hanCount(s) > 0 && (kanaCount(s) == 0 || chineseWithKatakanaName(s))

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
/** Romaji written syllable by syllable ("shi ka re tu rwe e o"): Latin letters only, nearly every word 1 to 3 letters. */
private fun spacedSyllables(line: String): Boolean {
    if(kanaCount(line) + hanCount(line) > 0) return false
    val words = Regex("""[A-Za-z]+""").findAll(line).map { it.value }.toList()
    return words.size >= 3 && words.count { it.length <= 3 } >= 0.8 * words.size
}
private fun withoutReadings(run: List<String>): List<String>? {
    for(k in listOf(3, 2)) {
        if(run.size % k != 0) continue
        val size = run.size / k
        val groups = run.chunked(k)
        // Line, kana, romaji: the kana and romaji may be how it is sung rather than read (レール as れうぇえお, rwe e o).
        fun sungReading(g: List<String>) = k == 3 && kanaCount(g[0]) + hanCount(g[0]) > 0 && hanCount(g[1]) == 0 && kanaCount(g[1]) > 0 &&
            (isRomajiLine(g[2]) || spacedSyllables(g[2]))
        if(groups.all { g -> sungReading(g) || g.drop(1).all { isReadingOf(g[0], it) } }) return groups.map { it[0] }
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
    val body = all.drop(if(heading >= 0) heading + 1 else afterCredits(all)).filterNot { creditLine.containsMatchIn(it) }
        .map { it.replace(inlineFurigana, "$1") }
    // Most posts put the translation under each line; some put it first. When both readings pair, the one whose
    // pairs share more kanji (夢 and 夢, 信じて and 相信) is the right one.
    val under = translationUnder(body.dropWhile { !isOriginalLine(it) })
    val first = translationFirst(body) ?: return under
    if(under?.translation == null) return first
    return if(kanjiAgreement(first) > kanjiAgreement(under)) first else under
}

/** Runs of original (true) or Chinese (false) lines; symbols and similar lines stay in the run they are in. */
private fun runsOf(lines: List<String>): List<Pair<Boolean, MutableList<String>>> {
    val runs = mutableListOf<Pair<Boolean, MutableList<String>>>()
    for(line in lines) {
        val kind = when { isOriginalLine(line) -> true; isChineseLine(line) -> false; else -> null }
        val last = runs.lastOrNull()
        if(last != null && (kind == null || kind == last.first)) last.second += line
        else if(kind != null) runs += kind to mutableListOf(line)
    }
    return runs
}

/** Chinese characters a line and its translation share, per line, with kanji in their Japanese forms. */
private fun kanjiAgreement(lyrics: BilingualLyrics): Double {
    val original = lyrics.original.lines(); val translation = lyrics.translation?.lines() ?: return 0.0
    fun han(s: String) = japaneseKanjiForms(s).filter { it in '一'..'鿿' }.toSet()
    return original.zip(translation).sumOf { (o, t) -> (han(o) intersect han(t)).size }.toDouble() / original.size.coerceAtLeast(1)
}

/**
 * Posts that give each line or verse in Chinese first, then (optionally) its romaji, then the lyrics. Chinese lines
 * before the first lyrics are a heading, and a title given only in Chinese and romaji is not a lyric line. Verses
 * that do not pair are skipped; two in a row after the lyrics mean the translator's notes have begun, as does a verse
 * without romaji in a post that gives it for nearly every verse (notes quoting Japanese). Null unless at least 80 %
 * of the sung lines are paired.
 */
private fun translationFirst(lines: List<String>): BilingualLyrics? {
    val runs = runsOf(lines)
    val original = mutableListOf<String>(); val translation = mutableListOf<String>()
    var paired = 0; var sung = 0; var misses = 0; var verses = 0; var withRomaji = 0
    for(i in runs.indices) {
        val (isOriginal, chinese) = runs[i]
        if(isOriginal) continue
        val next = runs.getOrNull(i + 1)?.second ?: break
        // Romaji written above the lyrics is left out; a verse of Latin lines only is English lyrics. Kana readings
        // and romaji written under each line are left out when that is what lets the verse pair.
        val stripped = next.dropWhile { kanaCount(it) + hanCount(it) == 0 }.ifEmpty { next }
        val lyrics = stripped.takeIf { it.size == chinese.size } ?: withoutReadings(stripped)?.takeIf { it.size == chinese.size } ?: stripped
        val japanese = lyrics.count { kanaCount(it) + hanCount(it) > 0 }
        val romaji = lyrics.size < next.size
        when {
            japanese == 0 && paired == 0 -> continue
            japanese > 0 && !romaji && paired >= 8 && verses >= 3 && withRomaji >= 0.8 * verses -> break
            lyrics.size == chinese.size || lyrics.size < chinese.size && paired == 0 -> {
                original += lyrics; translation += chinese.takeLast(lyrics.size); paired += japanese; sung += japanese; misses = 0
                verses++; if(romaji) withRomaji++
            }
            else -> { if(paired >= 8 && ++misses >= 2) break; sung += japanese }
        }
    }
    return if(original.size >= 4 && sung > 0 && paired >= 0.8 * sung) BilingualLyrics(original.joinToString("\n"), translation.joinToString("\n")) else null
}

/** Posts that put the translation under each line or verse. */
private fun translationUnder(lines: List<String>): BilingualLyrics? {
    val runs = runsOf(lines)
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
        if(next != null && (next.size == run.size || next.size > run.size && isLast)) { translation += next.take(run.size); continue }
        // A line left untranslated, such as an English phrase ("I'm dreaming"): pair the Japanese lines with the
        // Chinese ones and let the English line stand for itself.
        val sung = run.count { kanaCount(it) + hanCount(it) > 0 }
        if(next == null || sung == run.size || sung == 0 || next.size < sung || next.size > sung && !isLast)
            return lineByLine(lines) ?: BilingualLyrics(original.joinToString("\n"), null)
        var k = 0
        translation += run.map { if(kanaCount(it) + hanCount(it) > 0) next[k++] else it }
    }
    return BilingualLyrics(original.joinToString("\n"), translation.joinToString("\n"))
}

/**
 * Posts that put each line's translation right under it, where runs do not pair because a few lines were left
 * untranslated ("ん～ 1 2 3 4!") or an English line was written again as its own translation ("Yeah!" / "Yeah!").
 * Untranslated lines stand for themselves. Null unless at least 80 % of the sung lines have a Chinese line under
 * them. A Chinese line away from a lyric line ends the lyrics once most of them are paired (the translator's notes
 * follow, and may quote kana); before that it means the post is laid out some other way. Untranslated lines left at
 * the end (a source's title, a link) are not lyrics.
 */
private fun lineByLine(lines: List<String>): BilingualLyrics? {
    val original = mutableListOf<String>(); val translation = mutableListOf<String>()
    var i = 0; var paired = 0
    while(i < lines.size) {
        val line = lines[i]
        if(!isOriginalLine(line)) {
            if(isChineseLine(line) && lines.drop(i + 1).any(::isOriginalLine)) {
                val sungSoFar = original.count { kanaCount(it) + hanCount(it) > 0 }
                if(paired >= 8 && paired >= 0.8 * sungSoFar) break else return null
            }
            i++; continue
        }
        val next = lines.getOrNull(i + 1)
        original += line
        when {
            next != null && !isOriginalLine(next) && isChineseLine(next) -> { translation += next; if(kanaCount(line) + hanCount(line) > 0) paired++; i += 2 }
            next == line && kanaCount(line) + hanCount(line) == 0 -> { translation += line; i += 2 }
            else -> { translation += line; i++ }
        }
    }
    while(original.isNotEmpty() && translation.last() == original.last()) { original.removeAt(original.lastIndex); translation.removeAt(translation.lastIndex) }
    val sung = original.count { kanaCount(it) + hanCount(it) > 0 }
    return if(original.size >= 4 && sung > 0 && paired >= 0.8 * sung) BilingualLyrics(original.joinToString("\n"), translation.joinToString("\n")) else null
}

/** Traditional Chinese forms of kanji and their Japanese forms (奧 → 奥, 觸 → 触), as lyrics are typed with either. */
private val japaneseVariants=("奧奥 墮堕 屆届 裡裏 內内 眾衆 歲歳 關関 單単 戀恋 櫻桜 聲声 實実 樂楽 與与 應応 氣気 靜静 顏顔 淚涙 數数 " +
    "覺覚 變変 邊辺 圓円 會会 來来 爭争 圖図 國国 學学 體体 燈灯 雙双 臺台 燒焼 絲糸 續続 據拠 擊撃 戰戦 鐵鉄 輕軽 經経 讀読 醉酔 " +
    "壞壊 懷懐 兒児 亞亜 惡悪 壓圧 圍囲 爲為 價価 擔担 攝摂 瀨瀬 將将 從従 總総 聽聴 廳庁 廣広 黑黒 團団 傳伝 轉転 雜雑 顯顕 險険 驗験 鹽塩 " +
    "觸触 繫繋 黃黄 虛虚 處処 遙遥 著着 譯訳 驛駅 濕湿 燈灯 營営 榮栄 螢蛍 覽覧 殘残 淺浅 錢銭 發発 廢廃 澀渋 釋釈 靈霊 嶽岳 擧挙 譽誉 彈弾 纖繊 齒歯 龍竜")
            .split(' ').filter { it.length==2 }.associate { it[0] to it[1] }
/** [text] with Japanese kanji forms, so 君に觸れて and 君に触れて compare equal. */
fun japaneseKanjiForms(text: String)=buildString { text.forEach { append(japaneseVariants[it] ?: it) } }

/** A lyric line reduced to its letters and digits, to find the same line in another source's lyrics. */
private fun lineKey(s: String) = japaneseKanjiForms(normalize(s)).filter { it.isLetterOrDigit() }.map(::hiragana).joinToString("")

/** Share of [original]'s sung lines that get a line of [translation] when the two are lined up (0 to 1). */
fun translationCoverage(original: String, translation: String): Double {
    val sung = Lrc.align(Lrc.parse(original), Lrc.parse(translation)).filter { lineKey(it.text).isNotEmpty() }
    return if(sung.isEmpty()) 0.0 else sung.count { it.translation != null }.toDouble() / sung.size
}

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
    val carried = BooleanArray(lines.size)
    var rest = ""
    for((n, line) in lines.withIndex()) {
        val key = lineKey(line.text)
        when {
            key.isEmpty() -> covered++
            rest.isNotEmpty() && rest.startsWith(key) -> { rest = rest.removePrefix(key); covered++; carried[n] = true }
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
    covered += fillByTime(lines, Lrc.align(Lrc.parse(sourceOriginal), Lrc.parse(sourceTranslation)), found, carried)
    if(covered < lines.size * 0.7) return null
    if(lines.any { it.timeMs == null }) return if(found.all { it != null }) found.joinToString("\n") else null
    return lines.indices.filter { found[it] != null }.joinToString("\n") { n ->
        val ms = lines[n].timeMs!!
        String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]%s", ms / 60000, ms / 1000 % 60, ms % 1000 / 10, found[n])
    }
}

/**
 * Lines the text match left without a translation because the two sources split them differently ("でも人生は…"
 * against "人生は…", or three source lines in one): the translations of the source lines that start while the line is
 * sung, when most of each one's text is in the line. Both sides must be synced. Returns how many lines it filled.
 */
private fun fillByTime(lines: List<LyricLine>, source: List<LyricLine>, found: Array<String?>, carried: BooleanArray): Int {
    if(lines.any { it.timeMs == null } || source.none { it.timeMs != null }) return 0
    val sung = lines.indices.filter { lineKey(lines[it].text).isNotEmpty() }
    var filled = 0
    for((i, n) in sung.withIndex()) {
        if(found[n] != null || carried[n]) continue
        val key = lineKey(lines[n].text)
        val start = lines[n].timeMs!! - 1000
        val end = sung.getOrNull(i + 1)?.let { lines[it].timeMs!! - 300 } ?: (lines[n].timeMs!! + 10_000)
        val parts = source.filter { s -> s.translation != null && s.timeMs != null && s.timeMs in start until end && sharedShare(lineKey(s.text), key) >= 0.6 }
        if(parts.isEmpty()) continue
        found[n] = parts.joinToString(" ") { it.translation!! }; filled++
    }
    return filled
}

/** Share of [a]'s character pairs that also appear in [b] (0 to 1). */
private fun sharedShare(a: String, b: String): Double {
    if(a.length < 2) return if(a.isNotEmpty() && b.contains(a)) 1.0 else 0.0
    val pairs = (0 until a.length - 1).map { a.substring(it, it + 2) }
    return pairs.count { b.contains(it) }.toDouble() / pairs.size
}

/** Title text reduced for comparison: case, spaces, punctuation and decorations such as ♡ removed. */
fun looseTitleKey(s: String) = japaneseKanjiForms(normalize(s)).replace(Regex("""[\s\p{Punct}。、！？「」『』♪☆★・♡♥❤]"""), "")
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
