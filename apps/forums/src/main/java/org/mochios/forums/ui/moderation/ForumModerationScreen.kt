// Copyright © 2026 Mochi OÜ
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.forums.R
import org.mochios.forums.model.ModerationLogEntry
import org.mochios.forums.model.ModerationReport
import org.mochios.forums.model.Restriction
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumModerationScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf(
        stringResource(R.string.forums_moderation_tab_queue),
        stringResource(R.string.forums_moderation_tab_reports),
        stringResource(R.string.forums_moderation_tab_log),
        stringResource(R.string.forums_moderation_tab_restrictions),
    )

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
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.forums_moderation_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = uiState.selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) },
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
                    0 -> QueueTab(uiState, viewModel)
                    1 -> ReportsTab(uiState, viewModel)
                    2 -> LogTab(uiState.log)
                    3 -> RestrictionsTab(uiState.restrictions, viewModel)
                }
            }
        }
    }
}

@Composable
private fun QueueTab(uiState: ModerationUiState, viewModel: ModerationViewModel) {
    val queue = uiState.queue ?: return
    if (queue.counts.total == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.forums_moderation_queue_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsTab(uiState: ModerationUiState, viewModel: ModerationViewModel) {
    val reports = uiState.reports?.reports ?: emptyList()
    var statusExpanded by remember { mutableStateOf(false) }
    val statusLabel = when (uiState.reportsStatus) {
        "resolved" -> stringResource(R.string.forums_moderation_reports_resolved)
        "all" -> stringResource(R.string.forums_moderation_reports_all)
        else -> stringResource(R.string.forums_moderation_reports_pending)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ExposedDropdownMenuBox(
            expanded = statusExpanded,
            onExpandedChange = { statusExpanded = it },
            modifier = Modifier.padding(12.dp),
        ) {
            OutlinedTextField(
                value = statusLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.forums_moderation_reports_status)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = statusExpanded,
                onDismissRequest = { statusExpanded = false },
            ) {
                listOf(
                    "pending" to stringResource(R.string.forums_moderation_reports_pending),
                    "resolved" to stringResource(R.string.forums_moderation_reports_resolved),
                    "all" to stringResource(R.string.forums_moderation_reports_all),
                ).forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            statusExpanded = false
                            viewModel.setReportsStatus(code)
                        },
                    )
                }
            }
        }

        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.forums_moderation_reports_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.forums_moderation_log_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
private fun RestrictionsTab(restrictions: List<Restriction>, viewModel: ModerationViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = { showAdd = true },
            modifier = Modifier.padding(12.dp),
        ) {
            Text(stringResource(R.string.forums_moderation_restriction_add))
        }
        HorizontalDivider()
        if (restrictions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.forums_moderation_restrictions_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val format = LocalFormat.current
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                showAdd = false
            },
            onDismiss = { showAdd = false },
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
