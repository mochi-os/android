// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.staff.R
import org.mochios.android.format.formatFingerprint
import org.mochios.android.format.formatPrice
import org.mochios.staff.model.ActivityListing
import org.mochios.staff.model.ActivityOrder
import org.mochios.staff.model.ActivitySignup
import org.mochios.staff.model.AuditEntry
import org.mochios.staff.model.MetricsOverview
import org.mochios.staff.model.ModerationEntry
import org.mochios.staff.ui.components.KpiCard
import org.mochios.staff.ui.components.ScoreColorChip
import org.mochios.staff.ui.components.StaffStatusBadge

/**
 * Staff dashboard landing screen. Mirrors
 * `apps/staff/web/src/features/dashboard/dashboard-page.tsx`:
 *
 *  - KPI section: adaptive 180dp grid of [KpiCard]s — one per metric on
 *    [MetricsOverview], plus one extra card per currency for revenue.
 *  - Activity section: TabRow with 5 tabs (Orders / Listings / Signups /
 *    Moderation / Audit), each backed by a paginated table.
 *
 * The drawer + topbar live in `StaffLayout`; this composable renders only
 * the body. The selected tab is round-tripped through the ViewModel's
 * SavedStateHandle so deep-link `?tab=audit` style URLs and process-death
 * restoration both land on the right tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    DashboardBody(
        padding = PaddingValues(0.dp),
        state = state,
        onSetTab = viewModel::setTab,
        onLoadMore = viewModel::loadMore,
    )
}

@Composable
private fun DashboardBody(
    padding: PaddingValues,
    state: DashboardUiState,
    onSetTab: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore(state.currentTab) && !state.isLoading(state.currentTab) && last >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.currentTab) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("kpi") {
            KpiSection(state = state)
        }
        item("tabs") {
            ActivityTabs(current = state.currentTab, onSelect = onSetTab)
        }
        renderTabRows(state)
        if (state.isLoading(state.currentTab)) {
            item("loader") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun KpiSection(state: DashboardUiState) {
    val overview = state.overview
    if (state.overviewLoading && overview == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    if (overview == null) return

    val format = LocalFormat.current
    val cards = buildList {
        add(KpiData(stringResource(R.string.staff_dashboard_kpi_active_listings), format.formatNumber(overview.listings)))
        add(KpiData(stringResource(R.string.staff_dashboard_kpi_total_orders), format.formatNumber(overview.orders)))
        // Revenue: one card per currency. If the server returns no
        // currencies, render a single placeholder card so the slot is
        // visible (matches web's "—" fallback).
        if (overview.revenue.isEmpty()) {
            add(
                KpiData(
                    stringResource(R.string.staff_dashboard_kpi_revenue),
                    stringResource(R.string.staff_dashboard_kpi_revenue_placeholder),
                ),
            )
        } else {
            for (row in overview.revenue) {
                add(
                    KpiData(
                        label = stringResource(R.string.staff_dashboard_kpi_revenue),
                        value = formatPrice(row.total, row.currency),
                        subLabel = row.currency.uppercase(),
                    ),
                )
            }
        }
        add(KpiData(stringResource(R.string.staff_dashboard_kpi_sellers), format.formatNumber(overview.sellers)))
        add(KpiData(stringResource(R.string.staff_dashboard_kpi_buyers), format.formatNumber(overview.buyers)))
        add(KpiData(stringResource(R.string.staff_dashboard_kpi_open_disputes), format.formatNumber(overview.disputes)))
        add(
            KpiData(
                stringResource(R.string.staff_dashboard_kpi_pending_moderation),
                format.formatNumber(overview.pendingModeration),
            ),
        )
    }

    // Adaptive 180dp grid — rendering inside a non-scrolling fixed-height
    // container keeps the LazyColumn parent in charge of vertical scrolling
    // while letting the grid wrap into as many rows as the device width
    // allows.
    val rows = (cards.size + 1) / 2 // worst-case row count for a 180dp grid
    val rowHeight = 112.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight, max = rowHeight * rows + 16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(cards) { card ->
            KpiCard(label = card.label, value = card.value, subLabel = card.subLabel)
        }
    }
}

private data class KpiData(val label: String, val value: String, val subLabel: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityTabs(current: String, onSelect: (String) -> Unit) {
    val tabs = listOf(
        DashboardTab.ORDERS to stringResource(R.string.staff_dashboard_tab_orders),
        DashboardTab.LISTINGS to stringResource(R.string.staff_dashboard_tab_listings),
        DashboardTab.SIGNUPS to stringResource(R.string.staff_dashboard_tab_signups),
        DashboardTab.MODERATION to stringResource(R.string.staff_dashboard_tab_moderation),
        DashboardTab.AUDIT to stringResource(R.string.staff_dashboard_tab_audit),
    )
    val selectedIndex = tabs.indexOfFirst { it.first == current }.coerceAtLeast(0)
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        tabs.forEachIndexed { idx, (id, label) ->
            Tab(
                selected = idx == selectedIndex,
                onClick = { onSelect(id) },
                text = { Text(label, maxLines = 1) },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderTabRows(state: DashboardUiState) {
    when (state.currentTab) {
        DashboardTab.ORDERS -> ordersRows(state.perTabOrders, state.isLoading(DashboardTab.ORDERS))
        DashboardTab.LISTINGS -> listingsRows(state.perTabListings, state.isLoading(DashboardTab.LISTINGS))
        DashboardTab.SIGNUPS -> signupsRows(state.perTabSignups, state.isLoading(DashboardTab.SIGNUPS))
        DashboardTab.MODERATION -> moderationRows(state.perTabModeration, state.isLoading(DashboardTab.MODERATION))
        DashboardTab.AUDIT -> auditRows(state.perTabAudit, state.isLoading(DashboardTab.AUDIT))
    }
}

// ---- Orders ----

private fun androidx.compose.foundation.lazy.LazyListScope.ordersRows(
    items: List<ActivityOrder>,
    isLoading: Boolean,
) {
    if (items.isEmpty() && !isLoading) {
        item("orders-empty") {
            EmptyRow(labelRes = R.string.staff_dashboard_empty_orders)
        }
        return
    }
    item("orders-head") {
        OrdersHeader()
    }
    items(items, key = { "order-${it.id}" }) { order ->
        OrderRow(order = order)
    }
}

@Composable
private fun OrdersHeader() {
    TableHeader(
        listOf(
            stringResource(R.string.staff_dashboard_col_title) to 2f,
            stringResource(R.string.staff_dashboard_col_seller) to 1.4f,
            stringResource(R.string.staff_dashboard_col_buyer) to 1.4f,
            stringResource(R.string.staff_dashboard_col_total) to 1f,
            stringResource(R.string.staff_dashboard_col_status) to 1f,
            stringResource(R.string.staff_dashboard_col_date) to 1f,
        ),
    )
}

@Composable
private fun OrderRow(order: ActivityOrder) {
    val format = LocalFormat.current
    TableRow {
        CellText(
            text = order.title.ifBlank { stringResource(R.string.staff_dashboard_order_fallback, order.id) },
            weight = 2f,
        )
        CellEntity(
            id = order.seller,
            name = order.sellerName,
            weight = 1.4f,
        )
        CellEntity(
            id = order.buyer,
            name = order.buyerName,
            weight = 1.4f,
        )
        CellText(
            text = formatPrice(order.total, order.currency),
            weight = 1f,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            StaffStatusBadge(status = order.status)
        }
        CellText(
            text = format.formatTimestamp(order.created),
            weight = 1f,
            muted = true,
        )
    }
}

// ---- Listings ----

private fun androidx.compose.foundation.lazy.LazyListScope.listingsRows(
    items: List<ActivityListing>,
    isLoading: Boolean,
) {
    if (items.isEmpty() && !isLoading) {
        item("listings-empty") {
            EmptyRow(labelRes = R.string.staff_dashboard_empty_listings)
        }
        return
    }
    item("listings-head") {
        TableHeader(
            listOf(
                stringResource(R.string.staff_dashboard_col_title) to 2f,
                stringResource(R.string.staff_dashboard_col_seller) to 1.4f,
                stringResource(R.string.staff_dashboard_col_status) to 1f,
                stringResource(R.string.staff_dashboard_col_moderation) to 1f,
                stringResource(R.string.staff_dashboard_col_score) to 0.8f,
                stringResource(R.string.staff_dashboard_col_date) to 1f,
            ),
        )
    }
    items(items, key = { "listing-${it.id}" }) { l ->
        ListingRow(listing = l)
    }
}

@Composable
private fun ListingRow(listing: ActivityListing) {
    val format = LocalFormat.current
    TableRow {
        CellText(text = listing.title, weight = 2f)
        CellEntity(id = listing.seller, name = listing.sellerName, weight = 1.4f)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            StaffStatusBadge(status = listing.status)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            StaffStatusBadge(status = listing.moderation)
        }
        Box(
            modifier = Modifier
                .weight(0.8f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            ScoreColorChip(score = listing.score.toInt())
        }
        CellText(
            text = format.formatTimestamp(listing.created),
            weight = 1f,
            muted = true,
        )
    }
}

// ---- Signups ----

private fun androidx.compose.foundation.lazy.LazyListScope.signupsRows(
    items: List<ActivitySignup>,
    isLoading: Boolean,
) {
    if (items.isEmpty() && !isLoading) {
        item("signups-empty") {
            EmptyRow(labelRes = R.string.staff_dashboard_empty_signups)
        }
        return
    }
    item("signups-head") {
        TableHeader(
            listOf(
                stringResource(R.string.staff_dashboard_col_name) to 2f,
                stringResource(R.string.staff_dashboard_col_seller) to 1f,
                stringResource(R.string.staff_dashboard_col_date) to 1f,
            ),
        )
    }
    items(items, key = { "signup-${it.id}" }) { s ->
        SignupRow(signup = s)
    }
}

@Composable
private fun SignupRow(signup: ActivitySignup) {
    val format = LocalFormat.current
    TableRow {
        CellText(text = signup.name.ifBlank { formatFingerprint(signup.id) }, weight = 2f)
        CellText(
            text = if (signup.seller != 0) {
                stringResource(R.string.staff_dashboard_yes)
            } else {
                stringResource(R.string.staff_dashboard_no)
            },
            weight = 1f,
        )
        CellText(
            text = format.formatTimestamp(signup.created),
            weight = 1f,
            muted = true,
        )
    }
}

// ---- Moderation ----

private fun androidx.compose.foundation.lazy.LazyListScope.moderationRows(
    items: List<ModerationEntry>,
    isLoading: Boolean,
) {
    if (items.isEmpty() && !isLoading) {
        item("moderation-empty") {
            EmptyRow(labelRes = R.string.staff_dashboard_empty_moderation)
        }
        return
    }
    item("moderation-head") {
        TableHeader(
            listOf(
                stringResource(R.string.staff_dashboard_col_listing) to 2f,
                stringResource(R.string.staff_dashboard_col_action) to 1f,
                stringResource(R.string.staff_dashboard_col_actor) to 1.2f,
                stringResource(R.string.staff_dashboard_col_score) to 0.8f,
                stringResource(R.string.staff_dashboard_col_reason) to 1.4f,
                stringResource(R.string.staff_dashboard_col_date) to 1f,
            ),
        )
    }
    items(items, key = { "moderation-${it.id}" }) { e ->
        ModerationRow(entry = e)
    }
}

@Composable
private fun ModerationRow(entry: ModerationEntry) {
    val format = LocalFormat.current
    TableRow {
        CellText(
            text = entry.listingTitle.ifBlank {
                stringResource(R.string.staff_dashboard_listing_fallback, entry.listing)
            },
            weight = 2f,
        )
        CellText(text = entry.action, weight = 1f)
        CellText(
            text = if (entry.actor == "system") {
                stringResource(R.string.staff_dashboard_system)
            } else {
                entry.actorName.ifBlank { formatFingerprint(entry.actor) }
            },
            weight = 1.2f,
        )
        Box(
            modifier = Modifier
                .weight(0.8f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            ScoreColorChip(score = entry.score.toInt())
        }
        CellText(text = entry.reason, weight = 1.4f)
        CellText(
            text = format.formatTimestamp(entry.created),
            weight = 1f,
            muted = true,
        )
    }
}

// ---- Audit ----

private fun androidx.compose.foundation.lazy.LazyListScope.auditRows(
    items: List<AuditEntry>,
    isLoading: Boolean,
) {
    if (items.isEmpty() && !isLoading) {
        item("audit-empty") {
            EmptyRow(labelRes = R.string.staff_dashboard_empty_audit)
        }
        return
    }
    item("audit-head") {
        TableHeader(
            listOf(
                stringResource(R.string.staff_dashboard_col_action) to 1.2f,
                stringResource(R.string.staff_dashboard_col_object) to 1.4f,
                stringResource(R.string.staff_dashboard_col_actor) to 1.2f,
                stringResource(R.string.staff_dashboard_col_detail) to 2f,
                stringResource(R.string.staff_dashboard_col_date) to 1f,
            ),
        )
    }
    items(items, key = { "audit-${it.id}" }) { e ->
        AuditRow(entry = e)
    }
}

@Composable
private fun AuditRow(entry: AuditEntry) {
    val format = LocalFormat.current
    val obj = entry.`object`
    val objectDisplay = "${entry.kind}/${if (obj.length > 12) formatFingerprint(obj) else obj}"
    val actorLabel = if (entry.actor == "system") {
        stringResource(R.string.staff_dashboard_system)
    } else {
        entry.actorName.ifBlank { formatFingerprint(entry.actor) }
    }
    TableRow {
        CellText(text = entry.action, weight = 1.2f)
        CellText(text = objectDisplay, weight = 1.4f)
        CellText(text = actorLabel, weight = 1.2f)
        CellText(text = parseAuditDetail(entry.action, entry.data), weight = 2f, muted = true)
        CellText(text = format.formatTimestamp(entry.timestamp), weight = 1f, muted = true)
    }
}

/**
 * Pull a compact human-readable summary out of an [AuditEntry.data] JSON
 * blob. Mirrors the slice of `useFormatAuditDetail` /
 * `apps/staff/web/src/components/shared/audit-labels.ts` we display in
 * the dashboard table — full ICU coverage lives on the dedicated audit
 * screen.
 *
 * Lightweight regex parsing: the wire shape is `{"reason":"...","notes":
 * "..."}`-style flat objects, never nested. We pull the most likely keys
 * (`reason`, `notes`, `amount`, `currency`, `value`) and join them.
 */
private fun parseAuditDetail(@Suppress("UNUSED_PARAMETER") action: String, data: String): String {
    if (data.isBlank()) return ""
    val keys = listOf("reason", "notes", "amount", "currency", "value", "resolution", "decision")
    val out = mutableListOf<String>()
    for (k in keys) {
        val re = Regex("\"${Regex.escape(k)}\"\\s*:\\s*\"([^\"]*)\"")
        val m = re.find(data) ?: continue
        val v = m.groupValues[1]
        if (v.isNotBlank()) out += "$k=$v"
    }
    if (out.isEmpty()) {
        // Fall back to numeric amount keys (`refund_amount`, `total`, ...).
        val num = Regex("\"(refund_amount|total|amount|fee)\"\\s*:\\s*(\\d+)").find(data)
        if (num != null) {
            out += "${num.groupValues[1]}=${num.groupValues[2]}"
        }
    }
    return out.joinToString(" · ")
}

// ---- Shared table primitives ----

@Composable
private fun TableHeader(columns: List<Pair<String, Float>>) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            for ((label, weight) in columns) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .weight(weight)
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TableRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CellText(
    text: String,
    weight: Float,
    muted: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        maxLines = 1,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 4.dp),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CellEntity(
    id: String,
    name: String,
    weight: Float,
) {
    Row(
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EntityAvatar(
            name = name.ifBlank { id },
            src = if (id.isNotBlank()) "/people/$id/-/avatar" else null,
            seed = id.ifBlank { name },
            size = 20.dp,
        )
        Text(
            text = name.ifBlank { formatFingerprint(id) },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyRow(labelRes: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
