package io.hkmario.monologue.domain

/** Hepburn romaji for kana, as lyric sites write it: ありがとう → arigatou, ハート → haato, がっこう → gakkou. */
private val kanaPairs = mapOf(
    "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo", "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
    "シャ" to "sha", "シュ" to "shu", "ショ" to "sho", "シェ" to "she", "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo", "ジェ" to "je",
    "チャ" to "cha", "チュ" to "chu", "チョ" to "cho", "チェ" to "che", "ヂャ" to "ja", "ヂュ" to "ju", "ヂョ" to "jo",
    "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo", "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
    "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo", "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
    "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo", "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
    "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo", "ティ" to "ti", "ディ" to "di", "トゥ" to "tu", "ドゥ" to "du",
    "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo", "ヴァ" to "va", "ヴィ" to "vi", "ヴェ" to "ve", "ヴォ" to "vo", "ツァ" to "tsa",
)
private val kanaSingles = mapOf(
    'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o", 'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
    'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go", 'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
    'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo", 'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
    'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do", 'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
    'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho", 'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
    'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po", 'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
    'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo", 'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
    'ワ' to "wa", 'ヰ' to "i", 'ヱ' to "e", 'ヲ' to "wo", 'ン' to "n", 'ヴ' to "vu",
    'ァ' to "a", 'ィ' to "i", 'ゥ' to "u", 'ェ' to "e", 'ォ' to "o", 'ャ' to "ya", 'ュ' to "yu", 'ョ' to "yo", 'ヮ' to "wa", 'ヵ' to "ka", 'ヶ' to "ke",
)

/** Hiragana → katakana; other characters unchanged. */
fun toKatakana(text: String) = buildString { text.forEach { append(if(it in 'ぁ'..'ゖ') it + 0x60 else it) } }

/** Romaji for kana text; characters that are not kana are kept as they are. */
fun kanaToRomaji(text: String): String {
    val kana = toKatakana(text)
    val out = StringBuilder()
    var geminate = false
    var i = 0
    while(i < kana.length) {
        val c = kana[i]
        val syllable = kanaPairs[kana.substring(i, minOf(i + 2, kana.length))]?.also { i += 2 } ?: kanaSingles[c]?.also { i += 1 }
        if(syllable == null) {
            i += 1
            when(c) {
                'ッ' -> { geminate = true; continue }
                // A long-vowel mark repeats the vowel before it: ハート → haato.
                'ー' -> { val last = out.lastOrNull(); if(last != null && last in "aiueo") out.append(last) }
                else -> out.append(c)
            }
            geminate = false
            continue
        }
        // A small tsu doubles the next consonant: がっこう → gakkou, まっちゃ → matcha.
        if(geminate) {
            if(syllable.startsWith("ch")) out.append('t') else if(syllable[0] !in "aiueon") out.append(syllable[0])
            geminate = false
        }
        out.append(syllable)
    }
    return out.toString()
}
