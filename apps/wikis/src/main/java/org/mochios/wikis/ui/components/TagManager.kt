// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.wikis.R
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject
import org.mochios.android.R as MochiR

/**
 * Tag chips and an add chip for the page footer. Injects its own
 * [TagManagerViewModel] so the page screen need not thread the repository
 * through; feedback goes to the caller's snackbar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManager(
    wikiId: String,
    slug: String,
    tags: List<String>,
    canEdit: Boolean,
    onTagClick: (String) -> Unit,
    onTagsChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    viewModel: TagManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addDialogOpen by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<String?>(null) }

    // Bridge ViewModel snackbar events into the host's snackbar host.
    if (snackbarHostState != null) {
        LaunchedEffect(snackbarHostState) {
            viewModel.snackbar.collect { msg ->
                val text = context.getString(msg.messageRes, *msg.args.toTypedArray())
                scope.launch { snackbarHostState.showSnackbar(text) }
            }
        }
    }

    // Keep the screen's tags list in sync with the ViewModel's latest
    // mutation outcome so the chip row updates immediately after add /
    // remove operations complete.
    LaunchedEffect(state.lastUpdated) {
        val latest = state.lastUpdated ?: return@LaunchedEffect
        onTagsChanged(latest)
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            AssistChip(
                onClick = { onTagClick(tag) },
                label = { Text(tag) },
                leadingIcon = {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                trailingIcon = if (canEdit) {
                    {
                        androidx.compose.material3.IconButton(
                            onClick = { pendingRemoval = tag },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.wikis_tag_manager_remove_confirm_title
                                ),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                } else null,
            )
        }

        if (canEdit) {
            AssistChip(
                onClick = { addDialogOpen = true },
                label = { Text(stringResource(R.string.wikis_tag_manager_add_action)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }

    if (addDialogOpen) {
        AddTagDialog(
            open = true,
            existingTags = tags,
            onDismiss = { addDialogOpen = false },
            onConfirm = { newTag ->
                viewModel.add(wikiId, slug, tags, newTag)
                addDialogOpen = false
            },
        )
    }

    val toRemove = pendingRemoval
    if (toRemove != null) {
        ConfirmDialog(
            title = stringResource(R.string.wikis_tag_manager_remove_confirm_title),
            message = stringResource(
                R.string.wikis_tag_manager_remove_confirm_message,
                toRemove,
            ),
            confirmLabel = stringResource(MochiR.string.common_delete),
            isDestructive = true,
            onConfirm = {
                viewModel.remove(wikiId, slug, tags, toRemove)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

/**
 * Add-tag dialog. Tags are trimmed and lowercased; blanks and duplicates are
 * refused inline.
 */
@Composable
fun AddTagDialog(
    open: Boolean,
    existingTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!open) return

    var newTag by remember { mutableStateOf("") }
    var inlineError by remember { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val submit: () -> Unit = {
        val cleaned = newTag.trim().lowercase()
        when {
            cleaned.isEmpty() -> {
                inlineError = R.string.wikis_tag_dialog_empty
            }
            existingTags.any { it.equals(cleaned, ignoreCase = true) } -> {
                inlineError = R.string.wikis_tag_dialog_duplicate
            }
            else -> {
                inlineError = null
                onConfirm(cleaned)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wikis_tag_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = {
                        newTag = it
                        inlineError = null
                    },
                    label = { Text(stringResource(R.string.wikis_tag_dialog_label)) },
                    singleLine = true,
                    isError = inlineError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                val errorRes = inlineError
                if (errorRes != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = submit,
                enabled = newTag.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.wikis_tag_dialog_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.wikis_tag_dialog_cancel))
            }
        },
    )
}

data class TagManagerSnackbar(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
)

data class TagManagerUiState(
    val lastUpdated: List<String>? = null,
)

@HiltViewModel
class TagManagerViewModel @Inject constructor(
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagManagerUiState())
    val uiState: StateFlow<TagManagerUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<TagManagerSnackbar>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<TagManagerSnackbar> = _snackbar.asSharedFlow()

    fun add(wikiId: String, slug: String, currentTags: List<String>, tag: String) {
        viewModelScope.launch {
            try {
                repository.addTag(wikiId, slug, tag)
                val newList = (currentTags + tag).distinct()
                _uiState.value = TagManagerUiState(lastUpdated = newList)
                _snackbar.emit(
                    TagManagerSnackbar(
                        R.string.wikis_tag_manager_added,
                        listOf(tag),
                    )
                )
            } catch (_: Exception) {
                _snackbar.emit(TagManagerSnackbar(R.string.wikis_tag_manager_add_failed))
            }
        }
    }

    fun remove(wikiId: String, slug: String, currentTags: List<String>, tag: String) {
        viewModelScope.launch {
            try {
                repository.removeTag(wikiId, slug, tag)
                val newList = currentTags.filterNot { it.equals(tag, ignoreCase = true) }
                _uiState.value = TagManagerUiState(lastUpdated = newList)
                _snackbar.emit(
                    TagManagerSnackbar(
                        R.string.wikis_tag_manager_removed,
                        listOf(tag),
                    )
                )
            } catch (_: Exception) {
                _snackbar.emit(TagManagerSnackbar(R.string.wikis_tag_manager_remove_failed))
            }
        }
    }
}
