package org.mochios.wikis.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.FieldRow
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.Truncate
import org.mochios.wikis.R
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.navigation.WikisApp

/**
 * Settings tab body. Renders identity / subscription / home page / delete
 * sections matching web's `wiki-settings.tsx` SettingsTab. The wiki name,
 * fingerprint, and source come from [WikiSettingsViewModel] so the parent
 * screen and this tab agree on the displayed name. Per-wiki home / source
 * settings come from [SettingsTabViewModel].
 *
 * @param onWikiDeleted Invoked after a successful delete so the host screen
 *                      can navigate back to the wiki list. Snackbars are
 *                      delivered via the tab view model's flow.
 */
@Composable
fun SettingsTab(
    navController: NavController,
    parentViewModel: WikiSettingsViewModel,
    onWikiDeleted: () -> Unit,
    viewModel: SettingsTabViewModel = hiltViewModel(),
) {
    val parentState by parentViewModel.uiState.collectAsState()
    val state by viewModel.uiState.collectAsState()

    // Relay tab-local snackbars through the parent view model so the host
    // Scaffold's SnackbarHost picks them up.
    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            parentViewModel.emit(msg.messageRes, *msg.args.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deleted.collect { onWikiDeleted() }
    }

    if (state.isLoading && state.settings.home.isEmpty() && parentState.wiki == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Identity — only rendered when we know the wiki (subscribed wikis
        // viewed before info loads will skip this section briefly).
        val wikiInfo = parentState.wiki
        if (wikiInfo != null) {
            IdentitySection(
                wiki = wikiInfo,
                fingerprint = parentState.fingerprint,
                isRenaming = state.isRenaming,
                nameError = state.nameError,
                onRename = { newName ->
                    viewModel.rename(newName, wikiInfo.name) { saved ->
                        parentViewModel.setWikiName(saved)
                    }
                },
                onClearNameError = { viewModel.clearNameError() },
            )
        }

        // Subscription — only for replica wikis (those with a source set).
        val source = state.settings.source
        if (!source.isNullOrBlank()) {
            SubscriptionSection(
                source = source,
                isSyncing = state.isSyncing,
                onOpenSource = { navController.navigate(WikisApp.wikiHome(source)) },
                onSync = { viewModel.sync() },
            )
        }

        // Home page
        HomePageSection(
            currentHome = state.settings.home,
            isSaving = state.isSaving,
            onSave = { newHome ->
                viewModel.saveHome(newHome) { saved ->
                    parentViewModel.setHome(saved)
                }
            },
        )

        // Delete — only for owned wikis (not subscribed).
        if (source.isNullOrBlank() && wikiInfo != null) {
            DeleteSection(
                isDeleting = state.isDeleting,
                onDelete = { viewModel.delete() },
            )
        }
    }
}

@Composable
private fun IdentitySection(
    wiki: WikiInfo,
    fingerprint: String?,
    isRenaming: Boolean,
    nameError: NameValidationError?,
    onRename: (String) -> Unit,
    onClearNameError: () -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember(wiki.name) { mutableStateOf(wiki.name) }

    Section(title = stringResource(R.string.wikis_settings_section_identity)) {
        FieldRow(label = stringResource(R.string.wikis_settings_field_name)) {
            if (isEditing) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = editValue,
                            onValueChange = {
                                editValue = it
                                onClearNameError()
                            },
                            singleLine = true,
                            enabled = !isRenaming,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                onRename(editValue)
                                // Editor closes when rename succeeds — but
                                // also close immediately on no-op (same name).
                                if (editValue.trim() == wiki.name) {
                                    isEditing = false
                                }
                            },
                            enabled = !isRenaming,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.wikis_settings_name_save_cd),
                            )
                        }
                        IconButton(
                            onClick = {
                                editValue = wiki.name
                                isEditing = false
                                onClearNameError()
                            },
                            enabled = !isRenaming,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.wikis_settings_name_cancel_cd),
                            )
                        }
                    }
                    val errorRes = when (nameError) {
                        NameValidationError.REQUIRED -> R.string.wikis_settings_name_required
                        NameValidationError.TOO_LONG -> R.string.wikis_settings_name_too_long
                        NameValidationError.INVALID_CHAR -> R.string.wikis_settings_name_invalid_char
                        null -> null
                    }
                    if (errorRes != null) {
                        Text(
                            text = stringResource(errorRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(wiki.name)
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            editValue = wiki.name
                            isEditing = true
                        },
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.wikis_settings_name_edit_cd),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        FieldRow(label = stringResource(R.string.wikis_settings_field_entity_id)) {
            DataChip(value = wiki.id, truncate = Truncate.MIDDLE)
        }
        if (!fingerprint.isNullOrBlank()) {
            FieldRow(label = stringResource(R.string.wikis_settings_field_fingerprint)) {
                DataChip(value = fingerprint, truncate = Truncate.MIDDLE)
            }
        }
    }
}

@Composable
private fun SubscriptionSection(
    source: String,
    isSyncing: Boolean,
    onOpenSource: () -> Unit,
    onSync: () -> Unit,
) {
    Section(
        title = stringResource(R.string.wikis_settings_section_subscription),
        action = {
            OutlinedButton(
                onClick = onSync,
                enabled = !isSyncing,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.wikis_settings_action_sync))
            }
        },
    ) {
        FieldRow(label = stringResource(R.string.wikis_settings_field_source)) {
            TextButton(onClick = onOpenSource) {
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HomePageSection(
    currentHome: String,
    isSaving: Boolean,
    onSave: (String) -> Unit,
) {
    var value by remember(currentHome) { mutableStateOf(currentHome.ifEmpty { "home" }) }
    val hasChanges = value != currentHome.ifEmpty { "home" }

    Section(
        title = stringResource(R.string.wikis_settings_section_home),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = stringResource(R.string.wikis_settings_field_home_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { onSave(value) },
                    enabled = hasChanges && !isSaving,
                ) {
                    Text(stringResource(R.string.wikis_settings_home_save))
                }
            }
        }
    }
}

@Composable
private fun DeleteSection(
    isDeleting: Boolean,
    onDelete: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }

    Section(
        title = stringResource(R.string.wikis_settings_section_delete),
        description = stringResource(R.string.wikis_settings_delete_description),
        action = {
            OutlinedButton(
                onClick = { showConfirm = true },
                enabled = !isDeleting,
            ) {
                Text(stringResource(R.string.wikis_settings_delete_action))
            }
        },
        content = {},
    )

    if (showConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.wikis_settings_delete_confirm_title),
            message = stringResource(R.string.wikis_settings_delete_confirm_message),
            confirmLabel = stringResource(R.string.wikis_settings_delete_action),
            isDestructive = true,
            onConfirm = {
                showConfirm = false
                onDelete()
            },
            onDismiss = { showConfirm = false },
        )
    }
}
