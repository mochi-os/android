// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.model.PlaceData
import org.mochios.android.ui.components.LocationPreviewMap
import org.mochios.android.ui.components.MapMarkerPoint
import org.mochios.android.ui.components.MentionTextField
import org.mochios.android.ui.components.PlacePicker
import org.mochios.android.ui.components.TravellingPicker
import org.mochios.feeds.R
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePostScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreatePostViewModel = hiltViewModel()
) {
    val availableFeeds by viewModel.availableFeeds.collectAsState()
    val selectedFeed by viewModel.selectedFeed.collectAsState()
    val body by viewModel.body.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val existingAttachments by viewModel.existingAttachments.collectAsState()
    val removedExistingIds by viewModel.removedExistingIds.collectAsState()
    val checkin by viewModel.checkin.collectAsState()
    val travellingOrigin by viewModel.travellingOrigin.collectAsState()
    val travellingDestination by viewModel.travellingDestination.collectAsState()
    val isPosting by viewModel.isPosting.collectAsState()
    val isLoadingFeeds by viewModel.isLoadingFeeds.collectAsState()
    val error by viewModel.error.collectAsState()
    val postSuccess by viewModel.postSuccess.collectAsState()
    val isEditing = viewModel.isEditing

    var showCheckinSheet by remember { mutableStateOf(false) }
    var showTravellingSheet by remember { mutableStateOf(false) }
    var feedDropdownExpanded by remember { mutableStateOf(false) }

    val hasTravelling = travellingOrigin != null || travellingDestination != null

    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.addAttachments(uris)
    }

    LaunchedEffect(postSuccess) {
        if (postSuccess) {
            onNavigateBack()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it.userMessage())
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEditing) R.string.feeds_edit_post else R.string.feeds_new_post)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.createPost() },
                        enabled = !isPosting
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(if (isEditing) R.string.feeds_save_label else R.string.feeds_post_action))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Feed selector — shown only when no feed was preselected (a global
            // "new post" entry). Coming from a feed already fixes the destination.
            if (!isEditing && !viewModel.feedPreselected) {
                ExposedDropdownMenuBox(
                    expanded = feedDropdownExpanded,
                    onExpandedChange = { feedDropdownExpanded = it }
                ) {
                    val selectFeedDefault = stringResource(R.string.feeds_select_feed)
                    val selectedFeedName = availableFeeds
                        .find { it.fingerprint == selectedFeed || it.id == selectedFeed }
                        ?.name ?: selectFeedDefault

                    OutlinedTextField(
                        value = selectedFeedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.feeds_feed_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = feedDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = feedDropdownExpanded,
                        onDismissRequest = { feedDropdownExpanded = false }
                    ) {
                        if (isLoadingFeeds) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.feeds_loading_feeds))
                                    }
                                },
                                onClick = {}
                            )
                        } else {
                            availableFeeds.forEach { feed ->
                                DropdownMenuItem(
                                    text = { Text(feed.name) },
                                    onClick = {
                                        viewModel.setSelectedFeed(feed.fingerprint.ifEmpty { feed.id })
                                        feedDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Body text area
            Text(
                text = stringResource(R.string.feeds_post_content),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            MentionTextField(
                value = body,
                onValueChange = { text -> viewModel.setBody(text) },
                onSearch = { query -> viewModel.searchMembers(query) },
                placeholder = { Text(stringResource(R.string.feeds_markdown_supported)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                maxLines = 20,
                fillHeight = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Neutral (variant) tint for the compose action buttons — not primary.
            val actionButtonColors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Preview card of the chosen location, above the action buttons.
            if (checkin != null) {
                LocationPreviewCard(
                    icon = Icons.Default.LocationOn,
                    text = checkin?.name.orEmpty(),
                    points = listOfNotNull(
                        checkin?.let { place ->
                            MapMarkerPoint(
                                place.lat,
                                place.lon,
                                LocationPinBlue
                            )
                        }
                    ),
                    connectRoute = false,
                    onClear = { viewModel.clearLocation() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else if (hasTravelling) {
                LocationPreviewCard(
                    icon = Icons.Default.Flight,
                    text = listOfNotNull(
                        travellingOrigin?.name,
                        travellingDestination?.name
                    ).joinToString(" – "),
                    points = listOfNotNull(
                        travellingOrigin?.let { place ->
                            MapMarkerPoint(
                                place.lat,
                                place.lon,
                                LocationPinBlue
                            )
                        },
                        travellingDestination?.let { place ->
                            MapMarkerPoint(
                                place.lat,
                                place.lon,
                                LocationPinGreen
                            )
                        }
                    ),
                    connectRoute = true,
                    onClear = { viewModel.clearLocation() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Location: each button opens a bottom sheet to search + confirm.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showCheckinSheet = true }, colors = actionButtonColors) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.feeds_check_in))
                }
                OutlinedButton(
                    onClick = { showTravellingSheet = true },
                    colors = actionButtonColors
                ) {
                    Icon(
                        Icons.Default.Flight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.feeds_travelling))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Attachments
            OutlinedButton(
                onClick = { filePickerLauncher.launch("*/*") },
                colors = actionButtonColors
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.feeds_add_files))
            }

            if (existingAttachments.isNotEmpty() || attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    existingAttachments.forEachIndexed { index, attachment ->
                        val isRemoved = attachment.id in removedExistingIds
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (existingAttachments.size > 1) {
                                Column {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = {
                                                viewModel.moveExistingAttachment(
                                                    attachment.id,
                                                    -1
                                                )
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ExpandLess,
                                                contentDescription = stringResource(R.string.feeds_move_up),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    if (index < existingAttachments.lastIndex) {
                                        IconButton(
                                            onClick = {
                                                viewModel.moveExistingAttachment(
                                                    attachment.id,
                                                    1
                                                )
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ExpandMore,
                                                contentDescription = stringResource(R.string.feeds_move_down),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            FilterChip(
                                selected = !isRemoved,
                                onClick = { viewModel.toggleRemoveExistingAttachment(attachment.id) },
                                label = {
                                    Text(
                                        attachment.name.takeLast(25),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(if (isRemoved) R.string.feeds_restore else R.string.feeds_remove),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                    attachments.forEachIndexed { index, uri ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (attachments.size > 1) {
                                Column {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = { viewModel.moveAttachment(uri, -1) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ExpandLess,
                                                contentDescription = stringResource(R.string.feeds_move_up),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    if (index < attachments.lastIndex) {
                                        IconButton(
                                            onClick = { viewModel.moveAttachment(uri, 1) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ExpandMore,
                                                contentDescription = stringResource(R.string.feeds_move_down),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            val fileLabel = stringResource(R.string.feeds_file)
                            AssistChip(
                                onClick = { viewModel.removeAttachment(uri) },
                                label = {
                                    Text(
                                        uri.lastPathSegment?.takeLast(25) ?: fileLabel,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.feeds_remove),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

        }
    }

    if (showCheckinSheet) {
        CheckinBottomSheet(
            initial = checkin,
            onConfirm = { place ->
                viewModel.applyCheckin(place)
                showCheckinSheet = false
            },
            onDismiss = { showCheckinSheet = false }
        )
    }

    if (showTravellingSheet) {
        TravellingBottomSheet(
            initialOrigin = travellingOrigin,
            initialDestination = travellingDestination,
            onConfirm = { origin, destination ->
                viewModel.applyTravelling(origin, destination)
                showTravellingSheet = false
            },
            onDismiss = { showTravellingSheet = false }
        )
    }
}

/** Origin/check-in pin colour, matching the web preview. */
private val LocationPinBlue = Color(0xFF2563EB)

/** Destination pin colour. */
private val LocationPinGreen = Color(0xFF22C55E)

/**
 * Preview card for a chosen post location: an icon + place name(s), a clear (✕)
 * button, and a map showing the marker(s) and — for travelling — a route line.
 */
@Composable
private fun LocationPreviewCard(
    icon: ImageVector,
    text: String,
    points: List<MapMarkerPoint>,
    connectRoute: Boolean,
    onClear: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = LocationPinBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(MochiR.string.common_close),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (points.any { point -> point.lat != 0.0 || point.lon != 0.0 }) {
                Spacer(modifier = Modifier.height(8.dp))
                LocationPreviewMap(
                    points = points,
                    connectRoute = connectRoute,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

/**
 * Bottom sheet for choosing a check-in location: a searchable map picker with a
 * Back / Confirm action. The selection is committed only on Confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckinBottomSheet(
    initial: PlaceData?,
    onConfirm: (PlaceData?) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.feeds_check_in),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(MochiR.string.common_close)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            PlacePicker(
                place = draft,
                onPlaceSelected = { place -> draft = place },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            SheetActions(
                onCancel = onDismiss,
                cancelLabel = stringResource(MochiR.string.common_back),
                onConfirm = { onConfirm(draft) }
            )
        }
    }
}

/**
 * Bottom sheet for choosing a travelling origin/destination pair with a
 * Cancel / Confirm action. Committed only on Confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravellingBottomSheet(
    initialOrigin: PlaceData?,
    initialDestination: PlaceData?,
    onConfirm: (PlaceData?, PlaceData?) -> Unit,
    onDismiss: () -> Unit
) {
    var origin by remember { mutableStateOf(initialOrigin) }
    var destination by remember { mutableStateOf(initialDestination) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flight, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.feeds_travelling),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(MochiR.string.common_close)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TravellingPicker(
                origin = origin,
                destination = destination,
                onOriginSelected = { place -> origin = place },
                onDestinationSelected = { place -> destination = place },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            SheetActions(
                onCancel = onDismiss,
                cancelLabel = stringResource(MochiR.string.common_cancel),
                onConfirm = { onConfirm(origin, destination) }
            )
        }
    }
}

/** Shared Cancel / Confirm button row for the location bottom sheets. */
@Composable
private fun SheetActions(
    onCancel: () -> Unit,
    cancelLabel: String,
    onConfirm: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text(cancelLabel)
        }
        Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(MochiR.string.common_confirm))
        }
    }
}
