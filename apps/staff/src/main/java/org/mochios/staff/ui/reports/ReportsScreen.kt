// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.format.formatFingerprint
import org.mochios.android.format.personName
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ChoiceFilter
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.FilterBar
import org.mochios.android.ui.components.FilterBarStyle
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.MochiCard
import org.mochios.staff.R
import org.mochios.staff.model.Report
import org.mochios.staff.navigation.StaffApp
import org.mochios.staff.ui.components.StaffCardAction
import org.mochios.staff.ui.components.filterOptions
import org.mochios.staff.ui.components.reportReasonLabel
import org.mochios.staff.ui.components.reportStatusLabels
import org.mochios.staff.ui.components.reportTypeLabel
import org.mochios.staff.ui.components.reportTypeLabels
import org.mochios.staff.ui.components.StaffCardMenu
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.components.StaffUserAvatar
import org.mochios.staff.ui.dialog.ReportActionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavController,
    viewModel: ReportsViewModel = hiltViewModel(),
    filtersOpen: Boolean = false,
    onFiltersDismiss: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReportsEvent.Toast -> snackbar.showSnackbar(resources.getString(event.messageRes))
                is ReportsEvent.Error -> snackbar.showSnackbar(event.error.userMessage())
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ReportsBody(
            state = state,
            filtersOpen = filtersOpen,
            onFiltersDismiss = onFiltersDismiss,
            onTypeChange = viewModel::setType,
            onStatusChange = viewModel::setStatus,
            onLoadMore = viewModel::loadMore,
            onOpenListing = { listingId ->
                StaffApp.listing(listingId)?.let { route -> navController.navigate(route) }
            },
            onOpenReport = viewModel::openAction,
            onRetry = viewModel::reload,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.actionDialog?.let { report ->
        ReportActionDialog(
            report = report,
            submitting = state.submitting,
            onDismiss = viewModel::dismissAction,
            onSubmit = { action, notes -> viewModel.actionReport(action, notes) },
            conversation = state.conversation,
            loadingConversation = state.loadingConversation,
            onShowConversation = viewModel::showConversation,
            // Already-resolved reports (anything other than the canonical
            // `pending` wire value) drop into the
            // metadata + history read-only view.
            readOnly = report.status != "pending",
        )
    }
}

@Composable
private fun ReportsBody(
    state: ReportsUiState,
    filtersOpen: Boolean,
    onFiltersDismiss: () -> Unit,
    onTypeChange: (String?) -> Unit,
    onStatusChange: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenReport: (Report) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FilterBar(
            style = FilterBarStyle.Sheet,
            sheetOpen = filtersOpen,
            onSheetDismiss = onFiltersDismiss,
            filters = listOf(
                ChoiceFilter(
                    label = stringResource(R.string.staff_reports_filter_type_label),
                    chipLabel = stringResource(R.string.staff_filter_label_type),
                    anyLabel = stringResource(R.string.staff_reports_any_type),
                    options = filterOptions(reportTypeLabels),
                    current = state.type,
                    onSelect = onTypeChange,
                ),
                ChoiceFilter(
                    label = stringResource(R.string.staff_reports_filter_status_label),
                    chipLabel = stringResource(R.string.staff_filter_label_status),
                    anyLabel = stringResource(R.string.staff_reports_any_status),
                    options = filterOptions(reportStatusLabels),
                    current = state.status,
                    onSelect = onStatusChange,
                ),
            ),
        )

        when {
            state.isLoading && state.reports.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.reports.isEmpty() ->
                ErrorState(error = state.error, onRetry = onRetry)

            state.reports.isEmpty() -> EmptyState(
                icon = Icons.Default.Flag,
                title = stringResource(R.string.staff_reports_empty),
            )

            else -> InfiniteList(
                items = state.reports,
                key = { report -> report.id },
                load = state.load,
                hasMore = state.paging.more,
                onLoadMore = onLoadMore,
            ) { report ->
                ReportRow(
                    report = report,
                    onOpenListing = onOpenListing,
                    onActionClick = { onOpenReport(report) },
                )
            }
        }
    }
}

@Composable
internal fun ReportRow(
    report: Report,
    onOpenListing: (String) -> Unit,
    onActionClick: () -> Unit,
) {
    val format = LocalFormat.current

    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
    val content: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(16.dp)) {
            // Target: the listing title, or the reported user's resolved name.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reportTarget(report),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val pending = report.status == "pending"
                StaffCardMenu(
                    actions = listOf(
                        StaffCardAction(
                            label = if (pending) {
                                stringResource(R.string.staff_reports_action)
                            } else {
                                stringResource(R.string.staff_reports_view)
                            },
                            icon = if (pending) Icons.Outlined.Gavel else Icons.Outlined.Visibility,
                            onClick = onActionClick,
                        ),
                    ),
                )
            }
            Spacer(Modifier.height(6.dp))

            // Reporter row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                StaffUserAvatar(
                    name = report.reporterName.ifBlank { formatFingerprint(report.reporterFingerprint) },
                    id = report.reporter,
                    size = 20.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = report.reporterName.ifBlank { formatFingerprint(report.reporterFingerprint) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))

            // What the report is about, and where it stands.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = reportTypeLabel(report.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StaffStatusBadge(status = report.status)
            }
            Spacer(Modifier.height(6.dp))

            // Reason, and the reporter's details when they wrote any.
            Text(
                text = reportReasonLabel(report.reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (report.details.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = report.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))

            Text(
                text = format.formatTimestamp(report.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val listing = report.listing
    if (report.type == "listing" && listing != null) {
        MochiCard(
            onClick = { onOpenListing(listing.id) },
            modifier = cardModifier,
            shape = MaterialTheme.shapes.medium,
            content = content,
        )
    } else {
        MochiCard(modifier = cardModifier, shape = MaterialTheme.shapes.medium, content = content)
    }
}

/**
 * What a report is about: the reported listing's title, or the reported
 * person's name, else fingerprint; never an id.
 */
@Composable
internal fun reportTarget(report: Report): String = when (report.type) {
    "listing" -> report.listing?.title?.ifBlank { null } ?: stringResource(R.string.staff_listing_untitled)
    else -> personName(report.targetName, report.targetFingerprint)
}

