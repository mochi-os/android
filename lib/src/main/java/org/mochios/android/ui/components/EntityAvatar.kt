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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.mochios.android.R

/**
 * Circular avatar for a person entity. When `src` is provided the image is
 * loaded asynchronously and falls back to an initials circle on load failure.
 * Apps should typically point `src` at their own avatar proxy action
 * (e.g. "/feeds/<feed>/-/<post>/<comment>/asset/avatar"), keyed on a comment /
 * message / post / activity ID in that app's local DB.
 *
 * @param name   Person's display name (used for initials + content description).
 * @param src    Absolute URL for the avatar image. Null or a failing URL falls
 *               back to the initials placeholder.
 * @param seed   Stable identifier (usually the person's entity ID) used to pick
 *               a deterministic colour for the initials circle.
 * @param size   Avatar diameter.
 * @param shape  Avatar outline shape. Defaults to a full circle; pass a
 *               [RoundedCornerShape] for a squircle (e.g. seller profiles).
 * @param accent Optional hex colour ("#rrggbb"). When set, drawn as a 2dp ring
 *               in place of the default border.
 * @param containerColor Initials-circle fill. Defaults to a flat white avatar
 *               (the app-wide style); pass null to use the deterministic seeded
 *               colour instead.
 * @param contentColor Initials text colour. Defaults to black to pair with the
 *               white fill.
 * @param borderColor Hairline ring drawn around the avatar so it stays defined
 *               against the surface. Pass [Color.Transparent] to drop it; it is
 *               ignored when [accent] is set (the accent ring takes over).
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
        val context = androidx.compose.ui.platform.LocalContext.current
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
    val bg = containerColor ?: colourFromSeed(seed)
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

// Deterministic HSV → RGB colour from a string seed. Same seed → same colour.
private fun colourFromSeed(seed: String): Color {
    var h = 0
    for (c in seed) h = h * 31 + c.code
    val hue = ((h and 0x7fffffff) % 360).toFloat()
    return hsvToColor(hue, 0.55f, 0.70f)
}

private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val hp = h / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Color(r1 + m, g1 + m, b1 + m)
}

private fun parseHexColour(hex: String): Color? {
    val s = hex.trim().removePrefix("#")
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
