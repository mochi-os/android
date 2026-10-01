// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mochios.android.R
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.util.Area
import org.mochios.android.util.Atlas
import org.mochios.android.util.Viewport
import org.mochios.android.util.Zones
import org.mochios.android.util.zoneCity

/** One layer's areas as the canvas draws them. */
private class Drawn(val zone: String, val path: Path)

private fun drawn(areas: List<Area>): List<Drawn> = areas.map { area ->
    val path = Path().apply { fillType = PathFillType.EvenOdd }
    for (ring in area.rings) {
        path.moveTo(ring[0], ring[1])
        for (i in 2 until ring.size step 2) path.lineTo(ring[i], ring[i + 1])
        path.close()
    }
    Drawn(area.zone, path)
}

/**
 * The world map of the time zones the web's picker shows: the chosen zone
 * filled in the primary colour and the one [pointed] at in a tint of it. A
 * tap points at the zone under it and a second tap on it chooses it, since a
 * finger covers more of a small zone than a pointer does; two fingers zoom
 * in on a crowded corner. Beneath, the zone pointed at reads its city and
 * its time now.
 */
@Composable
fun ZoneMap(
    selected: String?,
    pointed: String?,
    onPoint: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val atlas by produceState<Atlas?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { Atlas.load(context) }.getOrNull() }
    }
    val layers = remember(atlas) {
        atlas?.let { Triple(drawn(it.oceans), drawn(it.zones), drawn(it.land)) }
    }
    val sea = MaterialTheme.colorScheme.surfaceVariant
    val bands = MaterialTheme.colorScheme.outlineVariant
    val ground = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val coast = MaterialTheme.colorScheme.surface
    val chosen = MaterialTheme.colorScheme.primary
    val tint = chosen.copy(alpha = 0.35f)
    val format = LocalFormat.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio((atlas?.width ?: 960f) / (atlas?.height ?: 400f))
                .clipToBounds()
                .background(sea, RoundedCornerShape(6.dp)),
        ) {
            val box = with(LocalDensity.current) { maxWidth.toPx() }
            val tall = with(LocalDensity.current) { maxHeight.toPx() }
            var view by remember(atlas, box) { mutableStateOf(Viewport.whole(box, atlas?.width ?: 960f)) }
            val map = atlas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(map, box) {
                        if (map == null) return@pointerInput
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            view = view.moved(zoom, centroid.x, centroid.y, pan.x, pan.y, map.width, map.height, box, tall)
                        }
                    }
                    .pointerInput(map, box, pointed) {
                        if (map == null) return@pointerInput
                        detectTapGestures { tap ->
                            val (x, y) = view.map(tap.x, tap.y)
                            val zone = map.at(x, y) ?: return@detectTapGestures
                            if (zone == pointed) onSelect(zone) else onPoint(zone)
                        }
                    },
            ) {
                val drawn = layers ?: return@Canvas
                translate(view.x, view.y) {
                    scale(view.scale, view.scale, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                        val hairline = Stroke(width = 0.3f)
                        fun fill(zone: String): Color? = when (zone) {
                            selected -> chosen
                            pointed -> tint
                            else -> null
                        }
                        for (area in drawn.first) {
                            fill(area.zone)?.let { drawPath(area.path, it) }
                            drawPath(area.path, bands, style = hairline)
                        }
                        for (area in drawn.second) fill(area.zone)?.let { drawPath(area.path, it) }
                        for (area in drawn.third) {
                            drawPath(area.path, fill(area.zone) ?: ground)
                            drawPath(area.path, coast, style = hairline)
                        }
                    }
                }
            }
            // The data's licence notice, verbatim in every language.
            Text(
                text = stringResource(R.string.zone_attribution),
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp),
            )
        }
        val named = pointed ?: selected
        Text(
            text = named?.let { "${zoneCity(it)} · ${format.formatTime(System.currentTimeMillis() / 1000, it)}" }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (pointed != null) Modifier.clickable { onSelect(pointed) } else Modifier)
                .padding(vertical = 2.dp),
        )
    }
}

/**
 * The time zone picker the web shows: the map, then the zones of places by
 * their current names, each with its offset from UTC now, then the sea's
 * zones, in a list a search box narrows by a zone's name, its city, another
 * name it goes by or its offset. With [automatic], its label heads the list
 * as the choice "auto", which follows the device.
 */
@Composable
fun ZonePicker(
    title: String,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    automatic: String? = null,
) {
    var search by remember { mutableStateOf("") }
    var pointed by remember { mutableStateOf<String?>(null) }
    val listed = remember { Zones.listed() }
    val sea = remember { Zones.sea() }
    val offsets = remember(listed) {
        val now = java.time.Instant.now()
        listed.keys.associateWith { Zones.offset(it, now) }
    }
    val chosen = remember(selected) { if (selected == "auto") selected else Zones.current(selected) }
    // "auto" reads as the device's own zone on the map.
    val shown = remember(chosen) { if (chosen == "auto") Zones.current(java.util.TimeZone.getDefault().id) else chosen }
    val wanted = search.trim()
    fun found(zone: String, others: List<String>): Boolean =
        wanted.isEmpty() ||
            zone.contains(wanted, ignoreCase = true) ||
            zoneCity(zone).contains(wanted, ignoreCase = true) ||
            others.any { it.contains(wanted, ignoreCase = true) } ||
            offsets[zone].orEmpty().contains(wanted, ignoreCase = true)
    val places = listed.filter { (zone, others) -> found(zone, others) }.keys.toList()
    val waters = sea.filter { found(it, emptyList()) }

    @Composable
    fun Choice(zone: String, label: String, offset: String?) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(zone) }
                .padding(horizontal = 4.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (zone == chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!offset.isNullOrEmpty()) {
                Text(
                    text = offset,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissText = stringResource(R.string.common_cancel),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoneMap(
                    selected = shown,
                    pointed = pointed,
                    onPoint = { pointed = it },
                    onSelect = onSelect,
                )
                MochiTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    if (automatic != null && wanted.isEmpty()) {
                        item(key = "auto") { Choice("auto", automatic, null) }
                    }
                    items(places, key = { it }) { zone -> Choice(zone, Zones.label(zone), offsets[zone]) }
                    if (waters.isNotEmpty()) {
                        item(key = "sea") {
                            Box(modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) {
                                Text(
                                    text = stringResource(R.string.zone_sea),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // A sea zone is its offset from UTC, which is its whole name.
                        items(waters, key = { it }) { zone -> Choice(zone, Zones.label(zone), null) }
                    }
                }
            }
        },
    )
}
