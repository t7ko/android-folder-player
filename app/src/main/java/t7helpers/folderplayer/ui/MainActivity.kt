package t7helpers.folderplayer.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import t7helpers.folderplayer.Library
import t7helpers.folderplayer.Location
import t7helpers.folderplayer.Saf
import t7helpers.folderplayer.Settings

class MainActivity : ComponentActivity() {
    /** The folder the browser shows; null for the list of roots. */
    private var location by mutableStateOf<Location?>(null)
    private var showPlayer by mutableStateOf(false)
    private lateinit var player: PlayerConnection

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            Saf.keepAccess(this, uri)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Folder Player cannot keep access to that folder.", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        Library.add(uri)
        go(Location(uri.toString(), emptyList()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Library.init(this)
        player = PlayerConnection(this)
        location = Settings.location(this)
        showPlayer = savedInstanceState?.getBoolean(KEY_PLAYER) ?: false
        if (savedInstanceState == null) handle(intent)

        setContent {
            FolderPlayerTheme {
                val roots by Library.roots.collectAsState()
                val state = player.state
                val location = location
                val root = location?.let { l -> roots.find { it.uri == l.root } }
                LaunchedEffect(location, root == null) {
                    // The root was removed.
                    if (location != null && root == null) go(null)
                }
                val playerShown = showPlayer && state.uri != null
                LaunchedEffect(playerShown) {
                    // Light status bar icons over the always-dark player.
                    if (playerShown) {
                        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
                    } else {
                        enableEdgeToEdge()
                    }
                }
                BackHandler(enabled = playerShown || location != null) {
                    if (playerShown) showPlayer = false else go(location?.up())
                }
                when {
                    playerShown -> FolderPlayerTheme(dark = true) {
                        PlayerScreen(player, onBack = { showPlayer = false })
                    }
                    location == null || root == null -> RootsScreen(
                        roots,
                        state,
                        onOpen = { go(Location(it.uri, emptyList())) },
                        onAdd = { folderPicker.launch(null) },
                        onRemove = { Library.remove(it.uri) },
                        onRescanAll = Library::rescanAll,
                        onPlayPause = player::playPause,
                        onShowPlayer = { showPlayer = true },
                    )
                    else -> FolderScreen(
                        root,
                        location,
                        state,
                        onGo = ::go,
                        onPlay = { items, index ->
                            player.play(items, index)
                            showPlayer = true
                        },
                        onRescan = { Library.rescan(root.uri) },
                        onPlayPause = player::playPause,
                        onShowPlayer = { showPlayer = true },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player.connect()
    }

    override fun onStop() {
        super.onStop()
        player.disconnect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PLAYER, showPlayer)
    }

    private fun handle(intent: Intent) {
        if (intent.action == ACTION_SHOW_PLAYER) showPlayer = true
    }

    private fun go(location: Location?) {
        this.location = location
        Settings.setLocation(this, location)
    }

    companion object {
        private const val KEY_PLAYER = "player"

        /** From the notification: opens the player screen. */
        const val ACTION_SHOW_PLAYER = "t7helpers.folderplayer.action.SHOW_PLAYER"
    }
}
