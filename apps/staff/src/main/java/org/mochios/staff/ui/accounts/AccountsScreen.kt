// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.delay
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.staff.R
import org.mochios.staff.model.Account
import org.mochios.staff.ui.components.FilterChipSpec
import org.mochios.staff.ui.components.FilterChipsRow
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.dialog.AccountActionDialog
import org.mochios.staff.ui.dialog.AccountAuditDialog

/**
 * Staff "Accounts" moderation screen body.
 *
 * Android port of `apps/staff/web/src/features/accounts/accounts-page.tsx`.
 * Two filter dropdowns (status + seller) and a debounced search input narrow
 * the list; each row exposes History / Suspend / Unsuspend / Ban / Unban
 * actions whose visibility tracks the account's current `status` field
 * exactly the same way the web page does.
 *
 * Mutating actions route through the dialog at [AccountActionDialog];
 * History opens the read-only [AccountAuditDialog].
 *
 * The drawer + topbar live in `StaffLayout`; this screen renders only the
 * filter row, list, and a per-screen snackbar overlay (toast events come
 * from the screen's own VM).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val suspendedMsg = stringResource(R.string.staff_accounts_suspended_toast)
    val unsuspendedMsg = stringResource(R.string.staff_accounts_unsuspended_toast)
    val bannedMsg = stringResource(R.string.staff_accounts_banned_toast)
    val unbannedMsg = stringResource(R.string.staff_accounts_unbanned_toast)

    // Snackbar plumbing — translate ViewModel sentinel markers to their
    // localised string before showing.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountsEvent.Toast -> {
                    val text = when (event.message) {
                        AccountsViewModel.SUCCESS_SUSPENDED -> suspendedMsg
                        AccountsViewModel.SUCCESS_UNSUSPENDED -> unsuspendedMsg
                        AccountsViewModel.SUCCESS_BANNED -> bannedMsg
                        AccountsViewModel.SUCCESS_UNBANNED -> unbannedMsg
                        else -> event.message
                    }
                    snackbar.showSnackbar(text)
                }
            }
        }
    }

    // 300 ms debounce on the search field — match the web side exactly.
    var searchInput by remember { mutableStateOf(state.query) }
    LaunchedEffect(searchInput) {
        delay(300L)
        if (searchInput != state.query) viewModel.setQuery(searchInput)
    }

    Box(Modifier.fillMaxSize()) {
        AccountsBody(
            state = state,
            searchInput = searchInput,
            onSearchInput = { searchInput = it },
            onClearSearch = {
                searchInput = ""
                viewModel.setQuery("")
            },
            onStatusChange = viewModel::setStatus,
            onSellerChange = viewModel::setSeller,
            onLoadMore = viewModel::loadMore,
            onHistory = viewModel::openHistory,
            onAction = { type, account -> viewModel.openAction(type, account) },
            onRetry = viewModel::reload,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.pendingAction?.let { pending ->
        AccountActionDialog(
            action = pending,
            submitting = state.submitting,
            onDismiss = viewModel::dismissAction,
            onSubmit = viewModel::submitAction,
        )
    }

    state.historyAccount?.let { account ->
        AccountAuditDialog(
            account = account,
            onDismiss = viewModel::dismissHistory,
        )
    }
}

@Composable
private fun AccountsBody(
    state: AccountsUiState,
    searchInput: String,
    onSearchInput: (String) -> Unit,
    onClearSearch: () -> Unit,
    onStatusChange: (String?) -> Unit,
    onSellerChange: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onHistory: (Account) -> Unit,
    onAction: (AccountActionType, Account) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FiltersRow(
            status = state.status,
            seller = state.seller,
            onStatusChange = onStatusChange,
            onSellerChange = onSellerChange,
        )
        OutlinedTextField(
            value = searchInput,
            onValueChange = onSearchInput,
            placeholder = { Text(stringResource(R.string.staff_accounts_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchInput.isNotEmpty()) {
                {
                    IconButton(onClick = onClearSearch) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.staff_accounts_search_clear),
                        )
                    }
                }
            } else null,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ActiveFilterChips(
            status = state.status,
            seller = state.seller,
            query = state.query,
            onStatusChange = onStatusChange,
            onSellerChange = onSellerChange,
            onClearSearch = onClearSearch,
        )

        when {
            state.isLoading && state.accounts.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.accounts.isEmpty() ->
                ErrorState(error = state.error, onRetry = onRetry)

            state.accounts.isEmpty() -> EmptyState(
                icon = Icons.Default.People,
                title = stringResource(R.string.staff_accounts_empty),
            )

            else -> InfiniteList(
                items = state.accounts,
                isLoading = state.isLoadingMore,
                hasMore = state.accounts.size < state.total,
                onLoadMore = onLoadMore,
            ) { account ->
                AccountRow(
                    account = account,
                    onHistory = { onHistory(account) },
                    onAction = { type -> onAction(type, account) },
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    onHistory: () -> Unit,
    onAction: (AccountActionType) -> Unit,
) {
    val format = LocalFormat.current
    val displayName = account.name.ifBlank { stringResource(R.string.staff_accounts_unnamed) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = displayName,
                seed = account.id,
                size = 32.dp,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (account.seller != 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.staff_accounts_seller_chip)) },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                    VerifiedChip(level = account.verified)
                }
                Text(
                    text = formatFingerprint(account.id),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Status / sales / rating / joined row.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StaffStatusBadge(status = account.status.ifBlank { "active" })
            Text(
                text = stringResource(R.string.staff_accounts_sales, account.sales),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RatingMini(rating = account.rating, reviews = account.reviews)
            Spacer(Modifier.weight(1f))
            Text(
                text = format.formatTimestamp(account.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        AccountActionRow(account = account, onHistory = onHistory, onAction = onAction)
    }
}

@Composable
private fun AccountActionRow(
    account: Account,
    onHistory: () -> Unit,
    onAction: (AccountActionType) -> Unit,
) {
    val status = account.status.ifBlank { "active" }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(onClick = onHistory) {
            Icon(
                Icons.Default.History,
                contentDescription = stringResource(R.string.staff_accounts_history),
            )
        }
        // Suspend appears only for active sellers (mirroring web).
        if (status == "active" && account.seller != 0) {
            OutlinedButton(onClick = { onAction(AccountActionType.SUSPEND) }) {
                Text(stringResource(R.string.staff_accounts_suspend))
            }
        }
        if (status == "suspended") {
            OutlinedButton(onClick = { onAction(AccountActionType.UNSUSPEND) }) {
                Text(stringResource(R.string.staff_accounts_unsuspend))
            }
        }
        if (status != "banned") {
            OutlinedButton(onClick = { onAction(AccountActionType.BAN) }) {
                Text(stringResource(R.string.staff_accounts_ban))
            }
        } else {
            OutlinedButton(onClick = { onAction(AccountActionType.UNBAN) }) {
                Text(stringResource(R.string.staff_accounts_unban))
            }
        }
    }
}

/**
 * Verification-level chip. Mirrors the `VerifiedBadge` component in
 * `apps/staff/web/src/features/accounts/accounts-page.tsx` — a shield icon
 * plus an "L{level}" label so the chip stays readable at a glance instead of
 * the older opaque "v3" shorthand. Hidden entirely when [level] is zero
 * (unverified accounts don't get a chip at all, matching how the web table
 * omits the badge in that case after this redesign).
 */
@Composable
private fun VerifiedChip(level: Int) {
    if (level <= 0) return
    val label = stringResource(R.string.staff_accounts_verification_level, level)
    val (bg, fg) = if (level >= 2) {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            Icons.Default.VerifiedUser,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

/**
 * Small inline rating widget — five-star precision is overkill in the table
 * row, so we display "★ 4.7 (12)" instead. The web table uses the same
 * abbreviated form. Empty/zero ratings render as a placeholder dash.
 */
@Composable
private fun RatingMini(rating: Double, reviews: Long) {
    val format = LocalFormat.current
    if (rating <= 0.0) {
        Text(
            text = "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // Server stores rating * 100 (e.g. 470 = 4.7); divide before display.
    val starRating = rating / 100.0
    val starColor = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = starColor,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = format.formatNumber(starRating, decimals = 1),
            style = MaterialTheme.typography.labelSmall,
        )
        if (reviews > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "($reviews)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FiltersRow(
    status: String?,
    seller: String?,
    onStatusChange: (String?) -> Unit,
    onSellerChange: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropdown(
            label = stringResource(R.string.staff_accounts_filter_status_label),
            current = status,
            options = accountStatusOptions(),
            anyLabel = stringResource(R.string.staff_accounts_any_status),
            onSelect = onStatusChange,
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            label = stringResource(R.string.staff_accounts_filter_seller_label),
            current = seller,
            options = sellerOptions(),
            anyLabel = stringResource(R.string.staff_accounts_any_seller),
            onSelect = onSellerChange,
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
private fun accountStatusOptions(): List<Pair<String, String>> = listOf(
    "active" to stringResource(R.string.staff_accounts_status_active),
    "suspended" to stringResource(R.string.staff_accounts_status_suspended),
    "banned" to stringResource(R.string.staff_accounts_status_banned),
)

@Composable
private fun sellerOptions(): List<Pair<String, String>> = listOf(
    "yes" to stringResource(R.string.staff_accounts_seller_sellers),
    "no" to stringResource(R.string.staff_accounts_seller_buyers),
)

@Composable
private fun ActiveFilterChips(
    status: String?,
    seller: String?,
    query: String,
    onStatusChange: (String?) -> Unit,
    onSellerChange: (String?) -> Unit,
    onClearSearch: () -> Unit,
) {
    val statusLabel = stringResource(R.string.staff_filter_label_status)
    val sellerLabel = stringResource(R.string.staff_filter_label_seller)
    val queryLabel = stringResource(R.string.staff_filter_label_query)
    val statusOptions = accountStatusOptions()
    val sellerOpts = sellerOptions()
    val chips = buildList {
        if (!status.isNullOrBlank()) {
            val value = statusOptions.firstOrNull { it.first == status }?.second ?: status
            add(FilterChipSpec(statusLabel, value) { onStatusChange(null) })
        }
        if (!seller.isNullOrBlank()) {
            val value = sellerOpts.firstOrNull { it.first == seller }?.second ?: seller
            add(FilterChipSpec(sellerLabel, value) { onSellerChange(null) })
        }
        if (query.isNotBlank()) {
            add(FilterChipSpec(queryLabel, query, onClearSearch))
        }
    }
    FilterChipsRow(chips = chips)
}

/**
 * Slice a person fingerprint into the 9-char "xxx-xxx-xxx" display form,
 * mirroring `formatFingerprint` in `apps/staff/web/src/lib/format.ts`.
 */
internal fun formatFingerprint(id: String): String {
    val fp = id.take(9).padEnd(9, ' ').trimEnd()
    if (fp.length < 9) return fp
    return "${fp.substring(0, 3)}-${fp.substring(3, 6)}-${fp.substring(6, 9)}"
}
