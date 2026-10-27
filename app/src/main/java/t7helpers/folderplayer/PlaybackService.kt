package t7helpers.folderplayer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import t7helpers.folderplayer.ui.MainActivity

/** A file of the play queue: what a [MediaItem] carries in this app. */
data class QueueItem(val uri: String, val name: String, val folder: String) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(uri)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name.substringBeforeLast('.').ifEmpty { name })
                .setArtist(folder)
                .setDisplayTitle(name)
                .build(),
        )
        .build()
}

/**
 * Plays the queue (one folder's files) with ExoPlayer behind a media session: playback goes
 * on with the app closed, with the notification, lock screen and headset controls.
 * The queue, the file and the position are stored, so they are back after a restart.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val savePeriodically = object : Runnable {
        override fun run() {
            session?.player?.let { if (it.isPlaying) PlaybackStore.savePosition(this@PlaybackService, it) }
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.repeatMode = if (Settings.repeatOne(this)) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
        PlaybackStore.load(this)?.let { saved ->
            player.setMediaItems(saved.items.map { it.toMediaItem() }, saved.index, saved.positionMs)
            player.prepare()
        }
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) PlaybackStore.saveQueue(this@PlaybackService, player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = PlaybackStore.savePosition(this@PlaybackService, player)

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = PlaybackStore.savePosition(this@PlaybackService, player)

            override fun onRepeatModeChanged(repeatMode: Int) = Settings.setRepeatOne(this@PlaybackService, repeatMode == Player.REPEAT_MODE_ONE)
        })
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_PLAYER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
        handler.postDelayed(savePeriodically, SAVE_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** The app was swiped away: keep playing if it plays, otherwise stop. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(savePeriodically)
        session?.run {
            PlaybackStore.savePosition(this@PlaybackService, player)
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // onPlaybackResumption is marked unstable in Media3 1.8.
    @OptIn(UnstableApi::class)
    private inner class SessionCallback : MediaSession.Callback {
        /** Items from controllers arrive without their URI; it is the media ID here. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.buildUpon().setUri(Uri.parse(it.mediaId)).build() }.toMutableList())

        /** Play from a headset or the system's media controls after the process was gone. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = PlaybackStore.load(this@PlaybackService)
                ?: return Futures.immediateFailedFuture(IllegalStateException("Nothing played yet"))
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(saved.items.map { it.toMediaItem() }, saved.index, saved.positionMs),
            )
        }
    }

    companion object {
        private const val SAVE_INTERVAL_MS = 10_000L
    }
}

/** The stored queue (`files/queue.json`, written when it changes) and position (preferences). */
object PlaybackStore {
    class Saved(val items: List<QueueItem>, val index: Int, val positionMs: Long)

    private const val PREFS = "playback"

    private fun queueFile(context: Context) = File(context.filesDir, "queue.json")

    fun saveQueue(context: Context, player: Player) {
        val array = JSONArray()
        for (i in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(i)
            val metadata = item.mediaMetadata
            array.put(
                JSONObject().put("uri", item.mediaId)
                    .put("name", metadata.displayTitle?.toString() ?: "")
                    .put("folder", metadata.artist?.toString() ?: ""),
            )
        }
        val file = queueFile(context)
        val temp = File(file.path + ".tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    fun savePosition(context: Context, player: Player) {
        if (player.mediaItemCount == 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("index", player.currentMediaItemIndex)
            .putLong("position", player.currentPosition)
            .apply()
    }

    fun load(context: Context): Saved? = runCatching {
        val array = JSONArray(queueFile(context).readText())
        val items = List(array.length()) { i ->
            array.getJSONObject(i).let { QueueItem(it.getString("uri"), it.getString("name"), it.getString("folder")) }
        }
        if (items.isEmpty()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val index = prefs.getInt("index", 0).coerceIn(items.indices)
        Saved(items, index, prefs.getLong("position", 0).coerceAtLeast(0))
    }.getOrNull()
}
