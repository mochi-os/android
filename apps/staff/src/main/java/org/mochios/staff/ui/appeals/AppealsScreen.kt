// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.appeals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.staff.R
import org.mochios.android.format.formatFingerprint
import org.mochios.staff.model.Appeal
import org.mochios.staff.ui.components.ScoreColorChip
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.dialog.AppealDecideDialog

/**
 * Staff "Appeals" screen.
 *
 * Android port of `apps/staff/web/src/features/appeals/appeals-page.tsx`.
 * Lists pending listing appeals; each row's "Decide" button opens
 * [AppealDecideDialog]. Tapping the title links to the market listing
 * detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppealsScreen(
    navController: NavController,
    viewModel: AppealsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val upheldMsg = stringResource(R.string.staff_appeals_upheld_toast)
    val deniedMsg = stringResource(R.string.staff_appeals_denied_toast)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AppealsEvent.Decided -> snackbar.showSnackbar(if (event.upheld) upheldMsg else deniedMsg)
                is AppealsEvent.Toast -> snackbar.showSnackbar(event.message)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AppealsBody(
            state = state,
            onLoadMore = viewModel::loadMore,
            onOpenListing = { listingId ->
                navController.navigate("market/listing/$listingId")
            },
            onOpenAppeal = viewModel::openDecide,
            onRetry = viewModel::reload,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.decideDialog?.let { appeal ->
        AppealDecideDialog(
            appeal = appeal,
            submitting = state.submitting,
            onDismiss = viewModel::dismissDecide,
            onSubmit = { decision, notes -> viewModel.decideAppeal(decision, notes) },
        )
    }
}

@Composable
private fun AppealsBody(
    state: AppealsUiState,
    onLoadMore: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenAppeal: (Appeal) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.appeals.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.appeals.isEmpty() ->
                ErrorState(error = state.error, onRetry = onRetry)

            state.appeals.isEmpty() -> EmptyState(
                icon = Icons.Default.Gavel,
                title = stringResource(R.string.staff_appeals_empty),
            )

            else -> InfiniteList(
                items = state.appeals,
                isLoading = state.isLoadingMore,
                hasMore = state.appeals.size < state.total,
                onLoadMore = onLoadMore,
            ) { appeal ->
                AppealRow(
                    appeal = appeal,
                    onOpenListing = { onOpenListing(appeal.listing) },
                    onDecideClick = { onOpenAppeal(appeal) },
                )
            }
        }
    }
}

@Composable
private fun AppealRow(
    appeal: Appeal,
    onOpenListing: () -> Unit,
    onDecideClick: () -> Unit,
) {
    val format = LocalFormat.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        // Title → opens market listing detail.
        Text(
            text = appeal.title.ifBlank { stringResource(R.string.staff_appeals_listing_label, appeal.listing) },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenListing),
        )
        Spacer(Modifier.height(6.dp))

        // Seller row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = appeal.sellerName.ifBlank { appeal.seller },
                seed = appeal.seller,
                size = 20.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = appeal.sellerName.ifBlank { formatFingerprint(appeal.seller) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))

        // Listing-moderation badge + score chip.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StaffStatusBadge(status = appeal.listingModeration)
            ScoreColorChip(score = appeal.score.toInt())
        }
        Spacer(Modifier.height(6.dp))

        // Reason text.
        if (appeal.reason.isNotBlank()) {
            Text(
                text = appeal.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
        }

        // Created + decide button.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = format.formatTimestamp(appeal.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onDecideClick) {
                Text(stringResource(R.string.staff_appeals_decide))
            }
        }
    }
}
