package t7helpers.folderplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A root folder as the screens show it. */
data class Root(
    val uri: String,
    val name: String,
    /** The last scanned tree, from the cache until a scan finishes; null before the first scan. */
    val tree: Folder? = null,
    val scanning: Boolean = false,
    /** Folders listed so far by the running scan. */
    val progress: Int = 0,
    /** Why the last scan failed, if it did. */
    val problem: String? = null,
)

/**
 * The root folders and their trees, for the whole process. Trees are cached in files, so
 * they show at once when the app starts; every root is scanned again in the background at
 * the start of the process, when it is added and when the user asks.
 */
object Library {
    private const val TAG = "FolderPlayer"

    private val _roots = MutableStateFlow<List<Root>>(emptyList())
    val roots: StateFlow<List<Root>> = _roots

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scans = ConcurrentHashMap<String, Job>()
    private lateinit var app: Context

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        _roots.value = Settings.roots(app).map { Root(it.uri, it.name) }
        scope.launch {
            for (root in _roots.value) {
                val tree = TreeCache.load(app, root.uri) ?: continue
                update(root.uri) { if (it.tree == null) it.copy(tree = tree) else it }
            }
            _roots.value.forEach { rescan(it.uri) }
        }
    }

    /** Adds a folder the user picked (and took access to), or scans it again if it is there. */
    fun add(treeUri: Uri) {
        val uri = treeUri.toString()
        if (_roots.value.none { it.uri == uri }) {
            val name = Saf.displayName(app, treeUri) ?: treeUri.lastPathSegment ?: "Folder"
            _roots.update { it + Root(uri, name) }
            saveRoots()
        }
        rescan(uri)
    }

    /** Forgets a root folder; its files are not touched. */
    fun remove(uri: String) {
        scans.remove(uri)?.cancel()
        _roots.update { roots -> roots.filter { it.uri != uri } }
        saveRoots()
        Saf.releaseAccess(app, Uri.parse(uri))
        scope.launch { TreeCache.delete(app, uri) }
    }

    fun rescanAll() = _roots.value.forEach { rescan(it.uri) }

    fun rescan(uri: String) {
        val root = _roots.value.find { it.uri == uri } ?: return
        update(uri) { it.copy(scanning = true, progress = 0, problem = null) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val treeUri = Uri.parse(uri)
            val started = System.currentTimeMillis()
            try {
                if (!Saf.hasAccess(app, treeUri)) throw SecurityException("no grant")
                val scan = Scan(
                    SafLister(app.contentResolver, treeUri),
                    onFolder = { n -> if (n % 25 == 0) update(uri) { it.copy(progress = n) } },
                    checkCancelled = { ensureActive() },
                )
                val tree = scan.run(Saf.rootId(treeUri), root.name)
                ensureActive()
                TreeCache.save(app, uri, tree)
                Log.i(TAG, "Scanned ${scan.folders} folders, ${tree.total} files in ${System.currentTimeMillis() - started} ms")
                val problem = if (scan.unreadable > 0) "${scan.unreadable} folders could not be read." else null
                finish(uri) { it.copy(tree = tree, problem = problem) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                finish(uri) { it.copy(problem = "Folder Player lost access to this folder. Add it again to restore it.") }
            } catch (e: Exception) {
                Log.w(TAG, "Scan failed", e)
                finish(uri) { it.copy(problem = "The folder could not be read: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
        scans.put(uri, job)?.cancel()
        job.start()
    }

    /** Applies the result of a scan unless a newer scan of the same root replaced it. */
    private fun CoroutineScope.finish(uri: String, change: (Root) -> Root) {
        if (scans[uri] !== coroutineContext[Job]) return
        scans.remove(uri)
        update(uri) { change(it).copy(scanning = false, progress = 0) }
    }

    private fun update(uri: String, change: (Root) -> Root) {
        _roots.update { roots -> roots.map { if (it.uri == uri) change(it) else it } }
    }

    private fun saveRoots() = Settings.setRoots(app, _roots.value.map { StoredRoot(it.uri, it.name) })
}

/** The scanned trees in `files/trees`, one JSON file per root. */
private object TreeCache {
    private const val VERSION = 1

    private fun file(context: Context, uri: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "trees"), "$hash.json")
    }

    fun load(context: Context, uri: String): Folder? = runCatching {
        val json = JSONObject(file(context, uri).readText())
        if (json.getInt("version") != VERSION) null else TreeJson.read(json.getJSONObject("tree"))
    }.getOrNull()

    fun save(context: Context, uri: String, tree: Folder) {
        val file = file(context, uri)
        file.parentFile!!.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(JSONObject().put("version", VERSION).put("tree", TreeJson.write(tree)).toString())
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    fun delete(context: Context, uri: String) {
        file(context, uri).delete()
    }
}
