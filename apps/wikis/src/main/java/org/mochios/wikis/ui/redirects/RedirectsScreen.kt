// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.redirects

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.mochios.android.R as MochiR
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.wikis.R
import org.mochios.wikis.model.Redirect

/**
 * Standalone redirects screen. The list and its dialogs live in
 * [RedirectsBody], shared with the wiki-settings Redirects tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedirectsScreen(
    navController: NavController,
    viewModel: RedirectsViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            val text = context.getString(msg.messageRes, *msg.args.toTypedArray())
            scope.launch { snackbarHostState.showSnackbar(text) }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wikis_redirects_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
                actions = {
                    MochiTextButton(onClick = { showAddDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.wikis_redirects_add))
                    }
                },
            )
        },
    ) { padding ->
        RedirectsBody(
            viewModel = viewModel,
            showAddDialog = showAddDialog,
            onShowAddDialogChange = { showAddDialog = it },
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * Redirects list, add dialog and delete confirmation, with no surrounding
 * chrome - the caller supplies the Scaffold. Shared by [RedirectsScreen] and
 * the settings tab.
 */
@Composable
fun RedirectsBody(
    viewModel: RedirectsViewModel,
    showAddDialog: Boolean,
    onShowAddDialogChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<Redirect?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        when {
            state.isLoading && state.redirects.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.redirects.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.wikis_redirects_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    RedirectsHeader()
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(state.redirects, key = { it.source }) { redirect ->
                            RedirectRow(
                                redirect = redirect,
                                onDelete = { pendingDelete = redirect },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRedirectDialog(
            onDismiss = { onShowAddDialogChange(false) },
            onCreate = { source, target ->
                viewModel.create(source, target)
                onShowAddDialogChange(false)
            },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        MochiAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.wikis_redirect_delete_confirm_title),
            text = stringResource(
                R.string.wikis_redirect_delete_confirm_message,
                toDelete.source,
                toDelete.target,
            ),
            confirmText = stringResource(MochiR.string.common_delete),
            onConfirm = {
                viewModel.delete(toDelete.source)
                pendingDelete = null
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

// The table's columns, shared by the heading and every row so that a heading
// stands over what it names. The arrow and the delete button head nothing, as
// they do not on the web.
private const val SOURCE_WEIGHT = 1f
private const val TARGET_WEIGHT = 1f
private const val CREATED_WEIGHT = 0.9f
private val ARROW_COLUMN = 32.dp
private val ACTION_COLUMN = 48.dp

/** The table heading: Source, Target, Created, as the web lists them. */
@Composable
private fun RedirectsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderLabel(
            text = stringResource(R.string.wikis_redirect_source_label),
            modifier = Modifier.weight(SOURCE_WEIGHT),
        )
        Spacer(Modifier.width(ARROW_COLUMN))
        HeaderLabel(
            text = stringResource(R.string.wikis_redirect_target_label),
            modifier = Modifier.weight(TARGET_WEIGHT),
        )
        HeaderLabel(
            text = stringResource(R.string.wikis_redirect_created_label),
            modifier = Modifier.weight(CREATED_WEIGHT),
        )
        Spacer(Modifier.width(ACTION_COLUMN))
    }
}

@Composable
private fun HeaderLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun RedirectRow(
    redirect: Redirect,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(SOURCE_WEIGHT)) {
            ValueChip(value = redirect.source)
        }
        Box(
            modifier = Modifier.width(ARROW_COLUMN),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Box(modifier = Modifier.weight(TARGET_WEIGHT)) {
            ValueChip(value = redirect.target)
        }
        Text(
            // Empty for a redirect the server sent no timestamp for, which
            // leaves the column standing rather than shifting the row.
            text = if (redirect.created > 0) {
                LocalFormat.current.formatRelativeTime(redirect.created)
            } else {
                ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(CREATED_WEIGHT),
        )
        MochiIconButton(
            onClick = onDelete,
            modifier = Modifier.width(ACTION_COLUMN),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(MochiR.string.common_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValueChip(
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AddRedirectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var source by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.wikis_redirect_create_title),
        content = {
            Column {
                MochiTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text(stringResource(R.string.wikis_redirect_source_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                MochiTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.wikis_redirect_target_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmText = stringResource(R.string.wikis_redirect_create_action),
        onConfirm = { onCreate(source, target) },
        confirmEnabled = source.isNotBlank() && target.isNotBlank(),
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}
