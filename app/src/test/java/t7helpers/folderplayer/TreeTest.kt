package t7helpers.folderplayer

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeTest {
    /** A fake document provider: folder ID → children; IDs are paths, folders end with "/". */
    private class FakeLister(vararg paths: String) : FolderLister {
        val children = HashMap<String, MutableList<Entry>>()
        val failing = HashSet<String>()

        init {
            for (path in paths) {
                var parent = "/"
                val parts = path.trim('/').split('/')
                parts.forEachIndexed { i, name ->
                    val isFolder = i < parts.size - 1 || path.endsWith("/")
                    val id = parent + name + if (isFolder) "/" else ""
                    val list = children.getOrPut(parent) { ArrayList() }
                    if (list.none { it.id == id }) list += Entry(id, name, if (isFolder) "vnd.android.document/directory" else null, isFolder)
                    if (isFolder) children.getOrPut(id) { ArrayList() }
                    parent = id
                }
            }
        }

        override fun list(folderId: String): List<Entry> {
            if (folderId in failing) throw IOException("cannot list $folderId")
            return children[folderId] ?: throw IOException("no folder $folderId")
        }
    }

    @Test
    fun naturalOrderComparesNumbersAsNumbers() {
        val names = listOf("10 End.mp3", "2 Middle.mp3", "1 Start.mp3", "chapter 01b", "Chapter 1a", "b", "A", "02", "2")
        assertEquals(
            listOf("1 Start.mp3", "02", "2", "2 Middle.mp3", "10 End.mp3", "A", "b", "Chapter 1a", "chapter 01b"),
            names.sortedWith(NaturalOrder),
        )
    }

    @Test
    fun naturalOrderIgnoresCaseIncludingCyrillic() {
        assertEquals(listOf("альфа", "Бета", "гамма"), listOf("гамма", "Бета", "альфа").sortedWith(NaturalOrder))
        assertEquals(listOf("Track 9", "track 10", "Track 100"), listOf("Track 100", "track 10", "Track 9").sortedWith(NaturalOrder))
    }

    @Test
    fun audioFilesByExtensionOrMime() {
        assertTrue(AudioFiles.isAudio("book.M4B", null))
        assertTrue(AudioFiles.isAudio("song.mp3", "application/octet-stream"))
        assertTrue(AudioFiles.isAudio("noext", "audio/mpeg"))
        assertFalse(AudioFiles.isAudio("list.m3u", "audio/x-mpegurl"))
        assertFalse(AudioFiles.isAudio("cover.jpg", "image/jpeg"))
        assertFalse(AudioFiles.isAudio("notes.txt", null))
    }

    @Test
    fun scanKeepsAudioSortsAndPrunes() {
        val lister = FakeLister(
            "Books/Book 10/01.mp3",
            "Books/Book 2/10.mp3",
            "Books/Book 2/9.mp3",
            "Books/Book 2/cover.jpg",
            "Books/Empty/",
            "Books/Pictures/a.jpg",
            "Books/Deep/er/still/x.ogg",
            "Books/.hidden/secret.mp3",
            "Books/.nomedia.mp3",
            "top.flac",
        )
        val scan = Scan(lister)
        val root = scan.run("/", "Root")

        assertEquals(listOf("top.flac"), root.files.map { it.name })
        val books = root.folders.single()
        assertEquals(listOf("Book 2", "Book 10", "Deep"), books.folders.map { it.name })
        assertEquals(listOf("9.mp3", "10.mp3"), books.folders[0].files.map { it.name })
        assertEquals(5, root.total)
        assertEquals(4, books.total)
        assertEquals("/Books/Book 2/9.mp3", books.folders[0].files[0].id)
        assertEquals(0, scan.unreadable)
    }

    @Test
    fun scanSkipsUnreadableSubfoldersButFailsOnTheRoot() {
        val lister = FakeLister("A/1.mp3", "B/2.mp3")
        lister.failing += "/A/"
        val scan = Scan(lister)
        val root = scan.run("/", "Root")
        assertEquals(listOf("B"), root.folders.map { it.name })
        assertEquals(1, scan.unreadable)

        lister.failing += "/"
        assertTrue(runCatching { Scan(lister).run("/", "Root") }.exceptionOrNull() is IOException)
    }

    @Test
    fun scanReportsProgressAndCanBeStopped() {
        val lister = FakeLister("A/1.mp3", "B/2.mp3", "C/3.mp3")
        val counts = ArrayList<Int>()
        Scan(lister, onFolder = { counts += it }).run("/", "Root")
        assertEquals(listOf(1, 2, 3, 4), counts)

        var listed = 0
        val stopped = runCatching {
            Scan(lister, onFolder = { listed = it }, checkCancelled = { if (listed >= 2) throw InterruptedException() }).run("/", "Root")
        }
        assertTrue(stopped.exceptionOrNull() is InterruptedException)
        assertEquals(2, listed)
    }

    @Test
    fun resolveFallsBackToTheDeepestExistingFolder() {
        val root = Scan(FakeLister("A/B/1.mp3", "A/2.mp3")).run("/", "Root")
        assertEquals(listOf("A", "B"), root.resolve(listOf("A", "B")).second)
        assertEquals("B", root.resolve(listOf("A", "B")).first.name)
        val (folder, path) = root.resolve(listOf("A", "Gone", "B"))
        assertEquals("A", folder.name)
        assertEquals(listOf("A"), path)
        assertEquals(emptyList<String>(), root.resolve(listOf("X")).second)
    }

    @Test
    fun locationUpAndInto() {
        val top = Location("content://tree", emptyList())
        val deep = top.into("A").into("B")
        assertEquals(listOf("A", "B"), deep.path)
        assertEquals(top.into("A"), deep.up())
        assertEquals(null, top.up())
    }

    @Test
    fun treeJsonRoundTrip() {
        val root = Scan(FakeLister("A/B/1.mp3", "A/2 \"quoted\".mp3", "Ü/ж.ogg", "x.wav")).run("/", "Root")
        val copy = TreeJson.read(org.json.JSONObject(TreeJson.write(root).toString()))
        assertEquals(TreeJson.write(root).toString(), TreeJson.write(copy).toString())
        assertEquals(root.total, copy.total)
        assertEquals("ж.ogg", copy.resolve(listOf("Ü")).first.files.single().name)
    }
}
