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
                if(text.isBlank()) line else tags+romajiLine(words.tokenize(japaneseForms(text))).ifBlank { text }
            }
        }
    }

    companion object {
        private val people=mapOf("一" to "hitori","二" to "futari")
        /**
         * Chinese character forms that fan posts type in Japanese lyrics (奧 for 奥), mapped to the Japanese forms the
         * dictionary knows. Used only for reading; the lyrics keep their own characters.
         */
        private val japaneseVariants=("奧奥 墮堕 屆届 裡裏 內内 眾衆 歲歳 關関 單単 戀恋 櫻桜 聲声 實実 樂楽 與与 應応 氣気 靜静 顏顔 淚涙 數数 " +
            "覺覚 變変 邊辺 圓円 會会 來来 爭争 圖図 國国 學学 體体 燈灯 雙双 臺台 燒焼 絲糸 續続 據拠 擊撃 戰戦 鐵鉄 輕軽 經経 讀読 醉酔 " +
            "壞壊 懷懐 兒児 亞亜 惡悪 壓圧 圍囲 爲為 價価 擔担 攝摂 瀨瀬 將将 從従 總総 聽聴 廳庁 廣広 黑黒 團団 傳伝 轉転 雜雑 顯顕 險険 驗験 鹽塩 " +
            "譯訳 驛駅 濕湿 燈灯 營営 榮栄 螢蛍 覽覧 殘残 淺浅 錢銭 發発 廢廃 澀渋 釋釈 靈霊 嶽岳 擧挙 譽誉 彈弾 纖繊 齒歯 龍竜")
            .split(' ').filter { it.length==2 }.associate { it[0] to it[1] }
        private fun japaneseForms(text: String)=buildString { text.forEach { append(japaneseVariants[it] ?: it) } }
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
