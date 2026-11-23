// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.interests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.messageOrFallback
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiButtonTone
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.theme.interestColor
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.Interest
import org.mochios.settings.api.InterestSearchResult
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterestsScreen(
    onBack: () -> Unit,
    viewModel: InterestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // An error while content is on screen is shown over it rather than
    // replacing it: the full-screen arm below only fires when there is
    // nothing to show, and nothing on it can reach a refresh to clear it.
    LaunchedEffect(state.error) {
        val failure = state.error
        if (failure != null && state.interests.isNotEmpty()) {
            snackbar.showSnackbar(failure.userMessage())
            viewModel.clearError()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.interests_title)) },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.interests.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null && state.interests.isEmpty() -> ErrorState(
                    error = state.error!!,
                    onRetry = { viewModel.refresh() },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item("summary") {
                        SummaryCard(
                            summary = state.summary,
                            isRegenerating = state.isRegeneratingSummary,
                            onRegenerate = viewModel::regenerateSummary,
                        )
                    }
                    item("search") {
                        SearchSection(
                            query = state.searchQuery,
                            results = state.searchResults,
                            isSearching = state.isSearching,
                            searchError = state.searchError?.messageOrFallback(
                                stringResource(R.string.interests_search_failed),
                            ),
                            onQueryChange = viewModel::onSearchQueryChange,
                            onSelect = viewModel::add,
                        )
                    }
                    if (state.interests.isEmpty()) {
                        item("empty") {
                            Text(
                                text = stringResource(R.string.interests_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    } else {
                        items(state.interests, key = { it.qid }) { interest ->
                            InterestRow(
                                interest = interest,
                                onWeightChange = { w -> viewModel.setWeight(interest.qid, w) },
                                onRemove = { viewModel.remove(interest.qid) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: String,
    isRegenerating: Boolean,
    onRegenerate: () -> Unit,
) {
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.interests_summary_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                MochiIconButton(onClick = onRegenerate, enabled = !isRegenerating) {
                    if (isRegenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.interests_summary_regenerate),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    isRegenerating -> stringResource(R.string.interests_summary_regenerating)
                    summary.isBlank() -> stringResource(R.string.interests_summary_empty)
                    else -> summary
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchSection(
    query: String,
    results: List<InterestSearchResult>,
    isSearching: Boolean,
    searchError: String?,
    onQueryChange: (String) -> Unit,
    onSelect: (InterestSearchResult) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MochiTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.interests_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            },
            // Why the search failed: the server's reason, else the fixed line.
            isError = searchError != null,
            supportingText = searchError?.let { message -> { Text(message) } },
        )
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            MochiCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    results.forEach { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(result) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = result.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (result.description.isNotBlank()) {
                                Text(
                                    text = result.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The weight a slider position stands for. Rounded, not truncated: the
 * slider's float arithmetic can put a tick just short of its ten (29.999998).
 */
internal fun sliderWeight(value: Float): Int = value.roundToInt()

/**
 * One interest with its weight. The slider moves the row only while it is
 * dragged, and [onWeightChange] is called once, with the weight it was let go
 * at, as the web saves on release.
 */
@Composable
internal fun InterestRow(
    interest: Interest,
    onWeightChange: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var dragged by remember(interest.qid) { mutableStateOf<Int?>(null) }
    val weight = dragged ?: interest.weight
    val weightColor = interestColor(weight.toDouble())
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = interest.label.ifBlank { interest.qid },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (weight > 0) "+$weight" else "$weight",
                    style = MaterialTheme.typography.labelMedium,
                    color = weightColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
                MochiIconButton(onClick = onRemove, tone = MochiButtonTone.Destructive) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.interests_remove),
                    )
                }
            }
            Slider(
                value = weight.toFloat(),
                onValueChange = { dragged = sliderWeight(it) },
                onValueChangeFinished = {
                    dragged?.let(onWeightChange)
                    dragged = null
                },
                valueRange = -100f..100f,
                steps = 19, // 20 segments at step 10 between -100 and 100
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = weightColor,
                    activeTrackColor = weightColor,
                ),
            )
        }
    }
}
