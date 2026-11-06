// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import dagger.hilt.android.EntryPointAccessors
import org.mochios.android.api.AssetHttpEntryPoint
import org.mochios.android.api.OWNED
import org.mochios.android.api.owned
import org.mochios.android.api.resolved

/**
 * A video at [url]: one of the app's own server-relative addresses, streamed
 * with the session, or any other URL, streamed without (see `owned`).
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serverUrl = rememberServerUrl()
    val exoPlayer = remember(url) { assetPlayer(context, url, serverUrl) }

    DisposableEffect(url) {
        val mediaItem = MediaItem.fromUri(resolved(serverUrl, url))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        onDispose {
            exoPlayer.release()
        }
    }

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
 * A player streaming [url] through the asset client. When [url] is one of the
 * app's own addresses ([owned]), the request for it, resolved onto [server], is
 * marked [OWNED], so the asset client sends it the session and the app's token
 * as a session-gated attachment needs. No other request the player makes is: a
 * media file may name further addresses, chosen by whoever made it.
 */
@OptIn(UnstableApi::class)
internal fun assetPlayer(context: Context, url: String, server: String): ExoPlayer {
    val client = EntryPointAccessors
        .fromApplication(context.applicationContext, AssetHttpEntryPoint::class.java)
        .assetHttpClient()
    val own = if (owned(url)) resolved(server, url) else null
    val source = ResolvingDataSource.Factory(OkHttpDataSource.Factory(client)) { spec -> marked(spec, own) }
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(source))
        .build()
}

/** [spec] marked [OWNED] when it asks for [own], the player's own address; any other as given. */
@OptIn(UnstableApi::class)
internal fun marked(spec: DataSpec, own: String?): DataSpec =
    if (own != null && spec.uri.toString() == own) spec.withAdditionalHeaders(mapOf(OWNED to "1")) else spec
