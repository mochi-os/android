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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.model.PlaceData
import org.mochios.android.places.NominatimPlace
import org.mochios.android.places.NominatimService
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

private const val SEARCH_DEBOUNCE_MS = 300L
private const val MAX_DISPLAY_NAME_LENGTH = 80

private fun truncate(text: String, max: Int = MAX_DISPLAY_NAME_LENGTH): String =
    if (text.length <= max) text else text.substring(0, max - 1) + "…"

@OptIn(FlowPreview::class)
@Composable
fun PlacePicker(
    place: PlaceData?,
    onPlaceSelected: (PlaceData) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val service = remember(context) { nominatimService(context) }

    var nameText by rememberSaveable { mutableStateOf(place?.name ?: "") }
    var suggestions by remember { mutableStateOf<List<NominatimPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var noResults by remember { mutableStateOf(false) }
    var suppressSearch by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
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
            .debounce(SEARCH_DEBOUNCE_MS)
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
                    return@collect
                }
                searching = true
                noResults = false
                val results = runCatching { service.search(trimmed) }.getOrDefault(emptyList())
                suggestions = results
                searching = false
                noResults = results.isEmpty()
            }
    }

    fun selectSuggestion(s: NominatimPlace) {
        suppressSearch = true
        nameText = s.displayName
        suggestions = emptyList()
        searching = false
        noResults = false
        val updated = (place ?: PlaceData()).copy(
            name = s.displayName,
            lat = s.lat,
            lon = s.lon,
            category = s.category
        )
        onPlaceSelected(updated)
        moveMarker(s.lat, s.lon, s.displayName, zoom = 14.0)
    }

    val reverseJob = remember { arrayOfNulls<Job>(1) }

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = nameText,
                onValueChange = { newName ->
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

        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(8.dp)),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    suggestions.forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectSuggestion(s) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
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
        } else if (noResults && nameText.trim().length >= 2) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.place_picker_no_results),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AndroidView(
            factory = {
                mapView.apply {
                    val receiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            // Drop a marker immediately and emit the lat/lon now.
                            moveMarker(p.latitude, p.longitude, nameText)
                            val current = (place ?: PlaceData()).copy(
                                name = nameText,
                                lat = p.latitude,
                                lon = p.longitude
                            )
                            onPlaceSelected(current)

                            // Reverse-geocode in the background to fill in the name.
                            reverseJob[0]?.cancel()
                            reverseJob[0] = scope.launch {
                                // Tiny delay so the marker render isn't blocked by network.
                                delay(50)
                                val reverse = runCatching {
                                    service.reverse(p.latitude, p.longitude)
                                }.getOrNull() ?: return@launch
                                val display = reverse.displayName
                                suppressSearch = true
                                nameText = display
                                suggestions = emptyList()
                                onPlaceSelected(
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}
