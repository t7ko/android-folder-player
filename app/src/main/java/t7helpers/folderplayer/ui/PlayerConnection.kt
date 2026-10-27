package t7helpers.folderplayer.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import t7helpers.folderplayer.PlaybackService
import t7helpers.folderplayer.QueueItem
import t7helpers.folderplayer.Seek

/** What the screens show of the player; [position] is read separately, as it changes all the time. */
data class PlayerState(
    /** The media ID (the URI) of the current file; null with nothing in the queue. */
    val uri: String? = null,
    val name: String = "",
    val folder: String = "",
    val isPlaying: Boolean = false,
    val repeatOne: Boolean = false,
    val index: Int = 0,
    val count: Int = 0,
    /** Negative while not known. */
    val durationMs: Long = C.TIME_UNSET,
    val artwork: Bitmap? = null,
)

/** The app's side of [PlaybackService]: a media controller, connected while the activity is started. */
class PlayerConnection(private val context: Context) {
    var state by mutableStateOf(PlayerState())
        private set

    private var controller: MediaController? = null
    private var pending: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var artworkSource: ByteArray? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)
    }

    fun connect() {
        if (pending != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        pending = future
        future.addListener({
            if (pending !== future) return@addListener
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            this.controller = controller
            controller.addListener(listener)
            refresh(controller)
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        pending?.let { MediaController.releaseFuture(it) }
        pending = null
        controller = null
    }

    val positionMs: Long get() = controller?.currentPosition ?: 0

    /** Plays [items] (one folder's files) from [index]; if that folder is already the queue, just goes to the file. */
    fun play(items: List<QueueItem>, index: Int) {
        val controller = controller ?: return
        val same = controller.mediaItemCount == items.size &&
            items.indices.all { controller.getMediaItemAt(it).mediaId == items[it].uri }
        if (same) {
            controller.seekTo(index, 0)
        } else {
            controller.setMediaItems(items.map { it.toMediaItem() }, index, 0)
        }
        controller.prepare()
        controller.play()
    }

    fun playPause() {
        val controller = controller ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
            if (controller.playbackState == Player.STATE_ENDED) controller.seekToDefaultPosition()
            controller.play()
        }
    }

    fun next() = controller?.seekToNextMediaItem()

    fun previous() = controller?.seekToPrevious()

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    fun jump(deltaMs: Long) {
        val controller = controller ?: return
        controller.seekTo(Seek.target(controller.currentPosition, deltaMs, controller.duration))
    }

    fun toggleRepeat() {
        val controller = controller ?: return
        controller.repeatMode = if (controller.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE
    }

    private fun refresh(player: Player) {
        val item = player.currentMediaItem
        val artworkData = player.mediaMetadata.artworkData
        if (!artworkData.contentEquals(artworkSource)) {
            artworkSource = artworkData
            state = state.copy(artwork = artworkData?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() })
        }
        state = state.copy(
            uri = item?.mediaId,
            name = item?.mediaMetadata?.displayTitle?.toString() ?: "",
            folder = item?.mediaMetadata?.artist?.toString() ?: "",
            isPlaying = player.isPlaying,
            repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE,
            index = player.currentMediaItemIndex,
            count = player.mediaItemCount,
            durationMs = player.duration,
        )
    }
}
