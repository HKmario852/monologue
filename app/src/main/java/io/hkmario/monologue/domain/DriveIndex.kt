package io.hkmario.monologue.domain

import kotlinx.collections.immutable.*

data class DriveNode(val id: String, val name: String, val parent: String?, val owned: Boolean = true)
data class FolderStat(val folders: Int = 0, val tracks: Int = 0, val bytes: Long = 0, val saved: Int = 0)

/** Virtual folder for items whose parent is not visible to this account (typically "Shared with me"). */
const val SHARED_FOLDER = "shared"
/** Virtual folder for the account's own top-level folders outside My Drive, i.e. Drive for desktop "Computers" backups. */
const val COMPUTERS_FOLDER = "computers"

/**
 * Local view of the Drive music tree. Built from one folder listing plus one audio listing,
 * so browsing folders and counting songs never waits on the network.
 */
data class DriveIndex(val nodes: PersistentMap<String, DriveNode> = persistentMapOf(), val stats: PersistentMap<String, FolderStat> = persistentMapOf()) {
    fun children(id: String) = nodes.values.filter { it.parent == id }.sortedBy { normalize(it.name) }
    fun stat(id: String) = stats[id] ?: FolderStat()
    fun name(id: String) = when (id) { "root" -> "我的雲端硬碟"; SHARED_FOLDER -> "與我共用"; COMPUTERS_FOLDER -> "電腦"; else -> nodes[id]?.name ?: "資料夾" }
    /** True when [folder] is [ancestor] or lies beneath it. */
    fun within(folder: String, ancestor: String): Boolean {
        var current: String? = folder; var guard = 0
        while (current != null && guard++ < 64) { if (current == ancestor) return true; current = nodes[current]?.parent }
        return false
    }
    fun tracksUnder(id: String, tracks: List<Track>) = tracks.filter { within(it.folder, id) }

    companion object {
        /** Maps the real root ID to "root" and unreachable parents to [SHARED_FOLDER]. */
        fun build(rootId: String, folders: List<DriveNode>, tracks: List<Track>): Pair<DriveIndex, List<Track>> {
            val known = folders.map { it.id }.toSet()
            fun parentOf(p: String?) = when { p == null -> SHARED_FOLDER; p == rootId || p == "root" -> "root"; p in known -> p; else -> SHARED_FOLDER }
            // Parentless folders the account owns are computer backups; parentless or hidden-parent ones it does not own were shared.
            val nodes = folders.associate { it.id to it.copy(parent = parentOf(it.parent).let { p -> if (p == SHARED_FOLDER && it.owned) COMPUTERS_FOLDER else p }) }.toMutableMap()
            val placed = tracks.map { it.copy(folder = if (it.folder == SHARED_FOLDER) it.folder else parentOf(it.folder.ifBlank { null })) }
            if (placed.any { it.folder == SHARED_FOLDER } || nodes.values.any { it.parent == SHARED_FOLDER }) nodes[SHARED_FOLDER] = DriveNode(SHARED_FOLDER, "與我共用", "root")
            if (nodes.values.any { it.parent == COMPUTERS_FOLDER }) nodes[COMPUTERS_FOLDER] = DriveNode(COMPUTERS_FOLDER, "電腦", "root")
            val index = DriveIndex(nodes.toPersistentMap())
            val stats = mutableMapOf<String, FolderStat>()
            for (t in placed) {
                var current: String? = t.folder; var guard = 0
                while (current != null && guard++ < 64) {
                    val s = stats[current] ?: FolderStat()
                    stats[current] = s.copy(tracks = s.tracks + 1, bytes = s.bytes + t.bytes, saved = s.saved + if (t.offlinePath != null) 1 else 0)
                    current = if (current == "root") null else nodes[current]?.parent
                }
            }
            for (n in nodes.values) n.parent?.let { p -> val s = stats[p] ?: FolderStat(); stats[p] = s.copy(folders = s.folders + 1) }
            return index.copy(stats = stats.toPersistentMap()) to placed
        }
    }
}
