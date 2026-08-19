// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Twelve presets matching the web palette: white + slate, a spectrum, then black. */
val COLOR_PICKER_PRESETS = listOf(
    "#ffffff", // white
    "#94a3b8", // slate
    "#ef4444", // red
    "#f97316", // orange
    "#f59e0b", // amber
    "#22c55e", // green
    "#14b8a6", // teal
    "#06b6d4", // cyan
    "#3b82f6", // blue
    "#a78bfa", // violet
    "#ec4899", // pink
    "#000000", // black
)

// Sized for a phone: the picker sits inside dialogs and a scrolling profile
// form, where a full-height field pushed the buttons off screen.
private val PRESET_SIZE = 32.dp
private val PRESET_RING_GAP = 3.dp
private val FIELD_HEIGHT = 120.dp
private val FIELD_THUMB = 16.dp
private val HUE_HEIGHT = 12.dp
private val HUE_THUMB_WIDTH = 10.dp

/**
 * Colour picker mirroring the web `<ColourPicker>`: a wrapping grid of presets,
 * a 2D saturation/value field, a hue slider, and a hex field.
 *
 * Controlled by [hex] — every change flows out through [onHexChange] and comes
 * back in, so a caller that clears or overwrites the value moves the controls
 * with it. HSV drives the field and slider internally; a hex the picker cannot
 * parse is left in the text box without disturbing them, so a half-typed value
 * doesn't throw the swatch around.
 *
 * @param hex Current colour as `#rrggbb`, or "" when unset.
 * @param onHexChange Fired with the new `#rrggbb` whenever a control moves.
 * @param presets Swatches offered above the field.
 * @param hexPlaceholder Shown in the hex box while [hex] is empty.
 * @param trailing Extra controls on the preview row, after the hex box —
 *   Save / Clear buttons, or nothing when the surrounding form owns them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    hex: String,
    onHexChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<String> = COLOR_PICKER_PRESETS,
    hexPlaceholder: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    // Seed HSV from the incoming hex, defaulting to a mid violet.
    val initial = remember {
        parseHexColor(hex)?.let { colour -> rgbToHsv(colour) } ?: Triple(270f, 0.5f, 0.5f)
    }
    var hue by remember { mutableFloatStateOf(initial.first) }
    var sat by remember { mutableFloatStateOf(initial.second) }
    var bright by remember { mutableFloatStateOf(initial.third) }
    var hexText by remember { mutableStateOf(hex) }

    var svSize by remember { mutableStateOf(IntSize.Zero) }
    var hueSize by remember { mutableStateOf(IntSize.Zero) }

    // rememberUpdatedState keeps the latest callback reachable from the
    // pointerInput closures without restarting their gesture loops.
    val onHexState = rememberUpdatedState(onHexChange)
    val commit = remember {
        { h: Float, s: Float, v: Float ->
            hue = h
            sat = s
            bright = v
            val newHex = hsvToHex(h, s, v)
            hexText = newHex
            onHexState.value(newHex)
        }
    }

    // A value the caller set itself — cleared, or seeded from elsewhere — wins
    // over the local echo. Changes the picker emitted already match, so this
    // never fights the controls.
    LaunchedEffect(hex) {
        if (!hex.equals(hexText.trim(), ignoreCase = true)) {
            hexText = hex
            parseHexColor(hex)?.let { colour ->
                val (h, s, v) = rgbToHsv(colour)
                hue = h
                sat = s
                bright = v
            }
        }
    }

    val hueBrush = remember {
        Brush.horizontalGradient((0..6).map { step -> Color.hsv(step * 60f, 1f, 1f) })
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── Preset swatches (wrap by width) ──
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                val selected = hexText.equals(preset, ignoreCase = true)
                // Ring + inner gap + colour fill: the border sits on the outer
                // edge, padding opens a gap, then the fill is a smaller circle.
                Box(
                    modifier = Modifier
                        .size(PRESET_SIZE)
                        .clip(CircleShape)
                        .clickable {
                            val colour = parseHexColor(preset) ?: return@clickable
                            val (h, s, v) = rgbToHsv(colour)
                            hue = h
                            sat = s
                            bright = v
                            hexText = preset
                            onHexState.value(preset)
                        }
                        .border(
                            width = 2.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else Color.Transparent,
                            shape = CircleShape,
                        )
                        .padding(PRESET_RING_GAP)
                        .clip(CircleShape)
                        .background(parseHexColor(preset) ?: Color.Gray),
                )
            }
        }

        // ── 2D saturation (x) / value (y) field ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FIELD_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.hsv(hue, 1f, 1f))
                .onSizeChanged { size -> svSize = size }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (size.width > 0 && size.height > 0) {
                            commit(
                                hue,
                                (offset.x / size.width).coerceIn(0f, 1f),
                                (1f - offset.y / size.height).coerceIn(0f, 1f),
                            )
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        if (size.width > 0 && size.height > 0) {
                            change.consume()
                            commit(
                                hue,
                                (change.position.x / size.width).coerceIn(0f, 1f),
                                (1f - change.position.y / size.height).coerceIn(0f, 1f),
                            )
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent))),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
            )
            Box(
                modifier = Modifier
                    .offset {
                        val radius = FIELD_THUMB.toPx() / 2f
                        IntOffset(
                            (sat * svSize.width - radius).roundToInt(),
                            ((1f - bright) * svSize.height - radius).roundToInt(),
                        )
                    }
                    .size(FIELD_THUMB)
                    .clip(CircleShape)
                    .background(Color.hsv(hue, sat, bright))
                    .border(2.dp, Color.White, CircleShape),
            )
        }

        // ── Hue slider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HUE_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(hueBrush)
                .onSizeChanged { size -> hueSize = size }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (size.width > 0) {
                            commit((offset.x / size.width * 360f).coerceIn(0f, 360f), sat, bright)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        if (size.width > 0) {
                            change.consume()
                            commit(
                                (change.position.x / size.width * 360f).coerceIn(0f, 360f),
                                sat,
                                bright,
                            )
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        val thumbHalf = HUE_THUMB_WIDTH.toPx() / 2f
                        IntOffset((hue / 360f * hueSize.width - thumbHalf).roundToInt(), 0)
                    }
                    .width(HUE_THUMB_WIDTH)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(5.dp),
                    ),
            )
        }

        // ── Preview + hex + whatever the caller adds ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val previewColour = parseHexColor(hexText.trim())
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(previewColour ?: Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            MochiTextField(
                value = hexText,
                onValueChange = { input ->
                    hexText = input
                    val colour = parseHexColor(input.trim())
                    if (colour != null) {
                        val (h, s, v) = rgbToHsv(colour)
                        hue = h
                        sat = s
                        bright = v
                        onHexState.value(input.trim())
                    }
                },
                singleLine = true,
                placeholder = hexPlaceholder?.let { text -> { Text(text) } },
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
    }
}

/**
 * Parses a `#rgb` or `#rrggbb` colour, with or without the leading `#`.
 *
 * @return the colour, or null when the text is not a hex colour.
 */
fun parseHexColor(hex: String): Color? {
    val value = hex.trim().removePrefix("#")
    return try {
        when (value.length) {
            6 -> Color(
                red = value.substring(0, 2).toInt(16) / 255f,
                green = value.substring(2, 4).toInt(16) / 255f,
                blue = value.substring(4, 6).toInt(16) / 255f,
            )
            3 -> Color(
                red = (value[0].digitToInt(16) * 17) / 255f,
                green = (value[1].digitToInt(16) * 17) / 255f,
                blue = (value[2].digitToInt(16) * 17) / 255f,
            )
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

/** Hue (0–360), saturation (0–1), value (0–1) from an sRGB [Color]. */
private fun rgbToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { value -> if (value < 0f) value + 360f else value }
    val sat = if (max == 0f) 0f else delta / max
    return Triple(hue, sat, max)
}

/** `#rrggbb` for the given HSV triple. */
private fun hsvToHex(h: Float, s: Float, v: Float): String {
    val colour = Color.hsv(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    val r = (colour.red * 255f).roundToInt()
    val g = (colour.green * 255f).roundToInt()
    val b = (colour.blue * 255f).roundToInt()
    return "#%02x%02x%02x".format(r, g, b)
}
