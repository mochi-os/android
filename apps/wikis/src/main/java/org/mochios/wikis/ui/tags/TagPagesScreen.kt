package org.mochios.wikis.ui.tags

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.wikis.R
import org.mochios.wikis.model.TagPage
import org.mochios.wikis.navigation.WikisApp
import org.mochios.android.R as MochiR

/**
 * Pages-for-a-tag surface. Mirrors web's
 * `apps/wikis/web/src/features/wiki/tag-pages.tsx`: a header with the tag
 * name, a count subtitle, a LazyColumn of page rows showing title + last
 * updated, and an "All tags" action in the top bar that returns to the
 * tags index.
 *
 * Reads `wikiId` + `tag` via [TagPagesViewModel]'s
 * [androidx.lifecycle.SavedStateHandle] and is wired by `WikisApp.TAG_PAGES`
 * in the nav graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPagesScreen(
    navController: NavController,
    viewModel: TagPagesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // Subscribed to keep the top-app-bar in sync with nav changes.
    navController.currentBackStackEntryAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            R.string.wikis_tag_pages_title_template,
                            viewModel.tag,
                        ),
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
                actions = {
                    TextButton(onClick = {
                        navController.navigate(WikisApp.tags(viewModel.wikiId))
                    }) {
                        Icon(
                            Icons.Default.LocalOffer,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.wikis_tag_pages_all_tags_action))
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
                state.isLoading && state.pages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.pages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.error!!.userMessage(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                state.pages.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.LocalOffer,
                        title = stringResource(R.string.wikis_tag_pages_empty_title),
                        subtitle = stringResource(R.string.wikis_tag_pages_empty_description),
                    )
                }
                else -> {
                    TagPagesBody(
                        pages = state.pages,
                        onPageClick = { slug ->
                            navController.navigate(
                                WikisApp.pageView(viewModel.wikiId, slug)
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagPagesBody(
    pages: List<TagPage>,
    onPageClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = pluralStringResource(
                R.plurals.wikis_tag_pages_count,
                pages.size,
                pages.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(pages, key = { it.page }) { page ->
                TagPageRow(page = page, onClick = { onPageClick(page.page) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TagPageRow(
    page: TagPage,
    onClick: () -> Unit,
) {
    val format = LocalFormat.current
    val updatedLabel = format.formatTimestamp(page.updated)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = page.title.ifBlank { page.page },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.wikis_pageview_updated, updatedLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
