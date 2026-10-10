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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.mochios.android.R as MochiR
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.launcher.Shortcuts
import org.mochios.android.launcher.openApp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.parseHexColour
import org.mochios.android.ui.theme.oklch
import org.mochios.android.ui.theme.oklchOf
import org.mochios.home.R
import org.mochios.home.repository.Tile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "HomeScreen"

/** The glow at the top of the page: its colour's strength, where it fades out, and how far down it reaches. */
private const val GLOW_STRENGTH = 0.12f
private const val GLOW_FADE = 0.7f
private val GLOW_HEIGHT = 420.dp
private val SQRT2 = sqrt(2f)

/** The wordmark gradient's direction, in degrees clockwise from up. */
private const val WORDMARK_ANGLE = 165f

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
    onLogout: () -> Unit,
    onOpenLink: (String) -> Unit,
    onManageCategories: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    menu: HomeMenuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val pinnable = remember(context) { Shortcuts.supported(context) }
    val menuState by menu.state.collectAsState()
    val count by menu.count.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }
    // The list follows the count while the menu is open, as a notification
    // arrives or is read elsewhere.
    LaunchedEffect(open, count) {
        if (open) menu.refresh()
    }

    HomePage(
        glow = LocalFormat.current.preferences.background,
        navigation = {
            UserButton(name = menuState.name, identity = menuState.identity, count = count) { open = true }
        },
    ) {
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

    if (open) {
        val close = {
            open = false
            menu.closePicker()
        }
        MochiBottomSheet(onDismissRequest = close) {
            UserMenu(
                state = menuState,
                onLogout = {
                    close()
                    onLogout()
                },
                onReadAll = {
                    menu.readAll()
                    close()
                },
                onViewAll = {
                    close()
                    onOpenNotifications()
                },
                onOpen = { notification ->
                    menu.read(notification)
                    if (notification.link.isNotBlank()) {
                        close()
                        onOpenLink(notification.link)
                    }
                },
                onPick = menu::pick,
                onClosePicker = menu::closePicker,
                onCategorise = menu::categorise,
                onManageCategories = {
                    close()
                    onManageCategories()
                },
            )
        }
    }
}

/**
 * The home page around [content], as the web's on a phone: the "mochi"
 * wordmark centred in a bar with [navigation] at its start and [actions] at
 * its end, over the page background with its glow when [glow] is on. Both bar
 * and page are see-through, so the glow shows behind the wordmark as it does
 * on the web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomePage(
    glow: Boolean,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    HomeBackground(glow = glow) {
        Scaffold(
            containerColor = Color.Transparent,
            // A see-through page cannot imply its text colour, so name it.
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Wordmark() },
                    navigationIcon = navigation,
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                content()
            }
        }
    }
}

/**
 * The page behind the home grid, as the web's: the theme's background, with
 * the theme's primary colour glowing down from the top when [glow] is on, the
 * user's background preference.
 */
@Composable
internal fun HomeBackground(glow: Boolean, content: @Composable () -> Unit) {
    val colours = MaterialTheme.colorScheme
    val tint = colours.primary.copy(alpha = GLOW_STRENGTH)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colours.background)
            .then(if (glow) Modifier.glow(tint) else Modifier),
    ) {
        content()
    }
}

/**
 * The web's `radial-gradient(ellipse at top, primary 12%, transparent 70%)`
 * over a strip [GLOW_HEIGHT] tall: an ellipse centred on the top edge that
 * reaches the strip's bottom corners, the colour fading out 70% of the way.
 */
private fun Modifier.glow(colour: Color): Modifier = drawBehind {
    val strip = GLOW_HEIGHT.toPx()
    val (across, down) = glowRadii(size.width, strip)
    val top = Offset(size.width / 2, 0f)
    val brush = Brush.radialGradient(0f to colour, GLOW_FADE to Color.Transparent, center = top, radius = down)
    // A radial brush is round: squash the drawing across so its circle becomes
    // the ellipse, and widen the rectangle to cover the strip once squashed.
    withTransform({ scale(scaleX = across / down, scaleY = 1f, pivot = top) }) {
        val width = size.width * down / across
        drawRect(brush, topLeft = Offset(top.x - width / 2, 0f), size = Size(width, minOf(strip, size.height)))
    }
}

/**
 * The radii of CSS's farthest-corner ellipse centred on the top edge of a
 * [width] by [height] box: its farthest-side radii, half the width and the
 * height, scaled by root two so the ellipse passes through the bottom corners.
 */
internal fun glowRadii(width: Float, height: Float): Pair<Float, Float> =
    Pair(width / 2 * SQRT2, height * SQRT2)

/**
 * The wordmark the web shows at the top of its home page: "mochi" in light
 * type, spaced out, shaded from the theme's primary colour to a lighter one.
 */
@Composable
private fun Wordmark() {
    val primary = MaterialTheme.colorScheme.primary
    val brush = remember(primary) { wordmarkBrush(primary) }
    Text(
        text = stringResource(R.string.home_title),
        style = MaterialTheme.typography.headlineMedium.copy(
            brush = brush,
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 3.sp,
        ),
        maxLines = 1,
    )
}

/**
 * The web's `bg-linear-165 from-primary to-primary-light`: a gradient at 165
 * degrees from [primary] to the theme's lighter primary, `oklch(0.74, 85% of
 * the primary's chroma, its hue)`, stretched over whatever it paints.
 */
internal fun wordmarkBrush(primary: Color): Brush {
    val (from, to) = wordmarkColours(primary)
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val (start, end) = gradientLine(size.width, size.height, WORDMARK_ANGLE)
            return LinearGradientShader(start, end, listOf(from, to))
        }
    }
}

/** The wordmark's two colours: [primary], and the web's `--color-primary-light` made from it. */
internal fun wordmarkColours(primary: Color): Pair<Color, Color> {
    val (_, chroma, hue) = oklchOf(primary)
    return Pair(primary, oklch(0.74f, chroma * 0.85f, hue))
}

/**
 * Where CSS draws a linear gradient at [degrees] across a [width] by [height]
 * box: through its centre, pointing [degrees] clockwise from up, and long
 * enough that its perpendicular ends touch the box's corners.
 */
internal fun gradientLine(width: Float, height: Float, degrees: Float): Pair<Offset, Offset> {
    val angle = Math.toRadians(degrees.toDouble())
    val across = sin(angle).toFloat()
    val down = -cos(angle).toFloat()
    val half = (abs(width * across) + abs(height * down)) / 2
    val centre = Offset(width / 2, height / 2)
    val step = Offset(across * half, down * half)
    return Pair(centre - step, centre + step)
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
