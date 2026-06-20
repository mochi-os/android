// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.reports

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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.mochios.staff.model.Report
import org.mochios.staff.ui.components.FilterChipSpec
import org.mochios.staff.ui.components.FilterChipsRow
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.dialog.ReportActionDialog

/**
 * Staff "Reports" screen.
 *
 * Android port of `apps/staff/web/src/features/reports/reports-page.tsx`.
 * Two filter dropdowns (type + status) narrow the list; each row's
 * "Action" / "View" button opens [ReportActionDialog].
 *
 * Navigation: tapping a listing-type report's target opens the market
 * listing detail at `market/listing/{id}`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavController,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val successMsg = stringResource(R.string.staff_reports_action_taken_toast)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReportsEvent.Toast -> {
                    val text = when (event.message) {
                        ReportsViewModel.SUCCESS_TOAST -> successMsg
                        else -> event.message
                    }
                    snackbar.showSnackbar(text)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ReportsBody(
            state = state,
            onTypeChange = viewModel::setType,
            onStatusChange = viewModel::setStatus,
            onLoadMore = viewModel::loadMore,
            onOpenListing = { listingId ->
                navController.navigate("market/listing/$listingId")
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
            // Already-resolved reports (anything other than the canonical
            // `pending` wire value — see [ReportStatus]) drop into the
            // metadata + history read-only view.
            readOnly = report.status != "pending",
        )
    }
}

@Composable
private fun ReportsBody(
    state: ReportsUiState,
    onTypeChange: (String?) -> Unit,
    onStatusChange: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenReport: (Report) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FiltersRow(
            type = state.type,
            status = state.status,
            onTypeChange = onTypeChange,
            onStatusChange = onStatusChange,
        )
        ActiveFilterChips(
            type = state.type,
            status = state.status,
            onTypeChange = onTypeChange,
            onStatusChange = onStatusChange,
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
                isLoading = state.isLoadingMore,
                hasMore = state.reports.size < state.total,
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
private fun ReportRow(
    report: Report,
    onOpenListing: (String) -> Unit,
    onActionClick: () -> Unit,
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
        // Target (listing title links to market detail; user shows resolved name).
        val targetText = targetText(report)
        if (report.type == "listing" && report.listing != null) {
            Text(
                text = targetText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenListing(report.listing.id) },
            )
        } else {
            Text(
                text = targetText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(6.dp))

        // Reporter row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = report.reporterName.ifBlank { report.reporter },
                seed = report.reporter,
                size = 20.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = report.reporterName.ifBlank { formatFingerprint(report.reporter) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))

        // Type / status / reason chips.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StaffStatusBadge(status = report.type)
            StaffStatusBadge(status = report.status)
        }
        Spacer(Modifier.height(6.dp))

        // Reason text.
        Text(
            text = reasonLabel(report.reason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))

        // Created + action row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = format.formatTimestamp(report.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onActionClick) {
                Text(
                    if (report.status == "pending") stringResource(R.string.staff_reports_action)
                    else stringResource(R.string.staff_reports_view),
                )
            }
        }
    }
}

@Composable
private fun targetText(report: Report): String = when (report.type) {
    "listing" -> report.listing?.title
        ?: stringResource(R.string.staff_reports_listing_label, report.target)
    else -> report.targetName.ifBlank { formatFingerprint(report.target) }
}

@Composable
private fun reasonLabel(reason: String): String = when (reason) {
    "prohibited" -> stringResource(R.string.staff_reports_reason_prohibited)
    "counterfeit" -> stringResource(R.string.staff_reports_reason_counterfeit)
    "misleading" -> stringResource(R.string.staff_reports_reason_misleading)
    "inappropriate" -> stringResource(R.string.staff_reports_reason_inappropriate)
    "spam" -> stringResource(R.string.staff_reports_reason_spam)
    "other" -> stringResource(R.string.staff_reports_reason_other)
    else -> reason
}

@Composable
private fun FiltersRow(
    type: String?,
    status: String?,
    onTypeChange: (String?) -> Unit,
    onStatusChange: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropdown(
            label = stringResource(R.string.staff_reports_filter_type_label),
            current = type,
            options = reportTypeOptions(),
            anyLabel = stringResource(R.string.staff_reports_any_type),
            onSelect = onTypeChange,
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            label = stringResource(R.string.staff_reports_filter_status_label),
            current = status,
            options = reportStatusOptions(),
            anyLabel = stringResource(R.string.staff_reports_any_status),
            onSelect = onStatusChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    current: String?,
    options: List<Pair<String, String>>,
    anyLabel: String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: anyLabel
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "$label: $currentLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(anyLabel) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            options.forEach { (value, l) ->
                DropdownMenuItem(
                    text = { Text(l) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun reportTypeOptions(): List<Pair<String, String>> = listOf(
    "listing" to stringResource(R.string.staff_reports_type_listing),
    "user" to stringResource(R.string.staff_reports_type_user),
)

@Composable
private fun reportStatusOptions(): List<Pair<String, String>> = listOf(
    "pending" to stringResource(R.string.staff_reports_status_pending),
    "reviewed" to stringResource(R.string.staff_reports_status_reviewed),
    "actioned" to stringResource(R.string.staff_reports_status_actioned),
    "dismissed" to stringResource(R.string.staff_reports_status_dismissed),
)

/**
 * Removable chips for the active type / status filters. Renders nothing
 * when both filters are at the default `null` (All) value. Mirrors
 * `ActiveFilterChips` in [org.mochios.staff.ui.accounts.AccountsScreen]
 * and `apps/staff/web/src/features/reports/reports-page.tsx`.
 */
@Composable
private fun ActiveFilterChips(
    type: String?,
    status: String?,
    onTypeChange: (String?) -> Unit,
    onStatusChange: (String?) -> Unit,
) {
    val typeLabel = stringResource(R.string.staff_filter_label_type)
    val statusLabel = stringResource(R.string.staff_filter_label_status)
    val typeOpts = reportTypeOptions()
    val statusOpts = reportStatusOptions()
    val chips = buildList {
        if (!type.isNullOrBlank()) {
            val value = typeOpts.firstOrNull { it.first == type }?.second ?: type
            add(FilterChipSpec(typeLabel, value) { onTypeChange(null) })
        }
        if (!status.isNullOrBlank()) {
            val value = statusOpts.firstOrNull { it.first == status }?.second ?: status
            add(FilterChipSpec(statusLabel, value) { onStatusChange(null) })
        }
    }
    FilterChipsRow(chips = chips)
}
