package io.hkmario.monologue.cloud

import android.os.Build
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/** Simplified → Traditional Chinese with the platform ICU transliterator (Android 10+); unchanged on older versions. */
fun toTraditional(text: String): String =
    if(Build.VERSION.SDK_INT>=29) runCatching { android.icu.text.Transliterator.getInstance("Simplified-Traditional").transliterate(text) }.getOrDefault(text) else text

/** Guesses the language of lyrics from their script: kana → Japanese, Hangul → Korean, Han only → Chinese, else English. */
fun lyricsLanguage(lines: List<String>): String? {
    val text=lines.joinToString("")
    fun count(vararg scripts: Character.UnicodeScript)=text.count { c -> runCatching { Character.UnicodeScript.of(c.code) in scripts }.getOrDefault(false) }
    return when {
        count(Character.UnicodeScript.HIRAGANA,Character.UnicodeScript.KATAKANA)>=3 -> TranslateLanguage.JAPANESE
        count(Character.UnicodeScript.HANGUL)>=3 -> TranslateLanguage.KOREAN
        count(Character.UnicodeScript.HAN)>=3 -> TranslateLanguage.CHINESE
        count(Character.UnicodeScript.LATIN)>=3 -> TranslateLanguage.ENGLISH
        else -> null
    }
}

/**
 * On-device machine translation of lyrics with ML Kit. The language model (about 30 MB) is downloaded
 * once inside the app; after that, lyrics are translated offline and never sent to a translation server.
 */
class LyricsTranslator(private val context: android.content.Context) {
    /** ML Kit starts here, on the first translation, instead of while the app launches (its start-up provider is removed). */
    private val mlKit by lazy { com.google.mlkit.common.MlKit.initialize(context.applicationContext); true }
    private fun target(language: String)=when(language) { "English"->TranslateLanguage.ENGLISH; "日本語"->TranslateLanguage.JAPANESE; else->TranslateLanguage.CHINESE }

    /** Returns an LRC with the same timestamps as [lrc], or null when the lyrics are already in [language]. */
    suspend fun translate(lrc: String,language: String): String? {
        // Timed lines keep their timestamps; plain lyrics are translated line by line in the same order.
        val lines=Lrc.parse(lrc).filter { it.text.isNotBlank() }
        val source=lyricsLanguage(lines.map { it.text }) ?: return null
        val target=target(language)
        if(source==target) return null
        withContext(Dispatchers.Default) { mlKit }
        val translator=Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        try {
            translator.downloadModelIfNeeded().await()
            val done=HashMap<String,String>()
            val out=StringBuilder()
            for(line in lines) {
                currentCoroutineContext().ensureActive()
                val translated=done.getOrPut(line.text) { translator.translate(line.text).await() }
                line.timeMs?.let { ms -> out.append("[%02d:%02d.%02d]".format(ms/60000,(ms/1000)%60,(ms%1000)/10)) }
                out.append(if(language=="繁體中文") toTraditional(translated) else translated).append('\n')
            }
            return out.toString()
        } finally { translator.close() }
    }
}
