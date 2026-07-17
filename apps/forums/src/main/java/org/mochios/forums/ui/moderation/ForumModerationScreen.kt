// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.EmptyState
import org.mochios.forums.R
import org.mochios.forums.model.ModerationLogEntry
import org.mochios.forums.model.ModerationReport
import org.mochios.forums.model.Restriction
import org.mochios.android.R as MochiR

/** A moderation tab as rendered: its label, icon, and the state it selects. */
private data class ModerationTabEntry(
    val tab: ModerationTab,
    val titleRes: Int,
    val icon: ImageVector,
)

// Ordered like the web moderation page: Queue, Reports, Restrictions, Log.
private val TABS = listOf(
    ModerationTabEntry(
        ModerationTab.QUEUE,
        R.string.forums_moderation_tab_queue,
        Icons.Outlined.Schedule,
    ),
    ModerationTabEntry(
        ModerationTab.REPORTS,
        R.string.forums_moderation_tab_reports,
        Icons.Outlined.Flag,
    ),
    ModerationTabEntry(
        ModerationTab.RESTRICTIONS,
        R.string.forums_moderation_tab_restrictions,
        Icons.Outlined.Group,
    ),
    ModerationTabEntry(
        ModerationTab.LOG,
        R.string.forums_moderation_tab_log,
        Icons.Outlined.History,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumModerationScreen(
    onBack: () -> Unit,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedIndex = TABS.indexOfFirst { tab -> tab.tab == uiState.selectedTab }
        .coerceAtLeast(0)
    // Owned here rather than in the tab, so the Scaffold's FAB can open it.
    var showAddRestriction by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forums_moderation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == ModerationTab.RESTRICTIONS) {
                FloatingActionButton(onClick = { showAddRestriction = true }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(
                            R.string.forums_moderation_restriction_add
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                // Primary colour is reserved for the selected tab's divider;
                // the labels stay neutral. Mirrors the forum settings tabs.
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                TABS.forEachIndexed { index, entry ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { viewModel.selectTab(entry.tab) },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = { Icon(entry.icon, contentDescription = null) },
                        text = {
                            Text(
                                stringResource(entry.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        uiState.error!!.userMessage(),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> when (uiState.selectedTab) {
                    ModerationTab.QUEUE -> QueueTab(uiState, viewModel)
                    ModerationTab.REPORTS -> ReportsTab(uiState, viewModel)
                    ModerationTab.RESTRICTIONS -> RestrictionsTab(
                        restrictions = uiState.restrictions,
                        viewModel = viewModel,
                        showAdd = showAddRestriction,
                        onDismissAdd = { showAddRestriction = false },
                    )
                    ModerationTab.LOG -> LogTab(uiState.log)
                }
            }
        }
    }
}

@Composable
private fun QueueTab(uiState: ModerationUiState, viewModel: ModerationViewModel) {
    val queue = uiState.queue ?: return
    if (queue.counts.total == 0) {
        EmptyState(
            icon = Icons.Outlined.Schedule,
            title = stringResource(R.string.forums_moderation_queue_empty),
            subtitle = stringResource(R.string.forums_moderation_queue_empty_subtitle),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (queue.posts.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.forums_moderation_pending_posts),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(queue.posts, key = { "p:${it.id}" }) { post ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(post.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(post.body.take(200),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            OutlinedButton(onClick = { viewModel.approvePost(post.id) }) {
                                Text(stringResource(R.string.forums_post_approve))
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.removePost(post.id) }) {
                                Text(stringResource(R.string.forums_post_remove))
                            }
                        }
                    }
                }
            }
        }
        if (queue.comments.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.forums_moderation_pending_comments),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(queue.comments, key = { "c:${it.id}" }) { c ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(c.body, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            OutlinedButton(onClick = { viewModel.approveComment(c.post, c.id) }) {
                                Text(stringResource(R.string.forums_comment_approve))
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.removeComment(c.post, c.id) }) {
                                Text(stringResource(R.string.forums_comment_remove))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsTab(uiState: ModerationUiState, viewModel: ModerationViewModel) {
    val reports = uiState.reports?.reports ?: emptyList()
    val statuses = listOf(
        "pending" to stringResource(R.string.forums_moderation_reports_pending),
        "resolved" to stringResource(R.string.forums_moderation_reports_resolved),
        "all" to stringResource(R.string.forums_moderation_reports_all),
    )

    if (reports.isEmpty()) {
        // Chips overlay rather than stack, so the empty state centres on the
        // whole tab — level with the other tabs' empty states.
        Box(modifier = Modifier.fillMaxSize()) {
            EmptyState(
                icon = Icons.Outlined.Flag,
                title = stringResource(R.string.forums_moderation_reports_empty),
                subtitle = stringResource(R.string.forums_moderation_reports_empty_subtitle),
            )
            ReportStatusChips(
                statuses = statuses,
                selected = uiState.reportsStatus,
                onSelect = { code -> viewModel.setReportsStatus(code) },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ReportStatusChips(
            statuses = statuses,
            selected = uiState.reportsStatus,
            onSelect = { code -> viewModel.setReportsStatus(code) },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(reports, key = { it.id }) { r ->
                ReportCard(r, onResolve = { viewModel.resolveReport(r.id, it) })
            }
        }
    }
}

/**
 * The report status filter. Three mutually-exclusive statuses read faster as
 * chips than as a dropdown — the current filter shows without opening anything.
 */
@Composable
private fun ReportStatusChips(
    statuses: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        statuses.forEach { (code, label) ->
            FilterChip(
                selected = selected == code,
                onClick = { onSelect(code) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ReportCard(report: ModerationReport, onResolve: (String) -> Unit) {
    val format = LocalFormat.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                AssistChip(onClick = {}, label = { Text(report.type) })
                Spacer(Modifier.width(6.dp))
                AssistChip(onClick = {}, label = { Text(report.reason) })
                Spacer(Modifier.weight(1f))
                Text(
                    format.formatRelativeTime(report.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (report.contentTitle.isNotBlank()) {
                Text(report.contentTitle, style = MaterialTheme.typography.titleSmall)
            }
            if (report.contentPreview.isNotBlank()) {
                Text(
                    report.contentPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (report.details.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.forums_moderation_report_details, report.details),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.forums_moderation_report_meta, report.reporterName, report.authorName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.status == "pending") {
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = { onResolve("ignored") }) {
                        Text(stringResource(R.string.forums_moderation_report_dismiss))
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onResolve("removed") }) {
                        Text(stringResource(R.string.forums_moderation_report_acted))
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.forums_moderation_report_resolved_by,
                        report.resolverName.ifBlank { "—" },
                        report.resolution.ifBlank { "—" },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LogTab(log: List<ModerationLogEntry>) {
    if (log.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.History,
            title = stringResource(R.string.forums_moderation_log_empty),
            subtitle = stringResource(R.string.forums_moderation_log_empty_subtitle),
        )
        return
    }
    val format = LocalFormat.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(log, key = { it.id }) { entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row {
                        Text(entry.action, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(6.dp))
                        AssistChip(onClick = {}, label = { Text(entry.type) })
                        Spacer(Modifier.weight(1f))
                        Text(
                            format.formatRelativeTime(entry.created),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.forums_moderation_log_meta, entry.moderatorName, entry.authorName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.reason.isNotBlank()) {
                        Text(
                            entry.reason,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestrictionsTab(
    restrictions: List<Restriction>,
    viewModel: ModerationViewModel,
    showAdd: Boolean,
    onDismissAdd: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (restrictions.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Group,
                title = stringResource(R.string.forums_moderation_restrictions_empty),
                subtitle = stringResource(R.string.forums_moderation_restrictions_empty_subtitle),
            )
        } else {
            val format = LocalFormat.current
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Bottom padding keeps the last card clear of the FAB.
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 8.dp,
                    end = 12.dp,
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(restrictions, key = { it.user }) { r ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.name.ifBlank { r.user }, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(6.dp))
                                AssistChip(onClick = {}, label = { Text(r.type) })
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { viewModel.removeRestriction(r.user) }) {
                                    Text(stringResource(R.string.forums_moderation_restriction_remove))
                                }
                            }
                            if (r.reason.isNotBlank()) {
                                Text(r.reason, style = MaterialTheme.typography.bodyMedium)
                            }
                            val expiresLabel = r.expires?.let {
                                stringResource(R.string.forums_moderation_restriction_expires, format.formatRelativeTime(it))
                            } ?: stringResource(R.string.forums_moderation_restriction_permanent)
                            Text(
                                expiresLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddRestrictionDialog(
            onConfirm = { user, type, reason, duration ->
                viewModel.addRestriction(user, type, reason, duration)
                onDismissAdd()
            },
            onDismiss = onDismissAdd,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRestrictionDialog(
    onConfirm: (user: String, type: String, reason: String, durationSeconds: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var user by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("muted") }
    var typeExpanded by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    val typeLabels = listOf(
        "muted" to stringResource(R.string.forums_moderation_restriction_type_muted),
        "banned" to stringResource(R.string.forums_moderation_restriction_type_banned),
        "shadowban" to stringResource(R.string.forums_moderation_restriction_type_shadowban),
    )
    val typeLabel = typeLabels.firstOrNull { it.first == type }?.second ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forums_moderation_restriction_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(stringResource(R.string.forums_moderation_restriction_user)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = typeLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.forums_moderation_restriction_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        typeLabels.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    type = code
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.forums_report_details)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.forums_moderation_restriction_duration_seconds)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(user.trim(), type, reason, duration.toLongOrNull()) },
                enabled = user.isNotBlank(),
            ) {
                Text(stringResource(R.string.forums_moderation_restriction_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
    )
}
