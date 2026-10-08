package io.hkmario.monologue.data

import android.content.Context

/**
 * Songs already looked up online, remembered for [days] across launches so the same song is not searched again on
 * every launch (each search reads several web pages; most songs have nothing new to find).
 */
class RecentLookups(context: Context, name: String, private val days: Int = 14) {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    operator fun contains(id: String) = System.currentTimeMillis() - prefs.getLong(id, 0) < days * 86_400_000L
    operator fun plusAssign(id: String) { prefs.edit().putLong(id, System.currentTimeMillis()).apply() }
    operator fun minusAssign(id: String) { prefs.edit().remove(id).apply() }
}
