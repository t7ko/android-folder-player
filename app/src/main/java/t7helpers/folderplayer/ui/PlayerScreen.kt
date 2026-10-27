package t7helpers.folderplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import t7helpers.folderplayer.R
import t7helpers.folderplayer.Seek

/** The player: the file, a swipe area to scrub, the position, controls and the jump buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(player: PlayerConnection, onBack: () -> Unit) {
    val state = player.state
    var position by remember { mutableLongStateOf(player.positionMs) }
    LaunchedEffect(player) {
        while (true) {
            position = player.positionMs
            delay(POSITION_POLL_MS)
        }
    }
    val duration = state.durationMs
    val seek = { target: Long ->
        player.seekTo(target)
        position = target
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text(state.folder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            ScrubArea(state, position, onSeek = seek, modifier = Modifier.weight(1f).fillMaxWidth())

            var sliderValue by remember { mutableStateOf<Float?>(null) }
            val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Slider(
                value = sliderValue ?: fraction,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    sliderValue?.let { seek((it * duration).toLong()) }
                    sliderValue = null
                },
                enabled = duration > 0,
            )
            Row(Modifier.fillMaxWidth()) {
                val shown = sliderValue?.let { (it * duration).toLong() } ?: position
                Text(Seek.format(shown), style = MaterialTheme.typography.bodySmall)
                Text(
                    if (state.count > 0) "${state.index + 1} / ${state.count}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(if (duration > 0) Seek.format(duration) else "--:--", style = MaterialTheme.typography.bodySmall)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = player::toggleRepeat) {
                    Icon(
                        painterResource(if (state.repeatOne) R.drawable.ic_repeat_one else R.drawable.ic_repeat),
                        contentDescription = if (state.repeatOne) "Repeat one (tap for repeat all)" else "Repeat all (tap for repeat one)",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { player.previous() }) {
                    Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "Previous file")
                }
                FilledIconButton(onClick = player::playPause, modifier = Modifier.size(72.dp)) {
                    Icon(
                        painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { player.next() }) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Next file")
                }
                // Balances the repeat button, so play stays in the middle.
                Spacer(Modifier.size(48.dp))
            }

            JumpRow(sign = -1) { delta -> seek(Seek.target(player.positionMs, delta, duration)) }
            JumpRow(sign = 1) { delta -> seek(Seek.target(player.positionMs, delta, duration)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Six buttons that jump back (sign -1) or forward (sign 1) by [Seek.JUMPS_MS]. */
@Composable
private fun JumpRow(sign: Int, onJump: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (ms in Seek.JUMPS_MS) {
            OutlinedButton(
                onClick = { onJump(sign * ms) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text((if (sign < 0) "−" else "+") + Seek.label(ms), maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * The cover (or a note) and the file name. Dragging left or right here moves the position:
 * the target shows while dragging, and the player seeks there on release.
 */
@Composable
private fun ScrubArea(state: PlayerState, position: Long, onSeek: (Long) -> Unit, modifier: Modifier) {
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var startPosition by remember { mutableLongStateOf(0L) }
    val currentPosition by rememberUpdatedState(position)
    val duration by rememberUpdatedState(state.durationMs)
    val seekTo by rememberUpdatedState(onSeek)
    val target = Seek.target(startPosition, Seek.scrubDelta(dragX / width, duration), duration)

    Box(
        modifier
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (duration > 0) {
                            startPosition = currentPosition
                            dragX = 0f
                            dragging = true
                        }
                    },
                    onDragEnd = {
                        if (dragging) seekTo(Seek.target(startPosition, Seek.scrubDelta(dragX / width, duration), duration))
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, amount ->
                        if (dragging) {
                            change.consume()
                            dragX += amount
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val artwork = state.artwork
                if (artwork != null) {
                    Image(
                        artwork.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(160.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                state.name,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Swipe left or right here to seek",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (dragging) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Seek.formatDelta(target - startPosition), style = MaterialTheme.typography.headlineLarge)
                    Text("${Seek.format(target)} / ${Seek.format(duration)}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private const val POSITION_POLL_MS = 250L
