package t7helpers.folderplayer

import org.json.JSONArray
import org.json.JSONObject

/** One audio file of a scanned tree: its display name and its document ID. */
data class AudioFile(val name: String, val id: String)

/**
 * A scanned folder with only what the player needs: the subfolders that hold audio somewhere
 * below them, and its audio files, both in [NaturalOrder].
 */
class Folder(val name: String, val id: String, val folders: List<Folder>, val files: List<AudioFile>) {
    /** Audio files in this folder and all folders below it. */
    val total: Int = files.size + folders.sumOf { it.total }

    /**
     * The folder at [path] (folder names from here down) with that path, or, when part of it
     * is gone after a rescan, the deepest folder that still exists with the path to it.
     */
    fun resolve(path: List<String>): Pair<Folder, List<String>> {
        var folder = this
        val found = ArrayList<String>()
        for (name in path) {
            folder = folder.folders.find { it.name == name } ?: break
            found += name
        }
        return folder to found
    }
}

/** Where the browser is: a root folder (its tree URI) and the folder names below it. */
data class Location(val root: String, val path: List<String>) {
    fun up(): Location? = if (path.isEmpty()) null else Location(root, path.dropLast(1))
    fun into(name: String) = Location(root, path + name)
}

/** File names of audio files, judged by extension, then by MIME type. */
object AudioFiles {
    private val EXTENSIONS = setOf(
        "mp3", "mp2", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "amr", "awb", "mka", "3ga",
    )

    /** Playlists have audio MIME types (audio/x-mpegurl) but are not playable files. */
    private val NOT_AUDIO = setOf("m3u", "m3u8", "pls", "cue", "wpl", "xspf")

    fun isAudio(name: String, mime: String?): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in NOT_AUDIO) return false
        return extension in EXTENSIONS || mime?.startsWith("audio/") == true
    }
}

/**
 * Alphabetical order, ignoring case, in which runs of digits compare as numbers: "2 Intro"
 * before "10 Outro", as file managers sort.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isAsciiDigit() && b[j].isAsciiDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isAsciiDigit()) i++
                while (j < b.length && b[j].isAsciiDigit()) j++
                val numberA = a.substring(startA, i).trimStart('0')
                val numberB = b.substring(startB, j).trimStart('0')
                if (numberA.length != numberB.length) return numberA.length - numberB.length
                val byDigits = numberA.compareTo(numberB)
                if (byDigits != 0) return byDigits
            } else {
                val byChar = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                if (byChar != 0) return byChar
                i++
                j++
            }
        }
        val byRest = (a.length - i) - (b.length - j)
        return if (byRest != 0) byRest else a.compareTo(b)
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}

/** A child of a folder as the document provider lists it. */
data class Entry(val id: String, val name: String, val mime: String?, val isFolder: Boolean)

/** Lists the children of a folder by its document ID; throws if it cannot. */
fun interface FolderLister {
    fun list(folderId: String): List<Entry>
}

/**
 * Reads a whole tree, one listing per folder. Hidden entries (names starting with a dot)
 * and folders without audio anywhere below them are left out. A subfolder that cannot be
 * listed is skipped and counted in [unreadable]; the root failing to list throws.
 */
class Scan(
    private val lister: FolderLister,
    /** Called after each folder with the number listed so far. */
    private val onFolder: (Int) -> Unit = {},
    /** Called before each listing; throws to stop the scan. */
    private val checkCancelled: () -> Unit = {},
) {
    var folders = 0
        private set
    var unreadable = 0
        private set

    fun run(rootId: String, rootName: String): Folder = build(rootId, rootName, 0)!!

    private fun build(id: String, name: String, depth: Int): Folder? {
        checkCancelled()
        val entries = if (depth == 0) {
            lister.list(id)
        } else {
            try {
                lister.list(id)
            } catch (e: Exception) {
                unreadable++
                return null
            }
        }
        folders++
        onFolder(folders)
        val visible = entries.filter { !it.name.startsWith(".") }
        val files = visible.filter { !it.isFolder && AudioFiles.isAudio(it.name, it.mime) }
            .map { AudioFile(it.name, it.id) }
            .sortedWith(compareBy(NaturalOrder) { it.name })
        // Guards against a provider that reports a folder inside itself.
        val subfolders = if (depth >= MAX_DEPTH) emptyList() else visible.filter { it.isFolder }
            .mapNotNull { build(it.id, it.name, depth + 1) }
            .sortedWith(compareBy(NaturalOrder) { it.name })
        val folder = Folder(name, id, subfolders, files)
        return if (depth > 0 && folder.total == 0) null else folder
    }

    companion object {
        const val MAX_DEPTH = 64
    }
}

/** A tree as JSON, for the cache that shows it at once when the app starts. */
object TreeJson {
    fun write(folder: Folder): JSONObject = JSONObject()
        .put("n", folder.name)
        .put("i", folder.id)
        .put("d", JSONArray().apply { folder.folders.forEach { put(write(it)) } })
        .put("f", JSONArray().apply { folder.files.forEach { put(JSONArray().put(it.name).put(it.id)) } })

    fun read(json: JSONObject): Folder {
        val folders = json.getJSONArray("d")
        val files = json.getJSONArray("f")
        return Folder(
            json.getString("n"),
            json.getString("i"),
            List(folders.length()) { read(folders.getJSONObject(it)) },
            List(files.length()) { files.getJSONArray(it).let { file -> AudioFile(file.getString(0), file.getString(1)) } },
        )
    }
}
