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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.wikis.R
import org.mochios.wikis.model.Redirect
import org.mochios.android.R as MochiR

/**
 * Standalone redirects management surface. Mirrors web's
 * `apps/wikis/web/src/features/wiki/redirects-page.tsx`: a list of
 * source → target rows with delete affordances and an "Add" trailing button
 * in the top bar that opens a create dialog.
 *
 * Reads `wikiId` via the ViewModel's [SavedStateHandle] and is wired by
 * `WikisApp.REDIRECTS` in the nav graph. The visible list + add dialog are
 * factored into [RedirectsBody] so the wiki settings screen's Redirects tab
 * can reuse the same UI without re-implementing it.
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
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showAddDialog = true }) {
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
 * The visible redirects list (or empty state / loading spinner) plus the
 * "Add redirect" dialog and the "Delete redirect?" confirmation. Reused
 * by the standalone [RedirectsScreen] and the wiki-settings Redirects tab.
 *
 * The screen owns the TopAppBar / Scaffold; this composable just renders
 * the body inside whatever surface the caller provides.
 *
 * @param showAddDialog          Whether the Add dialog is currently open.
 * @param onShowAddDialogChange  Toggle the Add dialog visibility. The
 *                               settings-tab caller uses this to drive a
 *                               dialog opened by an inline "Add" button;
 *                               the standalone screen drives it from the
 *                               top-bar action.
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
        ConfirmDialog(
            title = stringResource(R.string.wikis_redirect_delete_confirm_title),
            message = stringResource(
                R.string.wikis_redirect_delete_confirm_message,
                toDelete.source,
                toDelete.target,
            ),
            confirmLabel = stringResource(MochiR.string.common_delete),
            isDestructive = true,
            onConfirm = {
                viewModel.delete(toDelete.source)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun RedirectRow(
    redirect: Redirect,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ValueChip(
            value = redirect.source,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        ValueChip(
            value = redirect.target,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDelete) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wikis_redirect_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text(stringResource(R.string.wikis_redirect_source_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.wikis_redirect_target_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(source, target) },
                enabled = source.isNotBlank() && target.isNotBlank(),
            ) {
                Text(stringResource(R.string.wikis_redirect_create_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
    )
}
