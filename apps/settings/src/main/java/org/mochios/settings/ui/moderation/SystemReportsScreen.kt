// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.settings.R
import org.mochios.settings.api.Report
import org.mochios.settings.ui.login.StepUpHost
import org.mochios.settings.ui.networks.networkLabel
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemReportsScreen(
    onBack: () -> Unit,
    viewModel: SystemReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<Pair<Report, ReportAction>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.error) {
        val failure = state.error
        if (failure != null && !state.isLoading && state.reports.isNotEmpty()) {
            snackbar.showSnackbar(failure.userMessage())
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
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
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MochiTabRow(
                tabs = listOf(
                    MochiTab(stringResource(R.string.reports_open)),
                    MochiTab(stringResource(R.string.reports_resolved)),
                ),
                selectedIndex = state.state.ordinal,
                onSelect = { viewModel.show(ReportState.entries[it]) },
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null && state.reports.isEmpty() -> ErrorState(
                        error = state.error!!,
                        onRetry = { viewModel.refresh() },
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.reports.isEmpty()) {
                            item("empty") {
                                Text(
                                    stringResource(
                                        if (state.state == ReportState.OPEN) R.string.reports_none_open else R.string.reports_none_resolved,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(state.reports, key = { it.id }) { report ->
                            ReportCard(
                                report = report,
                                busy = state.busy == report.id,
                                onDismiss = { viewModel.dismiss(report) },
                                onAction = { pending = report to it },
                            )
                        }
                    }
                }
            }
        }
    }

    pending?.let { (report, action) ->
        MochiAlertDialog(
            onDismissRequest = { pending = null },
            title = when (action) {
                is ReportAction.Person -> stringResource(R.string.reports_block_title, action.name)
                is ReportAction.Server -> stringResource(R.string.reports_block_title, action.host)
                is ReportAction.Suspend -> stringResource(R.string.reports_suspend_title)
            },
            text = when (action) {
                is ReportAction.Person -> stringResource(R.string.reports_block_person_message)
                is ReportAction.Server -> stringResource(R.string.reports_block_server_message, action.host)
                is ReportAction.Suspend -> stringResource(R.string.reports_suspend_message, action.username)
            },
            confirmText = stringResource(
                if (action is ReportAction.Suspend) R.string.reports_suspend else R.string.networks_block,
            ),
            onConfirm = {
                pending = null
                viewModel.act(report, action)
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
    StepUpHost(viewModel.stepUp)
}

/** The label a report's category is shown by, or none. */
@Composable
internal fun categoryLabel(category: String): String? = when (category) {
    "spam" -> stringResource(R.string.reports_category_spam)
    "abuse" -> stringResource(R.string.reports_category_abuse)
    "legal" -> stringResource(R.string.reports_category_legal)
    "violation" -> stringResource(R.string.reports_category_violation)
    "other" -> stringResource(R.string.reports_category_other)
    else -> null
}

@Composable
internal fun outcomeLabel(outcome: String): String = when (outcome) {
    "dismissed" -> stringResource(R.string.reports_outcome_dismissed)
    "blocked" -> stringResource(R.string.reports_outcome_blocked)
    "suspended" -> stringResource(R.string.reports_outcome_suspended)
    else -> ""
}

/**
 * One report: when it came and by which network, who sent it and whom or
 * which room it is about, why, the reported text and links to what was
 * complained of; how it was resolved, or what can be done about it.
 */
@Composable
internal fun ReportCard(
    report: Report,
    busy: Boolean,
    onDismiss: () -> Unit,
    onAction: (ReportAction) -> Unit,
) {
    val format = LocalFormat.current
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    format.formatTimestamp(report.created) + " · " + networkLabel(report.network),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
                Text(stringResource(R.string.reports_from, report.reporter.name), style = MaterialTheme.typography.bodySmall)
                val person = report.person
                val room = report.room
                when {
                    person != null -> {
                        Text(
                            stringResource(R.string.reports_about, person.name),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        report.account?.let { account ->
                            Text(
                                account.username + if (account.status == "suspended") " · " + stringResource(R.string.system_users_status_suspended) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                            )
                        }
                    }
                    room != null -> Text(
                        stringResource(R.string.reports_room, room.name),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                categoryLabel(report.category)?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (report.comment.isNotEmpty()) Text(report.comment, style = MaterialTheme.typography.bodyMedium)
                if (report.content.isNotEmpty()) {
                    Text(report.content, style = MaterialTheme.typography.bodySmall, color = muted, fontStyle = FontStyle.Italic)
                }
                for (link in report.objects.filter { it.startsWith("https://") }) {
                    Text(
                        link,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.clickable { uri.openUri(link) },
                    )
                }
                if (report.resolved > 0) {
                    Text(
                        outcomeLabel(report.outcome) + " · " + format.formatTimestamp(report.resolved),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                }
            }
            if (report.resolved == 0L) {
                Box {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        MochiIconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.reports_actions))
                        }
                    }
                    MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.reports_dismiss)) },
                            onClick = { menu = false; onDismiss() },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        )
                        for (action in reportActions(report)) {
                            MochiDropdownMenuItem(
                                text = {
                                    Text(
                                        when (action) {
                                            is ReportAction.Person -> stringResource(R.string.reports_block_person, action.name)
                                            is ReportAction.Server -> stringResource(R.string.reports_block_server, action.host)
                                            is ReportAction.Suspend -> stringResource(R.string.system_users_suspend)
                                        },
                                    )
                                },
                                onClick = { menu = false; onAction(action) },
                                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                            )
                        }
                    }
                }
            }
        }
    }
}
