// Copyright © 2026 Mochi OÜ
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import org.mochios.android.R
import org.mochios.android.api.AssetHttpEntryPoint

/**
 * Compact inline player for audio attachments: a play/pause button on the left
 * and a seekable progress bar with elapsed / total duration on the right.
 *
 * Drives [ExoPlayer] directly (rather than Media3's video-oriented `PlayerView`)
 * so the layout is a plain Compose row that themes correctly inside a chat
 * bubble. Audio streams through the authenticated asset client, same as
 * [VideoPlayer].
 *
 * @param url absolute, session-gated audio URL.
 */
@OptIn(UnstableApi::class)
@Composable
fun AudioPlayer(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        val client = EntryPointAccessors
            .fromApplication(context.applicationContext, AssetHttpEntryPoint::class.java)
            .assetHttpClient()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(client)))
            .build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(url) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
                if (state == Player.STATE_ENDED) {
                    exoPlayer.seekTo(0)
                    exoPlayer.playWhenReady = false
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Poll the play head while playing; ExoPlayer has no per-frame callback.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = exoPlayer.currentPosition
            delay(200)
        }
        positionMs = exoPlayer.currentPosition
    }

    val fraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
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
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        seekToX(exoPlayer, durationMs, offset.x, size.width) { positionMs = it }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures { change, _ ->
                        seekToX(exoPlayer, durationMs, change.position.x, size.width) { positionMs = it }
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
            text = "${formatClock(positionMs)} / ${formatClock(durationMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Seeks [player] to the fraction of [durationMs] at horizontal pixel [x] of [width]. */
private fun seekToX(
    player: ExoPlayer,
    durationMs: Long,
    x: Float,
    width: Int,
    onSeek: (Long) -> Unit
) {
    if (durationMs <= 0L || width <= 0) return
    val target = ((x / width).coerceIn(0f, 1f) * durationMs).toLong()
    onSeek(target)
    player.seekTo(target)
}

/** Formats a millisecond position as `m:ss` (or `h:mm:ss` past an hour). */
private fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
