// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val YOUTUBE_REGEX = Regex("""(?:youtube\.com/watch\?v=|youtu\.be/)([\w-]+)""")
private val VIMEO_REGEX = Regex("""vimeo\.com/(\d+)""")

data class VideoInfo(
    val type: String, // "youtube" or "vimeo"
    val id: String
)

/**
 * Extracts video URLs from HTML content.
 */
fun extractVideos(html: String): List<VideoInfo> {
    val videos = mutableListOf<VideoInfo>()
    YOUTUBE_REGEX.findAll(html).forEach { match ->
        videos.add(VideoInfo("youtube", match.groupValues[1]))
    }
    VIMEO_REGEX.findAll(html).forEach { match ->
        videos.add(VideoInfo("vimeo", match.groupValues[1]))
    }
    return videos.distinctBy { "${it.type}:${it.id}" }
}

/**
 * Renders an embedded video player via WebView iframe.
 */
@Composable
fun VideoEmbed(
    video: VideoInfo,
    modifier: Modifier = Modifier
) {
    val embedUrl = remember(video) {
        when (video.type) {
            "youtube" -> "https://www.youtube.com/embed/${video.id}"
            "vimeo" -> "https://player.vimeo.com/video/${video.id}"
            else -> ""
        }
    }

    if (embedUrl.isEmpty()) return

    val html = remember(embedUrl) {
        """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>body{margin:0;background:#000} iframe{width:100%;height:100%;border:none}</style>
        </head><body>
        <iframe src="$embedUrl" allowfullscreen></iframe>
        </body></html>
        """.trimIndent()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}
