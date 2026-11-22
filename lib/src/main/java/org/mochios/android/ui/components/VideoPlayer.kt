// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import dagger.hilt.android.EntryPointAccessors
import org.mochios.android.api.AssetHttpEntryPoint
import org.mochios.android.api.OWNED
import org.mochios.android.api.OwnedAddress

/** One of the app's own videos, at [address], streamed with a credential. */
@Composable
fun VideoPlayer(
    address: OwnedAddress,
    modifier: Modifier = Modifier
) = VideoPlayer(address.address, own = true, modifier)

/** A video at [url], streamed without a credential. */
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier
) = VideoPlayer(url, own = false, modifier)

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    url: String,
    own: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember(url, own) { assetPlayer(context, url, own, C.AUDIO_CONTENT_TYPE_MOVIE) }

    DisposableEffect(url, own) {
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        onDispose {
            exoPlayer.release()
        }
    }

    // The video fills the screen it was opened on, so it carries on where it
    // was when the reader comes back to it.
    PauseInBackground(exoPlayer, resume = true)

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier
    )
}

/**
 * Pauses [player] when the app goes to the background. Leaving the app does
 * not leave composition, so without this a playing clip went on sounding
 * behind the launcher, with no notification or media session to stop it by.
 * With [resume], a player this paused plays again when the app returns.
 */
@Composable
internal fun PauseInBackground(player: Player?, resume: Boolean) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player, resume) {
        var paused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (player != null && player.playWhenReady) {
                    paused = true
                    player.pause()
                }

                Lifecycle.Event.ON_START -> if (paused) {
                    paused = false
                    if (resume) player?.play()
                }

                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/**
 * A player streaming [url] through the asset client. When [own], [url] is one
 * of the app's own addresses and the request for it is marked [OWNED], so the
 * asset client sends it the credential a session-gated attachment needs. No
 * other request the player makes is: a media file may name further addresses,
 * chosen by whoever made it. Server-relative addresses are resolved onto the
 * server as each request is made ([marked]), so the player needs no address
 * of its own and follows a change of server. [content] is the kind of sound it
 * plays, one of the `C.AUDIO_CONTENT_TYPE_*` values.
 */
@OptIn(UnstableApi::class)
internal fun assetPlayer(context: Context, url: String, own: Boolean, content: Int): ExoPlayer {
    val entry = EntryPointAccessors.fromApplication(context.applicationContext, AssetHttpEntryPoint::class.java)
    val client = entry.assetHttpClient()
    val sessions = entry.sessionManager()
    val mine = if (own) url else null
    val source = ResolvingDataSource.Factory(OkHttpDataSource.Factory(client)) { spec ->
        marked(spec, mine, sessions.server.value)
    }
    return mediaPlayer(context, DefaultMediaSourceFactory(source), content)
}

/**
 * A player for [sources] that takes audio focus as a media app should: it asks
 * for focus before it sounds, so it pauses the reader's music or podcast (and
 * another attachment playing in the app) rather than playing over it, gives
 * way to a call, and pauses when headphones are unplugged.
 */
@OptIn(UnstableApi::class)
internal fun mediaPlayer(context: Context, sources: MediaSource.Factory, content: Int): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMediaSourceFactory(sources)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(content).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

/**
 * [spec] as it is sent: a server-relative address resolved onto [server], and
 * marked [OWNED] when it is [own], the player's own address; any other as
 * given.
 */
@OptIn(UnstableApi::class)
internal fun marked(spec: DataSpec, own: String?, server: String): DataSpec {
    val address = spec.uri.toString()
    if (spec.uri.scheme != null || !address.startsWith("/") || address.startsWith("//")) return spec
    val absolute = spec.withUri(Uri.parse(server.trimEnd('/') + address))
    return if (own != null && address == own) absolute.withAdditionalHeaders(mapOf(OWNED to "1")) else absolute
}
