package t7helpers.folderplayer.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import t7helpers.folderplayer.Folder
import t7helpers.folderplayer.Location
import t7helpers.folderplayer.QueueItem
import t7helpers.folderplayer.R
import t7helpers.folderplayer.Root
import t7helpers.folderplayer.Saf

/** The list of root folders: the level above every root. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RootsScreen(
    roots: List<Root>,
    player: PlayerState,
    onOpen: (Root) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Root) -> Unit,
    onRescanAll: () -> Unit,
    onPlayPause: () -> Unit,
    onShowPlayer: () -> Unit,
) {
    var removing by remember { mutableStateOf<Root?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folder Player") },
                actions = {
                    if (roots.isNotEmpty()) {
                        IconButton(onClick = onRescanAll) { Icon(Icons.Filled.Refresh, contentDescription = "Scan all again") }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add folder") },
            )
        },
        bottomBar = { MiniPlayer(player, onPlayPause, onShowPlayer) },
    ) { padding ->
        if (roots.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Add a folder with audio files. Folder Player shows its subfolders and plays " +
                        "the files of one folder in a row. You can add several folders.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(roots, key = { it.uri }) { root ->
                    ListItem(
                        headlineContent = { Text(root.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text(Saf.describe(Uri.parse(root.uri)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(rootStatus(root), color = if (root.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = { Icon(painterResource(R.drawable.ic_storage), contentDescription = null) },
                        modifier = Modifier.combinedClickable(onClick = { onOpen(root) }, onLongClick = { removing = root }),
                    )
                }
                item {
                    Text(
                        "Long-press a folder to remove it from the list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
    removing?.let { root ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${root.name}?") },
            text = { Text("The folder leaves this list. Its files stay where they are.") },
            confirmButton = { TextButton(onClick = { removing = null; onRemove(root) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

private fun rootStatus(root: Root): String = when {
    root.scanning && root.tree == null -> "Scanning… ${root.progress} folders"
    root.problem != null -> root.problem
    root.tree != null -> filesText(root.tree.total) + if (root.scanning) " · scanning…" else ""
    else -> ""
}

private fun filesText(n: Int) = if (n == 1) "1 file" else "$n files"

/** One folder of a root: its subfolders (only those with audio below them), then its audio files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    root: Root,
    location: Location,
    player: PlayerState,
    onGo: (Location?) -> Unit,
    onPlay: (List<QueueItem>, Int) -> Unit,
    onRescan: () -> Unit,
    onPlayPause: () -> Unit,
    onShowPlayer: () -> Unit,
) {
    val tree = root.tree
    val resolved = tree?.resolve(location.path)
    // A folder that is gone after a rescan: show the nearest one that is still there.
    LaunchedEffect(resolved?.second) {
        if (resolved != null && resolved.second != location.path) onGo(Location(location.root, resolved.second))
    }
    val folderName = location.path.lastOrNull() ?: root.name
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onGo(location.up()) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                        }
                    },
                    title = {
                        Column {
                            Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (location.path.isNotEmpty()) {
                                Text(
                                    (listOf(root.name) + location.path.dropLast(1)).joinToString(" / "),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onRescan, enabled = !root.scanning) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Scan again")
                        }
                    },
                )
                if (root.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = { MiniPlayer(player, onPlayPause, onShowPlayer) },
    ) { padding ->
        when {
            resolved == null -> Message(padding, if (root.scanning) "Scanning… ${root.progress} folders" else root.problem ?: "")
            else -> FolderList(root, location, resolved.first, player, padding, onGo, onPlay)
        }
    }
}

/** Where each folder's list was scrolled to, so going up returns to the same place. */
private val scrollPositions = HashMap<Location, Pair<Int, Int>>()

@Composable
private fun FolderList(
    root: Root,
    location: Location,
    folder: Folder,
    player: PlayerState,
    padding: PaddingValues,
    onGo: (Location?) -> Unit,
    onPlay: (List<QueueItem>, Int) -> Unit,
) {
    val treeUri = remember(root.uri) { Uri.parse(root.uri) }
    val fileUris = remember(folder) { folder.files.map { Saf.documentUri(treeUri, it.id).toString() } }
    val folderName = location.path.lastOrNull() ?: root.name
    val saved = scrollPositions[location]
    val listState = remember(location) { androidx.compose.foundation.lazy.LazyListState(saved?.first ?: 0, saved?.second ?: 0) }
    DisposableEffect(location) {
        onDispose { scrollPositions[location] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
    }
    if (folder.folders.isEmpty() && folder.files.isEmpty()) {
        Message(padding, root.problem ?: "No audio files here.")
        return
    }
    LazyColumn(state = listState, contentPadding = padding) {
        if (root.problem != null) {
            item { Text(root.problem, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        }
        items(folder.folders, key = { "d/" + it.name }) { sub ->
            ListItem(
                headlineContent = { Text(sub.name) },
                supportingContent = { Text(filesText(sub.total)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onGo(location.into(sub.name)) },
            )
        }
        items(folder.files.size, key = { "f/" + folder.files[it].id }) { index ->
            val current = fileUris[index] == player.uri
            ListItem(
                headlineContent = {
                    Text(folder.files[index].name, fontWeight = if (current) FontWeight.Bold else null)
                },
                leadingContent = {
                    Icon(
                        painterResource(if (current && player.isPlaying) R.drawable.ic_play else R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = if (current) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
                modifier = Modifier.clickable {
                    onPlay(folder.files.mapIndexed { i, file -> QueueItem(fileUris[i], file.name, folderName) }, index)
                },
            )
        }
    }
}

@Composable
private fun Message(padding: PaddingValues, text: String) {
    Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The bar at the bottom of the browser while something is in the queue; a tap opens the player. */
@Composable
fun MiniPlayer(player: PlayerState, onPlayPause: () -> Unit, onShowPlayer: () -> Unit) {
    if (player.uri == null) return
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onShowPlayer)) {
        Row(
            Modifier.navigationBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(player.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(player.folder, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    painterResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                )
            }
        }
    }
}
