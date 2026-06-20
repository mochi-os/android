// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mochios.android.model.PlaceData
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Renders a small map showing a checkin pin or a travelling route line.
 */
@Composable
fun LocationMapView(
    checkin: PlaceData? = null,
    origin: PlaceData? = null,
    destination: PlaceData? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    AndroidView(
        factory = { mapView },
        update = { map ->
            map.overlays.clear()

            if (checkin != null && (checkin.lat != 0.0 || checkin.lon != 0.0)) {
                val point = GeoPoint(checkin.lat, checkin.lon)
                map.controller.setCenter(point)
                map.controller.setZoom(14.0)
                val marker = Marker(map).apply {
                    position = point
                    title = checkin.name
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(marker)
            } else if (origin != null && destination != null &&
                (origin.lat != 0.0 || origin.lon != 0.0) &&
                (destination.lat != 0.0 || destination.lon != 0.0)
            ) {
                val originPoint = GeoPoint(origin.lat, origin.lon)
                val destPoint = GeoPoint(destination.lat, destination.lon)

                // Center between the two points
                val centerLat = (origin.lat + destination.lat) / 2
                val centerLon = (origin.lon + destination.lon) / 2
                map.controller.setCenter(GeoPoint(centerLat, centerLon))
                map.controller.setZoom(6.0)

                // Draw route line
                val line = Polyline().apply {
                    addPoint(originPoint)
                    addPoint(destPoint)
                    outlinePaint.color = android.graphics.Color.parseColor("#4285F4")
                    outlinePaint.strokeWidth = 6f
                }
                map.overlays.add(line)

                // Add markers
                val originMarker = Marker(map).apply {
                    position = originPoint
                    title = origin.name
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                val destMarker = Marker(map).apply {
                    position = destPoint
                    title = destination.name
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(originMarker)
                map.overlays.add(destMarker)
            }

            map.invalidate()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}
