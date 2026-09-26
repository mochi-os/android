// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dagger.hilt.android.EntryPointAccessors
import org.mochios.android.R
import org.mochios.android.auth.SessionEntryPoint

/**
 * The people app's avatar path for a person entity id, as an [EntityAvatar]
 * `src`. Null when the id is blank, leaving the initials placeholder in charge.
 */
fun personAvatarPath(entityId: String?): String? =
    entityId?.takeIf { id -> id.isNotBlank() }?.let { id -> "/people/$id/-/avatar" }

/**
 * The session server URL (no trailing slash), from `SessionManager` via Hilt.
 * Returns "" outside a Hilt application (e.g. a `@Preview`).
 */
@Composable
fun rememberServerUrl(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            EntryPointAccessors
                .fromApplication(context.applicationContext, SessionEntryPoint::class.java)
                .sessionManager()
                .getServerUrlBlocking()
                .trimEnd('/')
        }.getOrDefault("")
    }
}

/**
 * Circular avatar for a person entity. [src] may be absolute or server-relative
 * ("/people/<id>/-/avatar"), which `RelativeAssetUrlMapper` expands against the
 * session server; a blank or failing URL falls back to seeded initials.
 */
@Composable
fun EntityAvatar(
    name: String,
    src: String? = null,
    seed: String? = null,
    size: Dp = 24.dp,
    shape: Shape = CircleShape,
    accent: String? = null,
    containerColor: Color? = Color.White,
    contentColor: Color = Color.Black,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    modifier: Modifier = Modifier,
) {
    var loadFailed by remember(src) { mutableStateOf(false) }

    val ringColor = accent?.let { parseHexColour(it) }
    val ringModifier = when {
        ringColor != null -> Modifier.border(2.dp, ringColor, shape)
        borderColor != Color.Transparent -> Modifier.border(1.dp, borderColor, shape)
        else -> Modifier
    }
    val outer = modifier.size(size).then(ringModifier).clip(shape)

    val useImage = !src.isNullOrBlank() && !loadFailed
    if (useImage) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(src)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.entity_avatar_alt, name),
            contentScale = ContentScale.Crop,
            onError = { loadFailed = true },
            modifier = outer,
        )
    } else {
        InitialsPlaceholder(
            name = name,
            seed = seed ?: name,
            size = size,
            containerColor = containerColor,
            contentColor = contentColor,
            modifier = outer,
        )
    }
}

@Composable
private fun InitialsPlaceholder(
    name: String,
    seed: String,
    size: Dp,
    containerColor: Color?,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val bg = containerColor ?: seededEntityColor(seed)
    val initials = initialsOf(name)
    // Text size scales with the circle (~40% of diameter keeps initials snug).
    val fontSize = (size.value * 0.4f).coerceIn(8f, 28f).sp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(bg),
    ) {
        Text(
            text = initials,
            color = contentColor,
            style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Medium),
        )
    }
}

private fun initialsOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val parts = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        else -> parts[0].take(2).uppercase()
    }
}

/**
 * A `#rgb` or `#rrggbb` string as a [Color], or null when it is neither - the
 * shape servers send an accent colour in.
 *
 * @param hex Colour string, with or without the leading `#`.
 * @return The parsed colour, or null if [hex] is null or malformed.
 */
fun parseHexColour(hex: String?): Color? {
    val s = hex?.trim()?.removePrefix("#") ?: return null
    return try {
        when (s.length) {
            6 -> Color(
                red = s.substring(0, 2).toInt(16) / 255f,
                green = s.substring(2, 4).toInt(16) / 255f,
                blue = s.substring(4, 6).toInt(16) / 255f,
            )
            3 -> Color(
                red = (s[0].digitToInt(16) * 17) / 255f,
                green = (s[1].digitToInt(16) * 17) / 255f,
                blue = (s[2].digitToInt(16) * 17) / 255f,
            )
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
