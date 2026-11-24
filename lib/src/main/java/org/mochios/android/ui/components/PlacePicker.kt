// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.model.PlaceData
import org.mochios.android.places.NominatimPlace
import org.mochios.android.places.NominatimService
import org.mochios.android.ui.theme.LocalSpacing
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PlacePickerEntryPoint {
    fun nominatimService(): NominatimService
}

private fun nominatimService(context: Context): NominatimService =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        PlacePickerEntryPoint::class.java
    ).nominatimService()

private const val MAX_DISPLAY_NAME_LENGTH = 80

private fun truncate(text: String, max: Int = MAX_DISPLAY_NAME_LENGTH): String =
    if (text.length <= max) text else text.substring(0, max - 1) + "…"

@Composable
fun PlacePicker(
    place: PlaceData?,
    onPlaceSelected: (PlaceData) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val service = remember(context) { nominatimService(context) }
    PlacePicker(place = place, onPlaceSelected = onPlaceSelected, service = service, modifier = modifier)
}

/** [PlacePicker] over a given geocoder. */
@OptIn(FlowPreview::class)
@Composable
internal fun PlacePicker(
    place: PlaceData?,
    onPlaceSelected: (PlaceData) -> Unit,
    service: NominatimService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    // The map's tap receiver is made once, with the view; it calls the latest
    // callback, not the one of the first composition.
    val select by rememberUpdatedState(onPlaceSelected)

    var nameText by rememberSaveable { mutableStateOf(place?.name ?: "") }
    var suggestions by remember { mutableStateOf<List<NominatimPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var noResults by remember { mutableStateOf(false) }
    // A lookup that failed, which is not one that found nothing.
    var searchError by remember { mutableStateOf<MochiError?>(null) }
    var suppressSearch by remember { mutableStateOf(false) }

    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            // Pin the native view to 250.dp so it can't measure taller than the
            // AndroidView slot and bleed over the surrounding Compose content.
            val heightPx = (250 * context.resources.displayMetrics.density).toInt()
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx,
            )
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(5.0)
            if (place != null && (place.lat != 0.0 || place.lon != 0.0)) {
                controller.setCenter(GeoPoint(place.lat, place.lon))
                controller.setZoom(12.0)
            }
        }
    }

    fun moveMarker(lat: Double, lon: Double, title: String, zoom: Double? = null) {
        mapView.overlays.removeAll { it is Marker }
        val point = GeoPoint(lat, lon)
        val marker = Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = title
        }
        mapView.overlays.add(marker)
        mapView.controller.setCenter(point)
        if (zoom != null) mapView.controller.setZoom(zoom)
        mapView.invalidate()
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

    // Debounced autocomplete search driven by `nameText`.
    LaunchedEffect(Unit) {
        snapshotFlow { nameText }
            .debounce(SEARCH_DEBOUNCE)
            .distinctUntilChanged()
            .collect { query ->
                if (suppressSearch) {
                    suppressSearch = false
                    return@collect
                }
                val trimmed = query.trim()
                if (trimmed.length < 2) {
                    suggestions = emptyList()
                    searching = false
                    noResults = false
                    searchError = null
                    return@collect
                }
                searching = true
                noResults = false
                searchError = null
                try {
                    val results = service.search(trimmed)
                    suggestions = results
                    noResults = results.isEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    suggestions = emptyList()
                    searchError = e.toMochiError()
                } finally {
                    searching = false
                }
            }
    }

    // The map tap's name lookup. A place picked or a name typed after the
    // tap supersedes it, so it is cancelled rather than let its answer land
    // over theirs.
    val reverseJob = remember { arrayOfNulls<Job>(1) }

    // A picked suggestion is a new place: nothing the old one carried (its
    // country, state or city) describes it.
    fun selectSuggestion(s: NominatimPlace) {
        reverseJob[0]?.cancel()
        suppressSearch = true
        nameText = s.displayName
        suggestions = emptyList()
        searching = false
        noResults = false
        searchError = null
        onPlaceSelected(s.toPlaceData())
        moveMarker(s.lat, s.lon, s.displayName, zoom = 14.0)
    }

    val spacing = LocalSpacing.current

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            MochiTextField(
                value = nameText,
                onValueChange = { newName ->
                    reverseJob[0]?.cancel()
                    nameText = newName
                    onPlaceSelected(
                        (place ?: PlaceData()).copy(name = newName)
                    )
                },
                label = { Text(stringResource(R.string.place_picker_name)) },
                singleLine = true,
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        val failure = searchError
        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(spacing.gap(4.dp)))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                shape = mochiPopupShape(),
                color = mochiPopupContainerColor(),
                shadowElevation = MochiPopupElevation,
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    suggestions.forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectSuggestion(s) }
                                .padding(horizontal = spacing.gap(12.dp), vertical = spacing.gap(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(spacing.gap(8.dp)))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = s.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (s.displayName != s.name) {
                                    Text(
                                        text = truncate(s.displayName),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (failure != null && nameText.trim().length >= 2) {
            Spacer(modifier = Modifier.height(spacing.gap(4.dp)))
            InlineErrorState(error = failure)
        } else if (noResults && nameText.trim().length >= 2) {
            Spacer(modifier = Modifier.height(spacing.gap(4.dp)))
            Text(
                text = stringResource(R.string.place_picker_no_results),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.gap(12.dp), vertical = spacing.gap(4.dp))
            )
        }

        Spacer(modifier = Modifier.height(spacing.gap(8.dp)))

        AndroidView(
            factory = {
                mapView.apply {
                    val receiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            // Drop a marker immediately and emit the lat/lon now.
                            // The point is a new place: the name as typed, and
                            // nothing that described the old position.
                            moveMarker(p.latitude, p.longitude, nameText)
                            val current = PlaceData(
                                name = nameText,
                                lat = p.latitude,
                                lon = p.longitude
                            )
                            select(current)

                            // Reverse-geocode in the background to fill in the name.
                            reverseJob[0]?.cancel()
                            reverseJob[0] = scope.launch {
                                // Tiny delay so the marker render isn't blocked by network.
                                delay(50)
                                // Without a name for the point, the pin keeps the typed one.
                                val reverse = try {
                                    service.reverse(p.latitude, p.longitude)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    null
                                } ?: return@launch
                                val display = reverse.displayName
                                suppressSearch = true
                                nameText = display
                                suggestions = emptyList()
                                searchError = null
                                select(
                                    current.copy(
                                        name = display,
                                        category = reverse.category
                                    )
                                )
                                moveMarker(p.latitude, p.longitude, display)
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    }
                    overlays.add(0, MapEventsOverlay(receiver))

                    if (place != null && (place.lat != 0.0 || place.lon != 0.0)) {
                        val marker = Marker(this).apply {
                            position = GeoPoint(place.lat, place.lon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = place.name
                        }
                        overlays.add(marker)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                // osmdroid draws tiles/overlays past its bounds; clip so the
                // map can't bleed over the search field above or the controls
                // below it in the scrolling column.
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}
