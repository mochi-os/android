// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.mochios.people.R

/**
 * Inline accent colour picker backing the Profile editor's "Accent" section.
 * Mirrors the web `<ColourPicker>`: a wrapping grid of presets, a 2D
 * saturation/value field, a hue slider, and a hex field with Clear / Save.
 *
 * HSV (hue, saturation, value) is the picker's source of truth for the field
 * and slider; [hex] only seeds the initial state. Edits flow out through
 * [onHexChange] (live, for the avatar preview) and are persisted by [onSave].
 * An empty hex means "no accent".
 *
 * @param hex Current accent hex ("" when unset). Seeds the controls on first
 *            composition.
 * @param isSaving Disables Save and shows a spinner while a save is in flight.
 * @param onHexChange Fired on every change with the new `#rrggbb` hex.
 * @param onClear Clears the accent (the caller persists the empty value).
 * @param onSave Persists the current draft.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentColorPicker(
    hex: String,
    isSaving: Boolean,
    onHexChange: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Seed HSV from the incoming hex once, defaulting to a mid violet.
    val initial = remember { parseHex(hex)?.let { colour -> rgbToHsv(colour) } ?: Triple(270f, 0.5f, 0.5f) }
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

    val hueBrush = remember {
        Brush.horizontalGradient((0..6).map { step -> Color.hsv(step * 60f, 1f, 1f) })
    }

    // Save and Clear share the same in-flight flag ([isSaving]); remember which
    // button started the request so only that one shows the spinner. Reset once
    // the request settles.
    var pending by remember { mutableStateOf<AccentAction?>(null) }
    LaunchedEffect(isSaving) {
        if (!isSaving) pending = null
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── Preset swatches (wrap by width) ──
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRESETS.forEach { preset ->
                val selected = hexText.equals(preset, ignoreCase = true)
                // Ring + inner gap + colour fill: the border sits on the outer
                // edge, padding opens a gap, then the fill is a smaller circle.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            val colour = parseHex(preset) ?: return@clickable
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
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(parseHex(preset) ?: Color.Gray),
                )
            }
        }

        // ── 2D saturation (x) / value (y) field ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.hsv(hue, 1f, 1f))
                .onSizeChanged { svSize = it }
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
                        val radius = 18.dp.toPx() / 2f
                        IntOffset(
                            (sat * svSize.width - radius).roundToInt(),
                            ((1f - bright) * svSize.height - radius).roundToInt(),
                        )
                    }
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.hsv(hue, sat, bright))
                    .border(2.dp, Color.White, CircleShape),
            )
        }

        // ── Hue slider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(hueBrush)
                .onSizeChanged { hueSize = it }
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
                            commit((change.position.x / size.width * 360f).coerceIn(0f, 360f), sat, bright)
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        val thumbHalf = 10.dp.toPx() / 2f
                        IntOffset((hue / 360f * hueSize.width - thumbHalf).roundToInt(), 0)
                    }
                    .width(10.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp)),
            )
        }

        // ── Preview + hex + Clear / Save ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val previewColour = parseHex(hexText.trim())
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(previewColour ?: Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            OutlinedTextField(
                value = hexText,
                onValueChange = { input ->
                    hexText = input
                    val colour = parseHex(input.trim())
                    if (colour != null) {
                        val (h, s, v) = rgbToHsv(colour)
                        hue = h
                        sat = s
                        bright = v
                        onHexState.value(input.trim())
                    }
                },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.people_profile_accent_none)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.weight(1f),
            )
            val clearing = isSaving && pending == AccentAction.CLEAR
            val saving = isSaving && pending == AccentAction.SAVE
            OutlinedButton(
                onClick = {
                    hexText = ""
                    pending = AccentAction.CLEAR
                    onClear()
                },
                enabled = !isSaving,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                if (clearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(stringResource(R.string.people_profile_clear))
            }
            Button(
                onClick = {
                    pending = AccentAction.SAVE
                    onSave()
                },
                enabled = !isSaving,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.people_common_save))
            }
        }
    }
}

/** Which accent button kicked off the in-flight request, for its spinner. */
private enum class AccentAction { SAVE, CLEAR }

// 12 presets matching the web palette: white + slate, a spectrum, then black.
private val PRESETS = listOf(
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
    }.let { if (it < 0f) it + 360f else it }
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

private fun parseHex(hex: String): Color? {
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
