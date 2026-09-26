package io.hkmario.monologue.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.collections.immutable.toPersistentMap
import io.hkmario.monologue.domain.AppSettingsUiState
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import java.io.File
import org.json.JSONObject

private val Context.settingsStore by preferencesDataStore("settings")
class SettingsRepository(private val context: Context) {
    val state = context.settingsStore.data.map { p -> AppSettingsUiState(p.asMap().entries.associate { it.key.name to it.value.toString() }.toPersistentMap()) }
    suspend fun set(key: String, value: String) { require(key.matches(Regex("[a-zA-Z0-9_.-]+"))); context.settingsStore.edit { it[stringPreferencesKey(key)] = value } }
    suspend fun snapshot() = state.first()
    suspend fun reset() { context.settingsStore.edit { it.clear() } }
    suspend fun import(values: JSONObject) {
        val safe = values.keys().asSequence().filter { it in allowedSettings && it !in setOf("lbSync","onlineLyrics","autoIncremental") }.associateWith { values.getString(it) }
        context.settingsStore.edit { prefs -> safe.forEach { (k,v) -> prefs[stringPreferencesKey(k)] = v } }
    }
    companion object {
        val allowedSettings = setOf("audioQuality","audioProvider","updateRepository","theme","dynamic","navigation","banner","vinyl","reduceMotion","language","restoreQueue","autoplay","resumeInterruption","noisyPause","speed","offlineFirst","mobileStreaming","autoScan","hiddenFolders","minDuration","defaultTab","sort","searchHistory","driveRoot","downloadLocation","driveSort","groupSort.Artists","groupSort.Albums","groupSort.Folders","wifiOnly","autoIncremental","pauseBetween","retryCount","downloadNotifications","lyricSize","translations","translationLanguage","onlineLyrics","lyricsBase","autoLyrics","lyricOffset","statistics","timezone","rankPeriod","rankTime","lbSync","sleepFade","eqEnabled")
    }
}
/** Credentials only live in encrypted app-private, no-backup storage. No token in DataStore. */
class SecretStore(context: Context) {
    private val dir = File(context.noBackupFilesDir, "credentials").apply { mkdirs() }
    private val alias = "monologue.credentials.v1"
    private fun key(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias,null) as? javax.crypto.SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun put(name: String, value: String?) {
        require(name in setOf("listenbrainz", "lb-user", "spotify-token", "spotify-user", "spotify-pkce"))
        val file = File(dir,name)
        if(value == null) { file.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val bytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val temp = File(dir,"$name.tmp")
        temp.writeText(Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(bytes,Base64.NO_WRAP))
        java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
    @Synchronized fun get(name: String): String? = runCatching {
        val file = File(dir,name); if(!file.exists()) return null
        val parts=file.readText().split(':')
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP))) }
        String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8)
    }.getOrNull()
}
