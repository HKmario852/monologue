package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test

class DriveIndexTest {
    private fun song(id: String, parent: String, bytes: Long = 10) = Track("drive:$id", id, uri = "", folder = parent, source = Source.Drive, bytes = bytes)

    @Test fun countsSongsRecursivelyAndMapsRealRoot() {
        val folders = listOf(DriveNode("music", "Music", "REAL"), DriveNode("band", "夜航樂團", "music"), DriveNode("album", "低潮", "band"), DriveNode("empty", "Backup", "REAL"))
        val (index, tracks) = DriveIndex.build("REAL", folders, listOf(song("a", "album"), song("b", "album"), song("c", "band"), song("d", "REAL")))
        assertEquals(3, index.stat("music").tracks)
        assertEquals(2, index.stat("album").tracks)
        assertEquals(4, index.stat("root").tracks)
        assertEquals(0, index.stat("empty").tracks)
        assertEquals(listOf("Backup", "Music"), index.children("root").map { it.name })
        assertEquals("root", tracks.first { it.id == "drive:d" }.folder)
        assertEquals(3, index.tracksUnder("music", tracks).size)
    }

    @Test fun unreachableParentsGoToSharedWithMe() {
        val (index, tracks) = DriveIndex.build("REAL", listOf(DriveNode("x", "From friend", "someone-else", owned = false)), listOf(song("s", "hidden"), song("t", "x")))
        assertEquals(SHARED_FOLDER, tracks.first { it.id == "drive:s" }.folder)
        assertEquals(2, index.stat(SHARED_FOLDER).tracks)
        assertTrue(index.children("root").any { it.id == SHARED_FOLDER })
    }

    @Test fun ownedParentlessFoldersAreComputerBackups() {
        val folders = listOf(DriveNode("pc", "我的電腦 (1)", null), DriveNode("m", "Music", "pc"))
        val (index, _) = DriveIndex.build("REAL", folders, listOf(song("a", "m")))
        assertEquals(listOf(COMPUTERS_FOLDER), index.children("root").map { it.id })
        assertEquals(1, index.stat(COMPUTERS_FOLDER).tracks)
        assertEquals(1, index.stat("root").tracks)
    }
}
