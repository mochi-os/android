package org.mochios.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Canonical Mochi list row for entity collections (chat list, feed list,
 * forum list, project list, …). Uses Material3 [ListItem] for spacing /
 * typography defaults; the leading slot is a colour-seeded circle with the
 * app's representative [icon] centred inside, so every row shows a stable
 * identity colour without needing per-entity art.
 *
 * Slots:
 * - [name] — the headline. Renders semibold and truncates.
 * - [icon] — the per-app icon drawn inside the leading circle.
 * - [subtitle] — optional supporting line; pass null/empty to keep the row
 *   single-line.
 * - [trailing] — optional composable on the trailing edge (a badge, count,
 *   overflow menu, etc.). When null, the row is flush-right.
 *
 * Use [onClick] for tap (open the entity); [onLongClick] for long-press
 * affordances like "Add to home screen" / context menus.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EntityListRow(
    name: String,
    seed: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    avatarUrl: String? = null,
) {
    val clickable = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.combinedClickable(onClick = onClick)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(clickable)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                if (!avatarUrl.isNullOrBlank()) {
                    EntityAvatar(name = name, src = avatarUrl, seed = seed, size = 40.dp)
                } else {
                    EntityIconCircle(seed = seed, icon = icon)
                }
            },
            trailingContent = trailing,
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * 40dp circle filled with a colour seeded from [seed], with [icon] drawn
 * in white at the centre. Same colour seeding as [EntityAvatar] so a single
 * entity reads consistently across surfaces (avatar vs list row).
 */
@Composable
private fun EntityIconCircle(seed: String, icon: ImageVector) {
    val bg = colourFromSeed(seed)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

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

/** Convenience divider for stacked rows. Indents past the avatar so it
 *  reads as a list separator rather than a section break. */
@Composable
fun EntityListDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}
