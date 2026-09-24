// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.format.formatFingerprint
import org.mochios.android.format.formatPrice
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.staff.R
import org.mochios.staff.model.ActivityListing
import org.mochios.staff.model.ActivityOrder
import org.mochios.staff.model.ActivitySignup
import org.mochios.staff.model.AuditEntry
import org.mochios.staff.model.ModerationEntry
import org.mochios.staff.ui.components.KpiCard
import org.mochios.staff.ui.components.ScoreColorChip
import org.mochios.staff.ui.components.StaffStatusBadge

/**
 * Port of `apps/staff/web/src/features/dashboard/dashboard-page.tsx`. The
 * selected tab is kept in the ViewModel's SavedStateHandle so `?tab=` deep
 * links and process death restore it.
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
    // Derive only the scroll position: `state` read inside this unkeyed
    // remember would be pinned to its first-composition value across tab
    // switches.
    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }
    val tab = state.currentTab
    LaunchedEffect(reachedEnd, tab, state.hasMore(tab), state.isLoading(tab)) {
        if (reachedEnd && state.hasMore(tab) && !state.isLoading(tab)) onLoadMore()
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
    MochiTabRow(
        tabs = tabs.map { (_, label) -> MochiTab(label) },
        selectedIndex = selectedIndex,
        onSelect = { index -> onSelect(tabs[index].first) },
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

private fun LazyListScope.renderTabRows(state: DashboardUiState) {
    when (state.currentTab) {
        DashboardTab.ORDERS -> ordersRows(state.perTabOrders, state.isLoading(DashboardTab.ORDERS))
        DashboardTab.LISTINGS -> listingsRows(state.perTabListings, state.isLoading(DashboardTab.LISTINGS))
        DashboardTab.SIGNUPS -> signupsRows(state.perTabSignups, state.isLoading(DashboardTab.SIGNUPS))
        DashboardTab.MODERATION -> moderationRows(state.perTabModeration, state.isLoading(DashboardTab.MODERATION))
        DashboardTab.AUDIT -> auditRows(state.perTabAudit, state.isLoading(DashboardTab.AUDIT))
    }
}

// ---- Orders ----

private fun LazyListScope.ordersRows(items: List<ActivityOrder>, isLoading: Boolean) {
    if (items.isEmpty() && !isLoading) {
        item("orders-empty") {
            EmptyRow(labelRes = R.string.staff_dashboard_empty_orders)
        }
        return
    }
    items(items, key = { order -> "order-${order.id}" }) { order ->
        OrderCard(order = order)
    }
}

@Composable
private fun OrderCard(order: ActivityOrder) {
    val format = LocalFormat.current
    ActivityCard {
        CardTitleRow(
            title = order.title.ifBlank { stringResource(R.string.staff_dashboard_order_fallback, order.id) },
        ) {
            StaffStatusBadge(status = order.status)
        }
        Spacer(Modifier.height(8.dp))
        LabeledEntity(
            label = stringResource(R.string.staff_dashboard_col_seller),
            id = order.seller,
            name = order.sellerName,
            fingerprint = order.sellerFingerprint,
        )
        Spacer(Modifier.height(4.dp))
        LabeledEntity(
            label = stringResource(R.string.staff_dashboard_col_buyer),
            id = order.buyer,
            name = order.buyerName,
            fingerprint = order.buyerFingerprint,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatPrice(order.total, order.currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TimeText(text = format.formatTimestamp(order.created))
        }
    }
}

// ---- Listings ----

private fun LazyListScope.listingsRows(items: List<ActivityListing>, isLoading: Boolean) {
    if (items.isEmpty() && !isLoading) {
        item("listings-empty") {
            EmptyRow(labelRes = R.string.staff_dashboard_empty_listings)
        }
        return
    }
    items(items, key = { listing -> "listing-${listing.id}" }) { listing ->
        ListingCard(listing = listing)
    }
}

@Composable
private fun ListingCard(listing: ActivityListing) {
    val format = LocalFormat.current
    ActivityCard {
        CardTitleRow(title = listing.title) {
            ScoreColorChip(score = listing.score.toInt())
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StaffStatusBadge(status = listing.status)
            StaffStatusBadge(status = listing.moderation)
        }
        Spacer(Modifier.height(8.dp))
        Byline(
            id = listing.seller,
            name = listing.sellerName.ifBlank { formatFingerprint(listing.sellerFingerprint) },
            time = format.formatTimestamp(listing.created),
        )
    }
}

// ---- Signups ----

private fun LazyListScope.signupsRows(items: List<ActivitySignup>, isLoading: Boolean) {
    if (items.isEmpty() && !isLoading) {
        item("signups-empty") {
            EmptyRow(labelRes = R.string.staff_dashboard_empty_signups)
        }
        return
    }
    items(items, key = { signup -> "signup-${signup.id}" }) { signup ->
        SignupCard(signup = signup)
    }
}

@Composable
private fun SignupCard(signup: ActivitySignup) {
    val format = LocalFormat.current
    val name = signup.name.ifBlank { formatFingerprint(signup.fingerprint) }
    ActivityCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = name,
                src = if (signup.id.isNotBlank()) "/people/${signup.id}/-/avatar" else null,
                seed = signup.id.ifBlank { name },
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TimeText(text = format.formatTimestamp(signup.created))
            }
            if (signup.seller != 0) {
                LabelChip(text = stringResource(R.string.staff_dashboard_col_seller))
            }
        }
    }
}

// ---- Moderation ----

private fun LazyListScope.moderationRows(items: List<ModerationEntry>, isLoading: Boolean) {
    if (items.isEmpty() && !isLoading) {
        item("moderation-empty") {
            EmptyRow(labelRes = R.string.staff_dashboard_empty_moderation)
        }
        return
    }
    items(items, key = { entry -> "moderation-${entry.id}" }) { entry ->
        ModerationCard(entry = entry)
    }
}

@Composable
private fun ModerationCard(entry: ModerationEntry) {
    val format = LocalFormat.current
    ActivityCard {
        CardTitleRow(
            title = entry.listingTitle.ifBlank {
                stringResource(R.string.staff_dashboard_listing_fallback, entry.listing)
            },
        ) {
            ScoreColorChip(score = entry.score.toInt())
        }
        Spacer(Modifier.height(8.dp))
        StaffStatusBadge(status = entry.action)
        if (entry.reason.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            LabeledText(label = stringResource(R.string.staff_dashboard_col_reason), text = entry.reason)
        }
        Spacer(Modifier.height(8.dp))
        Byline(
            id = if (entry.actor == "system") "" else entry.actor,
            name = actorLabel(entry.actor, entry.actorName, entry.actorFingerprint),
            time = format.formatTimestamp(entry.created),
        )
    }
}

// ---- Audit ----

private fun LazyListScope.auditRows(items: List<AuditEntry>, isLoading: Boolean) {
    if (items.isEmpty() && !isLoading) {
        item("audit-empty") {
            EmptyRow(labelRes = R.string.staff_dashboard_empty_audit)
        }
        return
    }
    items(items, key = { entry -> "audit-${entry.id}" }) { entry ->
        AuditCard(entry = entry)
    }
}

@Composable
private fun AuditCard(entry: AuditEntry) {
    val format = LocalFormat.current
    val objectLabel = when (entry.kind) {
        "account", "staff" -> entry.objectName.ifBlank { formatFingerprint(entry.objectFingerprint) }
        else -> if (entry.`object`.all { char -> char.isDigit() }) "#${entry.`object`}" else entry.`object`
    }
    val detail = parseAuditDetail(entry.action, entry.data)
    ActivityCard {
        CardTitleRow(title = entry.action)
        Spacer(Modifier.height(8.dp))
        LabeledText(
            label = stringResource(R.string.staff_dashboard_col_object),
            text = "${entry.kind}/$objectLabel",
        )
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            LabeledText(label = stringResource(R.string.staff_dashboard_col_detail), text = detail)
        }
        Spacer(Modifier.height(8.dp))
        Byline(
            id = if (entry.actor == "system") "" else entry.actor,
            name = actorLabel(entry.actor, entry.actorName, entry.actorFingerprint),
            time = format.formatTimestamp(entry.timestamp),
        )
    }
}

/**
 * Compact summary of an [AuditEntry.data] blob for the dashboard card; full
 * labelling lives on the audit screen. `data` is a flat object, so regexes
 * suffice.
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

// ---- Shared card primitives ----

@Composable
private fun ActivityCard(content: @Composable ColumnScope.() -> Unit) {
    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun CardTitleRow(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

@Composable
private fun LabeledEntity(label: String, id: String, name: String, fingerprint: String) {
    val display = name.ifBlank { formatFingerprint(fingerprint) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel(text = label)
        EntityAvatar(
            name = display,
            src = if (id.isNotBlank()) "/people/$id/-/avatar" else null,
            seed = id.ifBlank { display },
            size = 20.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = display,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LabeledText(label: String, text: String) {
    Row {
        FieldLabel(text = label)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .width(64.dp)
            .padding(top = 2.dp),
    )
}

@Composable
private fun Byline(id: String, name: String, time: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EntityAvatar(
            name = name,
            src = if (id.isNotBlank()) "/people/$id/-/avatar" else null,
            seed = id.ifBlank { name },
            size = 20.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TimeText(text = time)
    }
}

@Composable
private fun TimeText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun LabelChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun actorLabel(actor: String, name: String, fingerprint: String): String =
    if (actor == "system") {
        stringResource(R.string.staff_dashboard_system)
    } else {
        name.ifBlank { formatFingerprint(fingerprint) }
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
