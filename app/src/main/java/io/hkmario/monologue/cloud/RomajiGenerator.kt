package io.hkmario.monologue.cloud

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Romaji made on the device for Japanese lyrics whose source has none. Kuromoji splits each line into words and
 * gives their readings from the bundled IPADIC dictionary, so kanji are read in context; nothing leaves the phone.
 * Readings of names and unusual words can be wrong, which the lyrics view says. The dictionary loads on first use.
 */
class RomajiGenerator {
    private val lock=Mutex()
    private var tokenizer: Tokenizer?=null

    /** Romaji with the same lines and timestamps as [lyrics], so it lines up one to one. */
    suspend fun generate(lyrics: String): String = withContext(Dispatchers.Default) {
        lock.withLock {
            val words=tokenizer ?: Tokenizer().also { tokenizer=it }
            lyrics.lines().joinToString("\n") { line ->
                val (tags,text)=leadingTags.find(line)!!.destructured
                if(text.isBlank()) line else tags+romajiLine(words.tokenize(text)).ifBlank { text }
            }
        }
    }

    companion object {
        private val people=mapOf("一" to "hitori","二" to "futari")
        private val leadingTags=Regex("""^((?:\s*\[[^\]]*])*)(.*)$""")
        private val punctuation=mapOf("、" to ",", "。" to ".", "？" to "?", "！" to "!", "　" to " ", "「" to "\"", "」" to "\"", "『" to "\"", "』" to "\"", "（" to "(", "）" to ")")

        /** Words separated by spaces, with endings (auxiliaries, て/で, suffixes) joined to the word before: 見ていた → miteita. */
        fun romajiLine(tokens: List<Token>): String {
            val out=StringBuilder()
            // A word ending in a small tsu (行っ) doubles the first consonant of the next one (た): itta.
            var geminate=false
            var previous=""
            var skip=false
            for((index,token) in tokens.withIndex()) {
                if(skip) { skip=false; continue }
                val surface=token.surface
                val pos1=token.partOfSpeechLevel1; val pos2=token.partOfSpeechLevel2
                if(surface.isBlank()) { if(out.isNotEmpty() && out.last()!=' ') out.append(' '); continue }
                if(pos1=="記号") {
                    val mark=punctuation[surface] ?: surface
                    if(mark=="(" || mark=="\"" && out.count { it=='"' }%2==0) { if(out.isNotEmpty() && out.last()!=' ') out.append(' ') }
                    out.append(mark); previous=pos1; continue
                }
                val kana=token.reading.takeIf { it!=null && it!="*" } ?: surface
                // Counted people read as a word, not digit by digit: 一人 → hitori, 二人 → futari.
                val pair=tokens.getOrNull(index+1)?.takeIf { it.surface=="人" }?.let { people[surface] }
                if(pair!=null) skip=true
                var word=when {
                    pair!=null -> pair
                    // The particles は and へ are pronounced wa and e; を is written wo, as lyric sites do.
                    pos1=="助詞" && surface=="は" -> "wa"
                    pos1=="助詞" && surface=="へ" -> "e"
                    pos1=="助詞" && surface=="を" -> "wo"
                    else -> kanaToRomaji(kana.trimEnd('ッ','っ'))
                }
                if(geminate && word.isNotEmpty()) word=(if(word.startsWith("ch")) "t" else if(word[0] !in "aiueon") word.substring(0,1) else "")+word
                geminate=kana.endsWith('ッ') || kana.endsWith('っ')
                // Auxiliaries join inflecting words (行きます → ikimasu) but stand apart after nouns (天気です → tenki desu).
                val attach=pos1=="助動詞" && previous in setOf("動詞","形容詞","助動詞") || pos2=="接続助詞" && surface in setOf("て","で","ば") || pos2=="非自立" && pos1=="動詞" || pos2=="接尾"
                if(out.isNotEmpty() && !attach && out.last()!=' ' && out.last()!='(' && !(out.last()=='"' && out.count { it=='"' }%2==1)) out.append(' ')
                out.append(word)
                previous=pos1
            }
            return out.toString().trim().replace(Regex(" {2,}")," ")
        }
    }
}
