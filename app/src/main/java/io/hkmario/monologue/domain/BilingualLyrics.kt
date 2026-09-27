package io.hkmario.monologue.domain

/** Lyrics and their Chinese translation read from a fan translation post; the translation pairs with the original line by line. */
data class BilingualLyrics(val original: String, val translation: String?)

private fun kanaCount(s: String) = s.count { it in '぀'..'ヿ' && it != 'ー' && it != '・' }
private fun hanCount(s: String) = s.count { it in '一'..'鿿' }

private val endMarker = Regex("""^[\s\-—=~～*＊・.。]*(end|fin|the end|完|終|おわり|終わり)[\s\-—=~～*＊・.。]*$""")

/** A lyric line in the song's language: Japanese (kana), or Latin letters without Chinese characters (English songs). */
private fun isOriginalLine(s: String) = kanaCount(s) > 0 || hanCount(s) == 0 && s.count { it.isLetter() } >= 3
private fun isChineseLine(s: String) = kanaCount(s) == 0 && hanCount(s) > 0

/**
 * Fan translation posts (such as on 巴哈姆特) put each stanza, or each line, of the original lyrics before its
 * Chinese translation. Lines are grouped into runs of original and Chinese lines, and a run of original lines is
 * paired with the Chinese run right after it when both have the same number of lines. Credits before a "歌詞"
 * heading are skipped. When any run cannot be paired the translation is left out rather than misaligned.
 * Returns null when the post has too few lyric lines to be lyrics.
 */
fun splitBilingualLyrics(text: String): BilingualLyrics? {
    // End markers ("END", "完") close many posts; they are not lyrics.
    val all = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !endMarker.matches(normalize(it)) }
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

private fun looseKey(s: String) = normalize(s).replace(Regex("""[\s\p{Punct}。、！？「」『』♪☆★・]"""), "")
private val translationWords = Regex("""(中文|中日|日中|中英|英中)?(歌詞)?(翻譯|翻译|中譯|歌詞)$""")

/** Whether a post title such as "【東方Vocal】FELT｜Time and again (中文翻譯)" is about the song [title]. */
fun postTitleMentionsSong(postTitle: String, title: String): Boolean {
    val cleaned = withoutBracketNotes(postTitle)
    val parts = cleaned.split(Regex("""[｜|／/－—–~～]|\s-\s""")).map { it.trim().replace(translationWords, "").trim() } + cleaned
    if(parts.any { sameTitle(it, title) }) return true
    val key = looseKey(withoutBracketNotes(title))
    return key.length >= 4 && looseKey(cleaned).contains(key)
}
