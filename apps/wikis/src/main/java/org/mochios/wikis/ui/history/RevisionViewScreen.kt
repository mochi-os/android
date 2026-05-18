package org.mochios.wikis.ui.history

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.wikis.R
import org.mochios.wikis.model.RevisionDetail
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.MarkdownContent
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.android.R as MochiR

/**
 * Single-revision viewer. Mirrors web's
 * `apps/wikis/web/src/features/wiki/revision-view.tsx`: a header card with
 * version badges, "Back to history" / "Revert to this version" buttons, a
 * meta row of timestamp + author fingerprint snippet, optional comment,
 * and the rendered markdown body of the revision.
 *
 * Reads `wikiId`, `page`, `version` via the ViewModel's [SavedStateHandle]
 * and is wired by `WikisApp.PAGE_REVISION` in the nav graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionViewScreen(
    navController: NavController,
    viewModel: RevisionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val wikiInfo = state.wiki
    val revision = state.revision

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = revision?.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.wikis_history_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && revision == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && revision == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.error!!.userMessage(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                revision == null || wikiInfo == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    val wikiCtx = WikiContextValue(
                        wikiId = viewModel.wikiId,
                        info = wikiInfo,
                        permissions = state.permissions,
                        serverUrl = viewModel.serverUrl,
                    )
                    CompositionLocalProvider(LocalWikiContext provides wikiCtx) {
                        RevisionBody(
                            slug = viewModel.slug,
                            revision = revision,
                            currentVersion = state.currentVersion,
                            onBackToHistory = {
                                navController.navigate(
                                    WikisApp.pageHistory(viewModel.wikiId, viewModel.slug)
                                ) {
                                    popUpTo(
                                        WikisApp.pageHistory(viewModel.wikiId, viewModel.slug),
                                    ) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            onRevert = {
                                navController.navigate(
                                    WikisApp.pageRevert(viewModel.wikiId, viewModel.slug, revision.version)
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RevisionBody(
    slug: String,
    revision: RevisionDetail,
    currentVersion: Int,
    onBackToHistory: () -> Unit,
    onRevert: () -> Unit,
) {
    val format = LocalFormat.current
    val createdLabel = format.formatTimestamp(revision.created)
    val isCurrent = revision.version == currentVersion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Header card with badges + title + action buttons
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                // Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VersionBadge(
                        text = stringResource(R.string.wikis_revision_version_badge, revision.version),
                        primary = true,
                    )
                    if (isCurrent) {
                        VersionBadge(
                            text = stringResource(R.string.wikis_revision_current_badge),
                            primary = false,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Title
                Text(
                    text = revision.title.ifBlank { slug },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(12.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onBackToHistory) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.wikis_revision_back_to_history))
                    }
                    if (!isCurrent) {
                        OutlinedButton(onClick = onRevert) {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.wikis_revision_revert_to_this))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Meta row: created timestamp + author fingerprint snippet
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = createdLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val fp = revision.author
                    val shown = if (fp.length > 16) "${fp.substring(0, 16)}…" else fp
                    Text(
                        text = "by $shown",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (revision.comment.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "\"${revision.comment}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Markdown body
        MarkdownContent(
            content = revision.content,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VersionBadge(
    text: String,
    primary: Boolean,
) {
    val bg = if (primary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val fg = if (primary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
