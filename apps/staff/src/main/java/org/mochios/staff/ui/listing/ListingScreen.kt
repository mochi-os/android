// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.listing

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.format.formatFingerprint
import org.mochios.android.format.formatPrice
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LightboxScreen
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiImage
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.staff.R
import org.mochios.staff.model.ListingView
import org.mochios.staff.model.ModerationEntry
import org.mochios.staff.model.listingPhotoAddress
import org.mochios.staff.model.parseFactors
import org.mochios.staff.ui.components.ScoreColorChip
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.components.StaffUserAvatar
import org.mochios.staff.ui.components.conditionRes
import org.mochios.staff.ui.dialog.ListingActionDialog
import org.mochios.staff.ui.listings.ListingActionType
import org.mochios.staff.ui.listings.listingActions

/**
 * One listing as staff see it, whatever its status: its photos, title,
 * description, condition and price, the factors behind its moderation score,
 * its seller and its moderation history, with the actions a moderator can
 * take on it. A history entry opens the moderation log for the listing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingScreen(
    onBack: () -> Unit,
    onOpenHistory: (listing: String) -> Unit,
    viewModel: ListingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ListingEvent.Toast -> snackbar.showSnackbar(resources.getString(event.messageRes))
                is ListingEvent.Error -> snackbar.showSnackbar(event.error.userMessage())
            }
        }
    }

    val view = state.view
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = view?.listing?.title?.ifBlank { null } ?: stringResource(R.string.staff_listing_untitled),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val error = state.error
            when {
                view == null && state.isLoading -> LoadingState()
                view == null && error != null -> ErrorState(error = error, onRetry = viewModel::load)
                view != null -> ListingBody(
                    view = view,
                    onAction = viewModel::openAction,
                    onOpenHistory = { onOpenHistory(view.listing.id) },
                )
            }
        }
    }

    state.pendingAction?.let { pending ->
        ListingActionDialog(
            action = pending,
            submitting = state.submitting,
            onDismiss = viewModel::dismissAction,
            onSubmit = viewModel::submitAction,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListingBody(
    view: ListingView,
    onAction: (ListingActionType) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val listing = view.listing
    var opened by rememberSaveable(listing.id) { mutableStateOf<Int?>(null) }
    // Each photo's thumbnail and full image, in the seller's order.
    val photos = view.photos.sortedBy { photo -> photo.position }
        .mapNotNull { photo ->
            val thumbnail = listingPhotoAddress(listing.id, photo.id, thumbnail = true)
            val full = listingPhotoAddress(listing.id, photo.id)
            if (thumbnail != null && full != null) thumbnail to full else null
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (photos.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                itemsIndexed(photos) { index, (thumbnail, _) ->
                    MochiImage(
                        model = thumbnail,
                        contentDescription = null,
                        shape = RoundedCornerShape(8.dp),
                        onClick = { opened = index },
                        modifier = Modifier.size(120.dp),
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            StaffStatusBadge(status = listing.status)
            StaffStatusBadge(status = listing.moderation)
            ScoreColorChip(score = listing.score.toInt())
        }

        Text(
            text = if (listing.price > 0L) {
                formatPrice(listing.price, listing.currency)
            } else {
                stringResource(R.string.staff_listings_price_placeholder)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Section(title = stringResource(R.string.staff_reports_meta_seller)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val fingerprint = formatFingerprint(view.seller.fingerprint)
                StaffUserAvatar(name = view.seller.name.ifBlank { fingerprint }, id = view.seller.id, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    if (view.seller.name.isNotBlank()) {
                        Text(text = view.seller.name, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (fingerprint.isNotEmpty()) {
                        Text(
                            text = fingerprint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (listing.condition.isNotBlank()) {
            Section(title = stringResource(R.string.staff_listing_condition)) {
                Text(
                    text = stringResource(conditionRes(listing.condition) ?: R.string.staff_status_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (listing.description.isNotBlank()) {
            Section(title = stringResource(R.string.staff_listing_description)) {
                Text(text = listing.description, style = MaterialTheme.typography.bodyMedium)
            }
        }

        val factors = parseFactors(listing.factors)
        if (factors.isNotEmpty()) {
            Section(title = stringResource(R.string.staff_listing_factors)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    factors.forEach { factor ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ScoreColorChip(score = factor.score.toInt())
                            Spacer(Modifier.width(8.dp))
                            Text(text = factor.reason, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (view.history.isNotEmpty()) {
            Section(title = stringResource(R.string.staff_audit_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    view.history.forEach { entry ->
                        HistoryRow(entry = entry, onOpen = onOpenHistory)
                    }
                }
            }
        }

        val actions = listingActions(listing)
        if (actions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ListingActionType.APPROVE in actions) {
                    MochiButton(onClick = { onAction(ListingActionType.APPROVE) }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.staff_listings_approve))
                    }
                }
                if (ListingActionType.REJECT in actions) {
                    MochiOutlinedButton(onClick = { onAction(ListingActionType.REJECT) }) {
                        Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.staff_listings_reject))
                    }
                }
                if (ListingActionType.REMOVE in actions) {
                    MochiOutlinedButton(onClick = { onAction(ListingActionType.REMOVE) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.staff_listings_remove))
                    }
                }
            }
        }
    }

    opened?.let { index ->
        LightboxScreen(
            images = photos.map { (_, full) -> full },
            initialIndex = index,
            onDismiss = { opened = null },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun HistoryRow(entry: ModerationEntry, onOpen: () -> Unit) {
    val format = LocalFormat.current
    MochiCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StaffStatusBadge(status = entry.action)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        entry.actor == "system" -> stringResource(R.string.staff_moderation_system)
                        entry.actorName.isNotBlank() -> entry.actorName
                        else -> formatFingerprint(entry.actorFingerprint)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = format.formatTimestamp(entry.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.reason.isNotBlank()) {
                Text(text = entry.reason, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
