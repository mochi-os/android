// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.ui

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.HomeMax
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.mochios.android.launcher.Shortcuts
import org.mochios.android.launcher.openApp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.parseHexColour
import org.mochios.home.R
import org.mochios.home.repository.Tile
import org.mochios.android.R as MochiR

private const val TAG = "HomeScreen"

/** The side of the square every icon is drawn in, as on the web. */
private val ICON = 64.dp

/** The narrowest a tile may be, and the space either side of its label. */
private val TILE = 88.dp
private val PADDING = 4.dp

/** The space either side of the grid. */
private val GUTTER = 12.dp

/**
 * The size to draw an app's adaptive foreground at for its glyph to come out
 * at the web's 44 dp: the foreground draws the glyph at 58% of its canvas.
 */
private val PLAIN = 76.dp

/** The same for a glyph on a theme's tile, which the web draws at 36 dp. */
private val TILED = 62.dp

/** The tile shapes a theme can ask for, as the web's home grid draws them. */
private val MASKS: Map<String, Shape> = mapOf(
    "circle" to CircleShape,
    "square" to RectangleShape,
    "rounded" to RoundedCornerShape(22),
    "squircle" to RoundedCornerShape(28),
)

/**
 * The Mochi home screen: every app the user can open, as a grid. A tap opens
 * the app in its own task, as its launcher icon does; a long press offers to
 * put the app's icon on the phone's home screen, where the launcher allows
 * it. The grid is fetched again whenever the screen comes back into view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenNotifications: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val pinnable = remember(context) { Shortcuts.supported(context) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = { NotificationBell(onClick = onOpenNotifications) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            HomeContent(
                state = state,
                pinnable = pinnable,
                onOpen = { tile ->
                    if (!openApp(context, tile.name)) {
                        Log.w(TAG, "No launcher activity could open ${tile.name}")
                    }
                },
                onPin = { tile -> Shortcuts.pin(context, tile.name, tile.label) },
                onRetry = viewModel::refresh,
            )
        }
    }
}

/**
 * The home screen's body drawn from [state] alone: the grid once one is
 * known, else a spinner, the error, or the empty state. [pinnable] says
 * whether the launcher takes pinned shortcuts, and so whether a long press
 * offers one.
 */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    pinnable: Boolean,
    onOpen: (Tile) -> Unit,
    onPin: (Tile) -> Unit,
    onRetry: () -> Unit,
) {
    val grid = state.grid
    val error = state.error
    when {
        grid == null && state.loading -> LoadingState()
        grid == null && error != null -> ErrorState(error = error, onRetry = onRetry)
        grid == null || grid.tiles.isEmpty() -> EmptyState(
            icon = Icons.Outlined.Apps,
            title = stringResource(R.string.home_empty),
        )
        else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // The grid spreads its tiles across the row. A tile also grows to
            // hold its label's widest word, up to a third of the row, so a word
            // that could fit on a line is never split.
            val measurer = rememberTextMeasurer()
            val style = labelStyle()
            val density = LocalDensity.current
            val word = remember(grid.tiles, style, density) {
                grid.tiles.flatMap { tile -> words(tile.label) }
                    .maxOfOrNull { word -> measurer.measure(word, style, softWrap = false, maxLines = 1).size.width }
                    ?: 0
            }
            val third = (maxWidth - GUTTER * 2) / 3
            val minimum = maxOf(TILE, minOf(third, with(density) { word.toDp() } + PADDING * 2))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minimum),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = GUTTER, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(grid.tiles, key = { tile -> tile.name }) { tile ->
                    AppTile(
                        tile = tile,
                        mask = grid.mask,
                        background = grid.background,
                        onOpen = { onOpen(tile) },
                        onPin = if (pinnable) {
                            { onPin(tile) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/** A label's words, as a line may break between them and after a hyphen. */
internal fun words(label: String): List<String> =
    label.split(Regex("\\s+|(?<=-)")).filter { word -> word.isNotEmpty() }

/**
 * The label's style: a name too long for its tile wraps, hyphenated in the
 * user's language.
 */
@Composable
private fun labelStyle() = MaterialTheme.typography.labelLarge.copy(
    hyphens = Hyphens.Auto,
    lineBreak = LineBreak.Paragraph,
)

/**
 * One app: its icon over its name. [onPin] is null where the launcher takes
 * no pinned shortcuts, and then a long press does nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    tile: Tile,
    mask: String?,
    background: String?,
    onOpen: () -> Unit,
    onPin: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val more = stringResource(MochiR.string.common_more_options)
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClickLabel = if (onPin != null) more else null,
                    onLongClick = onPin?.let {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        }
                    },
                )
                .padding(horizontal = PADDING, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                AppIcon(name = tile.name, mask = mask, background = background)
                if (tile.highlight) {
                    Highlight(modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // A name too long for the tile wraps onto up to three lines - the
            // longest translated app name needs that much - and the row grows
            // to hold it; the icons stay level along the row.
            Text(
                text = tile.label,
                style = labelStyle(),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onPin != null) {
            MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(MochiR.string.launcher_add_to_home)) },
                    leadingIcon = { Icon(Icons.Outlined.HomeMax, contentDescription = null) },
                    onClick = {
                        menu = false
                        onPin()
                    },
                )
            }
        }
    }
}

/**
 * An app's glyph, the adaptive foreground its module ships as
 * `ic_<name>_foreground`. Plain, it is drawn in the theme's primary colour;
 * on a theme's [mask] it is white on a tile of the theme's [background], or
 * of the primary colour when the theme names none. The name under it labels
 * the tile, so the glyph is decoration.
 */
@Composable
private fun AppIcon(name: String, mask: String?, background: String?) {
    val context = LocalContext.current
    val glyph = remember(name) {
        @Suppress("DiscouragedApi")
        context.resources.getIdentifier("ic_${name}_foreground", "mipmap", context.packageName)
    }
    val primary = MaterialTheme.colorScheme.primary
    val shape = mask?.let { MASKS[it] }
    if (shape != null) {
        Box(
            modifier = Modifier
                .size(ICON)
                .clip(shape)
                .background(parseHexColour(background) ?: primary),
            contentAlignment = Alignment.Center,
        ) {
            if (glyph != 0) {
                Image(
                    painter = painterResource(glyph),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.requiredSize(TILED),
                )
            }
        }
    } else {
        Box(modifier = Modifier.size(ICON), contentAlignment = Alignment.Center) {
            if (glyph != 0) {
                Image(
                    painter = painterResource(glyph),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(primary),
                    modifier = Modifier.requiredSize(PLAIN),
                )
            }
        }
    }
}

/** A pulsing dot asking for attention, as the web draws on Help until it is opened. */
@Composable
private fun Highlight(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val pulse = rememberInfiniteTransition(label = "highlight")
    val grow by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "grow",
    )
    val fade by pulse.animateFloat(
        initialValue = 0.75f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "fade",
    )
    Box(modifier = modifier.size(12.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(grow)
                .graphicsLayer { alpha = fade }
                .background(primary, CircleShape),
        )
        Box(modifier = Modifier.size(12.dp).background(primary, CircleShape))
    }
}
