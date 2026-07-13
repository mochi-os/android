// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.moderation

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.staff.R
import org.mochios.staff.model.ModerationEntry
import org.mochios.staff.ui.components.ScoreColorChip
import org.mochios.staff.ui.components.StaffStatusBadge

/**
 * Read-only moderation feed.
 *
 * Android port of `apps/staff/web/src/features/moderation/moderation-page.tsx`.
 * Shows the full Comptroller moderation log, optionally narrowed to a
 * single listing id. Each row links to the underlying market listing.
 *
 * The filter is a numeric field — the screen debounces 300 ms before
 * pushing the parsed id (or `null` when the field is empty) to the
 * ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationLogScreen(
    navController: NavController,
    viewModel: ModerationLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var listingInput by remember { mutableStateOf(state.listingId ?: "") }
    LaunchedEffect(listingInput) {
        delay(300L)
        val parsed = listingInput.trim().ifBlank { null }
        if (parsed != state.listingId) viewModel.setListingId(parsed)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = listingInput,
            onValueChange = { v -> listingInput = v.filter { it.isDigit() } },
            placeholder = { Text(stringResource(R.string.staff_moderation_listing_filter_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            trailingIcon = if (listingInput.isNotEmpty()) {
                {
                    IconButton(onClick = { listingInput = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.staff_moderation_filter_clear),
                        )
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val currentError = state.error
        when {
            state.isLoading && state.entries.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            currentError != null && state.entries.isEmpty() ->
                ErrorState(error = currentError, onRetry = viewModel::reload)

            state.entries.isEmpty() -> EmptyState(
                icon = Icons.Default.History,
                title = stringResource(R.string.staff_moderation_empty),
            )

            else -> InfiniteList(
                items = state.entries,
                isLoading = state.isLoadingMore,
                hasMore = state.entries.size < state.total,
                onLoadMore = viewModel::loadMore,
            ) { entry ->
                ModerationRow(
                    entry = entry,
                    onOpen = {
                        navController.navigate("market/listing/${entry.listing}")
                    },
                )
            }
        }
    }
}

@Composable
private fun ModerationRow(
    entry: ModerationEntry,
    onOpen: () -> Unit,
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
        // Listing link
        val linkText = entry.listingTitle.ifBlank {
            stringResource(R.string.staff_moderation_unknown_listing, entry.listing)
        }
        Text(
            text = linkText,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        )
        Spacer(Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StaffStatusBadge(status = entry.action)
            ScoreColorChip(score = entry.score.toInt())
        }
        Spacer(Modifier.height(6.dp))

        // Actor + reason + created
        Row(verticalAlignment = Alignment.CenterVertically) {
            val actorLabel = when {
                entry.actor == "system" -> stringResource(R.string.staff_moderation_system)
                entry.actorName.isNotBlank() -> entry.actorName
                else -> formatFingerprintSafe(entry.actor)
            }
            Text(
                text = actorLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(120.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = entry.reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = format.formatTimestamp(entry.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Slice a fingerprint into `xxx-xxx-xxx` form. Mirrors the helper in
 * `apps/staff/web/src/lib/format.ts`; defined locally rather than shared
 * because the moderation module doesn't need anything else from there.
 */
internal fun formatFingerprintSafe(id: String): String {
    val fp = id.take(9)
    if (fp.length < 9) return fp
    return "${fp.substring(0, 3)}-${fp.substring(3, 6)}-${fp.substring(6, 9)}"
}
