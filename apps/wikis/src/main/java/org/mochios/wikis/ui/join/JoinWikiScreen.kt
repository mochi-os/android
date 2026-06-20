// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.wikis.R
import org.mochios.android.R as MochiR

/**
 * "Replicate wiki" form. Mirrors web's `JoinWikiPage`
 * (`apps/wikis/web/src/routes/_authenticated/join.tsx`):
 *
 *  - A "Search for wikis" outline button that routes to [onSearch] (the
 *    host wires this to `WikisApp.FIND`).
 *  - A divider with "Or enter ID directly".
 *  - A text field for the wiki entity ID + a "Replicate wiki" button.
 *
 * On submit, the ViewModel calls `joinWiki(target, server=null)`. On
 * success [onJoined] is invoked with the new wiki's landing id (fingerprint
 * if present, else id) so the host can navigate to its home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinWikiScreen(
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onJoined: (wikiId: String) -> Unit,
    viewModel: JoinWikiViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val successMsg = stringResource(R.string.wikis_subscribe_success)
    val failedFallback = stringResource(R.string.wikis_subscribe_failed)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is JoinEvent.Success -> {
                    snackbarHostState.showSnackbar(successMsg)
                    onJoined(event.wikiId)
                }
                is JoinEvent.Failed -> {
                    val msg = event.error.userMessage().ifBlank { failedFallback }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wikis_join_title)) },
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
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.wikis_join_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    // "Search for wikis" navigates to FindWikisScreen.
                    OutlinedButton(
                        onClick = onSearch,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.wikis_join_search_button))
                    }

                    // Divider with "Or enter ID directly"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.wikis_join_or_id),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    // Entity-id form
                    OutlinedTextField(
                        value = uiState.target,
                        onValueChange = viewModel::updateTarget,
                        label = { Text(stringResource(R.string.wikis_join_id_label)) },
                        placeholder = { Text(stringResource(R.string.wikis_join_id_hint)) },
                        singleLine = true,
                        enabled = !uiState.isSubmitting,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (uiState.error != null) {
                        Text(
                            text = uiState.error!!.userMessage(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = viewModel::submit,
                            enabled = uiState.target.trim().isNotEmpty() && !uiState.isSubmitting,
                        ) {
                            if (uiState.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.wikis_join_submit))
                            }
                        }
                    }
                }
            }
        }
    }
}
