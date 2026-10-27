package t7helpers.folderplayer

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/** Folders the user picked, through the Storage Access Framework. */
object Saf {
    /** Keeps read access to a picked folder across restarts. */
    fun keepAccess(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun releaseAccess(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Already gone.
        }
    }

    fun hasAccess(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    fun rootId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    /** The playable URI of a document in the tree. */
    fun documentUri(treeUri: Uri, documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    /** The name of the picked folder itself. */
    fun displayName(context: Context, treeUri: Uri): String? = try {
        context.contentResolver.query(documentUri(treeUri, rootId(treeUri)), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    /**
     * Where the picked folder is, from its document ID, which local storage writes as
     * "primary:Music/Books" or "1A2B-3C4D:Books" (a memory card).
     */
    fun describe(treeUri: Uri): String {
        val id = runCatching { rootId(treeUri) }.getOrElse { return treeUri.toString() }
        val volume = id.substringBefore(':', "")
        val path = id.substringAfter(':')
        val where = when {
            treeUri.authority != "com.android.externalstorage.documents" -> treeUri.authority ?: ""
            volume == "primary" -> "Phone"
            else -> "Card $volume"
        }
        return if (path.isEmpty()) where else "$where / $path"
    }
}

/** Lists folders of a picked tree through the document provider. */
class SafLister(private val resolver: ContentResolver, private val treeUri: Uri) : FolderLister {
    override fun list(folderId: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(uri, columns, null, null, null) ?: throw IOException("Cannot list $folderId")
        return cursor.use {
            val entries = ArrayList<Entry>(it.count)
            while (it.moveToNext()) {
                val mime = it.getString(2)
                entries += Entry(it.getString(0), it.getString(1) ?: "", mime, mime == Document.MIME_TYPE_DIR)
            }
            entries
        }
    }
}
