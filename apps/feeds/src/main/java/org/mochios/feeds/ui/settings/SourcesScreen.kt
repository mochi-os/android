// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mochios.android.i18n.LocalFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.feeds.R
import org.mochios.android.R as MochiR

/**
 * Standalone Sources page from the feed's overflow menu; mirrors web's
 * `/$feedId_/sources` route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    onNavigateBack: () -> Unit,
    highlightSource: String? = null,
    viewModel: FeedSettingsViewModel = hiltViewModel(),
) {
    val feedInfo by viewModel.feedInfo.collectAsState()
    val error by viewModel.error.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val imported by viewModel.imported.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val format = LocalFormat.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // A Mastodon export of follows (following_accounts.csv), read here and
    // sent as text: each address becomes a source.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (!text.isNullOrBlank()) viewModel.importFollows(text)
            }
        }
    }

    LaunchedEffect(imported) {
        imported?.let { answer ->
            snackbarHostState.showSnackbar(
                if (answer.more) {
                    resources.getString(
                        R.string.feeds_follows_importing,
                        format.formatNumber(answer.total),
                        format.formatNumber(answer.imported),
                    )
                } else {
                    resources.getQuantityString(R.plurals.feeds_follows_imported, answer.imported, format.formatNumber(answer.imported))
                },
            )
            viewModel.clearImported()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSources()
    }

    LaunchedEffect(error) {
        error?.let { current ->
            snackbarHostState.showSnackbar(current.userMessage())
            viewModel.clearError()
        }
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { messageRes ->
            snackbarHostState.showSnackbar(resources.getString(messageRes))
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        feedInfo?.name?.let { name ->
                            stringResource(R.string.feeds_sources_title_named, name)
                        } ?: stringResource(R.string.feeds_sources_title),
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
                actions = {
                    MochiIconButton(
                        onClick = { importLauncher.launch(arrayOf("text/*", "application/csv")) },
                        enabled = !isImporting,
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Outlined.UploadFile,
                                contentDescription = stringResource(R.string.feeds_import_follows),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        SourcesTab(
            viewModel = viewModel,
            scrollToSourceUrl = highlightSource,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
