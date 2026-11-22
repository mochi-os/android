// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import org.mochios.android.R
import org.mochios.android.api.OwnedAddress
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.theme.LocalSpacing

/**
 * Compact inline player for one of the app's own audio attachments, streamed
 * with a credential. Drives an ExoPlayer directly rather than Media3's
 * `PlayerView` so the row is plain Compose and themes inside a chat bubble.
 */
@Composable
fun AudioPlayer(
    address: OwnedAddress,
    modifier: Modifier = Modifier
) = AudioPlayer(address.address, own = true, modifier)

/** Compact inline player for audio at [url], streamed without a credential. */
@Composable
fun AudioPlayer(
    url: String,
    modifier: Modifier = Modifier
) = AudioPlayer(url, own = false, modifier)

@OptIn(UnstableApi::class)
@Composable
private fun AudioPlayer(
    url: String,
    own: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    key(url, own) {
        AudioRow(
            build = {
                assetPlayer(context, url, own, C.AUDIO_CONTENT_TYPE_MUSIC).apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                }
            },
            modifier = modifier,
        )
    }
}

/**
 * The player's row. The player itself is made by [build] on the first tap of
 * play, not with the row: a thread holding several audio attachments opened a
 * player, a decoder and a download for each as it scrolled into view, for
 * clips mostly never played. Until then the clock reads zero, since an
 * attachment carries no length. The player is released when the row leaves
 * composition, and paused when the app goes to the background.
 */
@Composable
internal fun AudioRow(
    build: () -> Player,
    modifier: Modifier = Modifier,
) {
    var player by remember { mutableStateOf<Player?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }

    val current = player
    DisposableEffect(current) {
        if (current == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = current.duration.coerceAtLeast(0L)
                }
                if (state == Player.STATE_ENDED) {
                    current.seekTo(0)
                    current.playWhenReady = false
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        current.addListener(listener)
        // What the player reported before the listener was on.
        isPlaying = current.isPlaying
        if (current.playbackState == Player.STATE_READY) durationMs = current.duration.coerceAtLeast(0L)

        onDispose {
            current.removeListener(listener)
            current.release()
        }
    }

    PauseInBackground(current, resume = false)

    // Poll the play head while playing; ExoPlayer has no per-frame callback.
    LaunchedEffect(isPlaying, current) {
        if (current == null) return@LaunchedEffect
        while (isPlaying) {
            positionMs = current.currentPosition
            delay(200)
        }
        positionMs = current.currentPosition
    }

    val fraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val format = LocalFormat.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.gap(4.dp))
    ) {
        MochiIconButton(
            onClick = {
                val active = player ?: build().also { made -> player = made }
                if (active.playWhenReady) active.pause() else active.play()
            }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.common_pause else R.string.common_play
                ),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        // Tap or drag anywhere along the bar to seek; the slim track keeps the
        // touch target generous while the indicator itself stays thin.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .pointerInput(durationMs, current) {
                    detectTapGestures { offset ->
                        seekToX(current, durationMs, offset.x, size.width) { positionMs = it }
                    }
                }
                .pointerInput(durationMs, current) {
                    detectHorizontalDragGestures { change, _ ->
                        seekToX(current, durationMs, change.position.x, size.width) { positionMs = it }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }

        Text(
            text = "${format.formatDuration(positionMs)} / ${format.formatDuration(durationMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Seeks [player] to the fraction of [durationMs] at horizontal pixel [x] of [width]. */
private fun seekToX(
    player: Player?,
    durationMs: Long,
    x: Float,
    width: Int,
    onSeek: (Long) -> Unit
) {
    if (player == null || durationMs <= 0L || width <= 0) return
    val target = ((x / width).coerceIn(0f, 1f) * durationMs).toLong()
    onSeek(target)
    player.seekTo(target)
}
