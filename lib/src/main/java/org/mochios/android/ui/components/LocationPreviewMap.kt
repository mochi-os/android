// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** A single map marker: its position and pin colour. */
data class MapMarkerPoint(
    val lat: Double,
    val lon: Double,
    val color: Color,
)

/**
 * Read-only map preview showing one or more coloured markers, optionally joined
 * by a route line. Used to preview a post's chosen check-in or travelling
 * location. Points at (0, 0) are ignored.
 */
@Composable
fun LocationPreviewMap(
    points: List<MapMarkerPoint>,
    connectRoute: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(5.0)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(points, connectRoute) {
        mapView.overlays.removeAll { it is Marker || it is Polyline }

        val valid = points.filter { point -> point.lat != 0.0 || point.lon != 0.0 }
        val geoPoints = valid.map { point -> GeoPoint(point.lat, point.lon) }

        valid.forEach { point ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(point.lat, point.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                val pin = ContextCompat.getDrawable(
                    context,
                    org.osmdroid.library.R.drawable.marker_default
                )?.mutate()
                if (pin != null) {
                    DrawableCompat.setTint(pin, point.color.toArgb())
                    icon = pin
                }
            }
            mapView.overlays.add(marker)
        }

        if (connectRoute && geoPoints.size >= 2) {
            val line = Polyline(mapView).apply {
                setPoints(geoPoints)
                outlinePaint.color = points.first().color.toArgb()
                outlinePaint.strokeWidth = 6f
            }
            mapView.overlays.add(line)
        }

        when {
            geoPoints.size == 1 -> {
                mapView.controller.setZoom(13.0)
                mapView.controller.setCenter(geoPoints.first())
            }
            geoPoints.size >= 2 -> {
                val box = BoundingBox.fromGeoPoints(geoPoints).increaseByScale(1.4f)
                // zoomToBoundingBox needs the view laid out to know its size.
                mapView.post { mapView.zoomToBoundingBox(box, false, 48) }
            }
        }
        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            // osmdroid draws past its bounds; clip so tiles don't bleed out.
            .clipToBounds()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
    )
}
