// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.model.EntitySummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityDiscoveryScreen(
    recommendations: List<EntitySummary>,
    onSearch: suspend (query: String) -> List<EntitySummary>,
    onSubscribe: suspend (id: String, server: String?) -> Unit,
    onNavigate: (id: String) -> Unit,
    entityLabel: String,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<EntitySummary>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var subscribingIds by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { newQuery ->
                        query = newQuery
                        searchJob?.cancel()
                        if (newQuery.length >= 2) {
                            searchJob = scope.launch {
                                delay(300)
                                isSearching = true
                                try {
                                    searchResults = onSearch(newQuery)
                                } finally {
                                    isSearching = false
                                }
                            }
                        } else {
                            searchResults = null
                        }
                    },
                    onSearch = { searchQuery ->
                        if (searchQuery.isNotBlank()) {
                            scope.launch {
                                isSearching = true
                                try {
                                    searchResults = onSearch(searchQuery)
                                } finally {
                                    isSearching = false
                                }
                            }
                        }
                    },
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.discovery_search_placeholder, entityLabel)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {}

        Spacer(modifier = Modifier.height(16.dp))

        val displayItems = searchResults ?: recommendations
        val sectionTitle = if (searchResults != null) {
            stringResource(R.string.discovery_results)
        } else {
            stringResource(R.string.discovery_recommended)
        }

        if (isSearching) {
            LoadingState(modifier = Modifier.fillMaxSize())
        } else if (displayItems.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Search,
                title = if (searchResults != null) {
                    stringResource(R.string.discovery_no_results, entityLabel)
                } else {
                    stringResource(R.string.discovery_no_recommendations)
                },
                subtitle = stringResource(R.string.discovery_search_hint, entityLabel)
            )
        } else {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayItems, key = { it.id }) { entity ->
                    EntityCard(
                        entity = entity,
                        isSubscribing = subscribingIds.contains(entity.id),
                        onSubscribe = {
                            scope.launch {
                                subscribingIds = subscribingIds + entity.id
                                try {
                                    onSubscribe(entity.id, entity.server)
                                } finally {
                                    subscribingIds = subscribingIds - entity.id
                                }
                            }
                        },
                        onNavigate = { onNavigate(entity.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntityCard(
    entity: EntitySummary,
    isSubscribing: Boolean,
    onSubscribe: () -> Unit,
    onNavigate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onNavigate
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (entity.description.isNotBlank()) {
                    Text(
                        text = entity.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (entity.subscribers > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.discovery_subscriber_count,
                            entity.subscribers,
                            entity.subscribers
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (entity.isSubscribed) {
                OutlinedButton(onClick = {}, enabled = false) {
                    Text(stringResource(R.string.discovery_subscribed))
                }
            } else if (isSubscribing) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp).width(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(onClick = onSubscribe) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.common_subscribe))
                }
            }
        }
    }
}
