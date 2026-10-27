package t7helpers.folderplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A root folder as stored: its tree URI and the name shown for it. */
data class StoredRoot(val uri: String, val name: String)

/** What the app keeps between runs, in SharedPreferences. */
object Settings {
    private const val FILE = "settings"
    private const val ROOTS = "roots"
    private const val LOCATION = "location"
    private const val REPEAT_ONE = "repeatOne"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun roots(context: Context): List<StoredRoot> {
        val json = prefs(context).getString(ROOTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { array.getJSONObject(it).let { root -> StoredRoot(root.getString("uri"), root.getString("name")) } }
        }.getOrDefault(emptyList())
    }

    fun setRoots(context: Context, roots: List<StoredRoot>) {
        val array = JSONArray()
        roots.forEach { array.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
        prefs(context).edit().putString(ROOTS, array.toString()).apply()
    }

    /** The folder the browser showed last; null for the list of roots. */
    fun location(context: Context): Location? {
        val json = prefs(context).getString(LOCATION, null) ?: return null
        return runCatching {
            val location = JSONObject(json)
            val path = location.getJSONArray("path")
            Location(location.getString("root"), List(path.length()) { path.getString(it) })
        }.getOrNull()
    }

    fun setLocation(context: Context, location: Location?) {
        val json = location?.let { JSONObject().put("root", it.root).put("path", JSONArray(it.path)).toString() }
        prefs(context).edit().putString(LOCATION, json).apply()
    }

    fun repeatOne(context: Context): Boolean = prefs(context).getBoolean(REPEAT_ONE, false)

    fun setRepeatOne(context: Context, repeatOne: Boolean) {
        prefs(context).edit().putBoolean(REPEAT_ONE, repeatOne).apply()
    }
}
