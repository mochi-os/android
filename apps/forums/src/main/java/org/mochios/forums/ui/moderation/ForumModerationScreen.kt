// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusTone
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

/**
 * The card every moderation entry sits in: bordered, with no fill, so a list
 * reads as entries on the page rather than a stack of filled blocks.
 */
@Composable
private fun ModerationCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        content = content,
    )
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
                    stringResource(
                        R.string.forums_moderation_pending_posts,
                        queue.counts.posts,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(queue.posts, key = { "p:${it.id}" }) { post ->
                ModerationCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(post.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(post.body.take(200),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        QueueByline(
                            name = post.name,
                            member = post.member,
                            created = post.created,
                            avatarUrl = "/forums/${viewModel.forumId}/-/${post.id}/asset/avatar",
                        )
                        Spacer(Modifier.height(8.dp))
                        QueueActions(
                            authorName = post.name,
                            onApprove = { viewModel.approvePost(post.id) },
                            onReject = { viewModel.removePost(post.id) },
                            onMute = { viewModel.addRestriction(post.member, "muted", "") },
                            onBan = { viewModel.addRestriction(post.member, "banned", "") },
                        )
                    }
                }
            }
        }
        if (queue.comments.isNotEmpty()) {
            item {
                Text(
                    stringResource(
                        R.string.forums_moderation_pending_comments,
                        queue.counts.comments,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(queue.comments, key = { "c:${it.id}" }) { c ->
                ModerationCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(c.body, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        QueueByline(
                            name = c.name,
                            member = c.member,
                            created = c.created,
                            // Comment avatars hang off the comment, not the post.
                            avatarUrl =
                                "/forums/${viewModel.forumId}/-/${c.post}/${c.id}/asset/avatar",
                        )
                        Spacer(Modifier.height(8.dp))
                        QueueActions(
                            authorName = c.name,
                            onApprove = { viewModel.approveComment(c.post, c.id) },
                            onReject = { viewModel.removeComment(c.post, c.id) },
                            onMute = { viewModel.addRestriction(c.member, "muted", "") },
                            onBan = { viewModel.addRestriction(c.member, "banned", "") },
                        )
                    }
                }
            }
        }
    }
}

/** Who wrote the queued item and when, read before deciding what to do with it. */
@Composable
private fun QueueByline(
    name: String,
    member: String,
    created: Long,
    avatarUrl: String,
) {
    val format = LocalFormat.current
    val authorName = name.ifBlank { stringResource(R.string.forums_post_default_author) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        EntityAvatar(
            name = authorName,
            src = avatarUrl,
            seed = member.ifEmpty { authorName },
            size = 20.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = authorName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = format.formatRelativeTime(created),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * What a moderator can do with a queued item: reject it, let it through, or act
 * on whoever wrote it. All outlined, colour doing the work: Approve primary, the
 * rest neutral. The row wraps so four actions still fit a narrow screen.
 *
 * Muting and banning confirm first: they act on a person rather than a post, and
 * a mis-tap costs someone their access until a moderator undoes it by hand.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueActions(
    authorName: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onMute: () -> Unit,
    onBan: () -> Unit,
) {
    var confirming by remember { mutableStateOf<RestrictAction?>(null) }
    val name = authorName.ifBlank { stringResource(R.string.forums_post_default_author) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onReject,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(stringResource(R.string.forums_moderation_reject))
        }
        // Outlined with a primary tint, the way General settings tints Delete
        // error: same shape as its siblings, colour carrying the meaning.
        OutlinedButton(
            onClick = onApprove,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.forums_post_approve))
        }
        OutlinedButton(
            onClick = { confirming = RestrictAction.MUTE },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(stringResource(R.string.forums_moderation_restriction_type_muted))
        }
        OutlinedButton(
            onClick = { confirming = RestrictAction.BAN },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(stringResource(R.string.forums_moderation_restriction_type_banned))
        }
    }

    confirming?.let { action ->
        val mute = action == RestrictAction.MUTE
        ConfirmDialog(
            title = stringResource(
                if (mute) R.string.forums_moderation_mute_title
                else R.string.forums_moderation_ban_title,
                name,
            ),
            message = stringResource(
                if (mute) R.string.forums_moderation_mute_message
                else R.string.forums_moderation_ban_message
            ),
            confirmLabel = stringResource(
                if (mute) R.string.forums_moderation_restriction_type_muted
                else R.string.forums_moderation_restriction_type_banned
            ),
            dismissLabel = stringResource(MochiR.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                confirming = null
                if (mute) onMute() else onBan()
            },
            onDismiss = { confirming = null },
        )
    }
}

/** The two queue actions that restrict a person rather than a post. */
private enum class RestrictAction { MUTE, BAN }

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
                ReportCard(
                    report = r,
                    forumId = viewModel.forumId,
                    // Under Pending or Resolved the filter has already said what
                    // every row is; only All mixes them.
                    showStatus = uiState.reportsStatus == "all",
                    onResolve = { resolution -> viewModel.resolveReport(r.id, resolution) },
                )
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
private fun ReportCard(
    report: ModerationReport,
    forumId: String,
    showStatus: Boolean,
    onResolve: (String) -> Unit,
) {
    val format = LocalFormat.current
    ModerationCard {
        Column(modifier = Modifier.padding(12.dp)) {
            // Status leads when it is worth saying: whether this report still
            // needs a decision is the first thing a moderator reads. Type and
            // author follow it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showStatus) {
                    ReportStatusBadge(report.status)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = report.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (report.authorName.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.forums_moderation_report_by,
                            report.authorName,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
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
            // The reported content's own images, shown the way a comment shows
            // them — a report about a picture is unreadable without it.
            if (report.attachments.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                AttachmentGallery(
                    attachments = report.attachments,
                    urlBuilder = { att ->
                        att.url ?: "/forums/$forumId/-/attachments/${att.id}"
                    },
                    thumbnailUrlBuilder = { att ->
                        att.thumbnailUrl ?: "/forums/$forumId/-/attachments/${att.id}/thumbnail"
                    },
                    compact = true,
                )
            }
            if (report.details.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.forums_moderation_report_details, report.details),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (report.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.forums_moderation_report_reason, report.reason),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                // The author is already named in the header, so this line only
                // carries who raised the report.
                stringResource(
                    R.string.forums_moderation_report_reported_by,
                    report.reporterName,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.status == "pending") {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onResolve("ignored") },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(stringResource(R.string.forums_moderation_report_dismiss))
                    }
                    OutlinedButton(
                        onClick = { onResolve("removed") },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(stringResource(R.string.forums_post_remove))
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.forums_moderation_report_resolved_by,
                        report.resolverName.ifBlank { "\u2014" },
                        report.resolution.ifBlank { "\u2014" },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A report's own status: still waiting on a moderator, or already dealt with. */
@Composable
private fun ReportStatusBadge(status: String) {
    val tone = if (status == "pending") StatusTone.Waiting else StatusTone.Positive
    val label = stringResource(
        if (status == "pending") R.string.forums_moderation_reports_pending
        else R.string.forums_moderation_reports_resolved
    )
    StatusBadge(label = label, tone = tone)
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
            ModerationCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Action and type share one weighted row so the slack is
                        // theirs, not the timestamp's: a weighted Spacer beside a
                        // `fill = false` text splits the free space between them
                        // and strands the remainder past the time, which then
                        // drifts off the edge as the type gets shorter.
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry.action,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = entry.type,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            format.formatRelativeTime(entry.created),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
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
                    RestrictionCard(
                        restriction = r,
                        onRemove = { viewModel.removeRestriction(r.user) },
                    )
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

/**
 * One restricted user: avatar, who they are, and what they are under — with the
 * moderator who set it and how long it lasts underneath. Remove lifts it, behind
 * a confirm: it hands someone their access back, and the queue's Mute and Ban
 * ask before taking it away.
 */
@Composable
private fun RestrictionCard(
    restriction: Restriction,
    onRemove: () -> Unit,
) {
    val format = LocalFormat.current
    var confirming by remember { mutableStateOf(false) }
    // The server sends a resolved name when it has one; the raw entity id is the
    // fallback, and what the design shows for a user with no display name.
    val title = restriction.name.ifBlank { restriction.user }
    ModerationCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityAvatar(
                    name = title,
                    // The restricted user's own avatar, by entity id — there is
                    // no post or comment here to hang a forum-scoped asset off.
                    // Falls back to seeded initials when they have none.
                    src = "/people/${restriction.user}/-/avatar",
                    seed = restriction.user,
                    size = 36.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        RestrictionBadge(restriction.type)
                    }
                    Spacer(Modifier.height(2.dp))
                    val lasts = restriction.expires?.let { expires ->
                        stringResource(
                            R.string.forums_moderation_restriction_expires,
                            format.formatRelativeTime(expires),
                        )
                    } ?: stringResource(R.string.forums_moderation_restriction_permanent)
                    val by = stringResource(
                        R.string.forums_moderation_restriction_by,
                        restriction.moderatorName.ifBlank { restriction.moderator },
                    )
                    Text(
                        text = "$by \u00B7 $lasts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { confirming = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(stringResource(R.string.forums_moderation_restriction_remove))
                }
            }
            if (restriction.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = restriction.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirming) {
        ConfirmDialog(
            title = stringResource(
                R.string.forums_moderation_restriction_remove_title,
                title,
            ),
            message = stringResource(R.string.forums_moderation_restriction_remove_message),
            confirmLabel = stringResource(R.string.forums_moderation_restriction_remove),
            dismissLabel = stringResource(MochiR.string.common_cancel),
            onConfirm = {
                confirming = false
                onRemove()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** What the user is under, as a badge: muted, banned, or shadowbanned. */
@Composable
private fun RestrictionBadge(type: String) {
    val tone = when (type) {
        "banned" -> StatusTone.Negative
        "muted" -> StatusTone.Waiting
        else -> StatusTone.Neutral
    }
    val label = when (type) {
        "banned" -> stringResource(R.string.forums_moderation_restriction_state_banned)
        "muted" -> stringResource(R.string.forums_moderation_restriction_state_muted)
        "shadowban" -> stringResource(R.string.forums_moderation_restriction_state_shadowban)
        else -> type
    }
    StatusBadge(label = label, tone = tone)
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
