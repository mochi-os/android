// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feedlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.feeds.R
import org.mochios.android.R as MochiR

/**
 * Full-screen create form for a feed. "Allow anyone to search for feed" on
 * means the feed is public; memories keep the AI recap on. The top bar carries
 * the back button and Create sits in the bottom bar.
 *
 * @param onBack leaves the screen without creating anything.
 * @param onCreated hands the new feed's id to the caller so it can open it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFeedScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateFeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    // The toggle reads "Allow anyone to search for feed" — on means public.
    var allowSearch by remember { mutableStateOf(true) }
    var memoriesEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(uiState.createdFeedId) {
        uiState.createdFeedId?.let { newId ->
            onCreated(newId)
            viewModel.consumeCreatedFeed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeds_create_feed)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack, enabled = !uiState.isCreating) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    uiState.error?.let { error ->
                        Text(
                            text = error.userMessage(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    MochiButton(
                        onClick = {
                            val privacy = if (allowSearch) "public" else "private"
                            viewModel.createFeed(name, privacy, memoriesEnabled)
                        },
                        enabled = name.isNotBlank() && !uiState.isCreating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.feeds_create))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            MochiTextField(
                value = name,
                onValueChange = { value -> name = value },
                label = { Text(stringResource(R.string.feeds_feed_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.feeds_allow_search),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                )
                Switch(
                    checked = allowSearch,
                    onCheckedChange = { checked -> allowSearch = checked }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.feeds_enable_memories),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                )
                Switch(
                    checked = memoriesEnabled,
                    onCheckedChange = { checked -> memoriesEnabled = checked }
                )
            }
        }
    }
}
