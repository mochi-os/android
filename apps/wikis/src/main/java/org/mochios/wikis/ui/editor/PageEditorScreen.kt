// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.editor

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MarkdownPreviewSheet
import org.mochios.android.ui.components.MarkdownToolbar
import org.mochios.android.ui.components.MarkdownToolbarSeparator
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.wikis.R
import org.mochios.wikis.navigation.WikisApp
import androidx.compose.runtime.CompositionLocalProvider
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.wikis.ui.components.MarkdownContent

/**
 * Powers both "Edit page" and "Create page"; [PageEditorViewModel]'s `isNew`
 * flag drives the differences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    navController: NavController,
    viewModel: PageEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // This screen is a wiki route in its own right, reached straight from the
    // nav graph, so it provides the context rather than expecting an ancestor
    // to have done it - without which the preview's markdown cannot resolve an
    // attachment and raises.
    val wikiCtx = remember(viewModel.wikiId, viewModel.serverUrl, state.wiki, state.permissions) {
        WikiContextValue(
            wikiId = viewModel.wikiId,
            info = state.wiki ?: WikiInfo(id = viewModel.wikiId),
            permissions = state.permissions,
            serverUrl = viewModel.serverUrl,
        )
    }

    // Body field uses TextFieldValue so we can capture the cursor position
    // and splice inserted markdown at the right spot from the dialog.
    var bodyField by remember(state.content.length == 0 && !state.isLoading) {
        mutableStateOf(TextFieldValue(state.content))
    }
    // Keep TextFieldValue in sync when the ViewModel mutates content (e.g.
    // from the insert dialog or the initial page load).
    LaunchedEffect(state.content) {
        if (state.content != bodyField.text) {
            bodyField = bodyField.copy(text = state.content)
        }
    }
    var savedCursor by remember { mutableStateOf(0) }
    var insertDialogOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val canDeletePage = !viewModel.isNew && state.permissions.delete

    // Pre-resolve i18n strings so the ViewModel can build localised toasts
    // for error fallbacks without dipping into Android resources directly.
    val titleRequiredMsg = stringResource(R.string.wikis_editor_title_required)
    val slugRequiredMsg = stringResource(R.string.wikis_editor_slug_required)
    val createFailedMsg = stringResource(R.string.wikis_editor_create_failed)
    val editFailedMsg = stringResource(R.string.wikis_editor_save_failed)
    val deleteFailedMsg = stringResource(R.string.wikis_delete_page_failed)
    val createdMsg = stringResource(R.string.wikis_editor_created)
    val savedMsg = stringResource(R.string.wikis_editor_saved)
    val deletedMsg = stringResource(R.string.wikis_delete_page_success)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PageEditorEvent.Saved -> {
                    Toast.makeText(
                        context,
                        if (viewModel.isNew) createdMsg else savedMsg,
                        Toast.LENGTH_SHORT,
                    ).show()
                    navController.navigate(WikisApp.pageView(viewModel.wikiId, event.slug)) {
                        if (viewModel.isNew) {
                            // The page exists now, so there is no creation form
                            // to go back to - drop it, and let Back reach
                            // whatever opened the editor. Editing an existing
                            // page keeps its own history untouched.
                            popUpTo(WikisApp.NEW_PAGE) { inclusive = true }
                        } else {
                            popUpTo(WikisApp.wikiHome(viewModel.wikiId)) { inclusive = false }
                        }
                    }
                }
                PageEditorEvent.Deleted -> {
                    Toast.makeText(context, deletedMsg, Toast.LENGTH_SHORT).show()
                    navController.navigate(WikisApp.wikiHome(viewModel.wikiId)) {
                        popUpTo(WikisApp.wikiHome(viewModel.wikiId)) { inclusive = true }
                    }
                }
                is PageEditorEvent.Toast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // Editing shows the page's own title: the bar already
                        // sits above an editor, so saying so again spends the
                        // width that the title itself needs.
                        text = if (viewModel.isNew) {
                            stringResource(R.string.wikis_editor_title_new)
                        } else {
                            state.originalTitle.ifEmpty { state.slug }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(org.mochios.android.R.string.common_back),
                        )
                    }
                },
                actions = {
                    // Preview is a mode the writer flicks in and out of, so it
                    // stays one tap away and the icon says which mode is on.
                    MochiIconButton(onClick = { viewModel.togglePreview() }) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.wikis_editor_preview),
                        )
                    }
                    if (canDeletePage) {
                        Box {
                            MochiIconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(
                                        org.mochios.android.R.string.common_more_options
                                    ),
                                )
                            }
                            MochiDropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(R.string.wikis_editor_delete)) },
                                    onClick = {
                                        menuOpen = false
                                        val slug = state.slug.ifEmpty { return@MochiDropdownMenuItem }
                                        navController.navigate(
                                            WikisApp.pageDelete(viewModel.wikiId, slug)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Delete, contentDescription = null)
                                    },
                                    enabled = !state.isDeleting,
                                    destructive = true,
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            // The primary action sits under the thumb rather than at the end of
            // a row the user has to scroll sideways to reach - the same shape
            // the create-wiki form uses.
            Surface(color = MaterialTheme.colorScheme.surface) {
                MochiButton(
                    onClick = {
                        viewModel.save(
                            invalidTitle = titleRequiredMsg,
                            invalidSlug = slugRequiredMsg,
                            createFailed = createFailedMsg,
                            editFailed = editFailedMsg,
                        )
                    },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            when {
                                state.isSaving && viewModel.isNew ->
                                    R.string.wikis_editor_creating
                                state.isSaving -> R.string.wikis_editor_saving
                                viewModel.isNew -> R.string.wikis_editor_create
                                else -> R.string.wikis_editor_save
                            }
                        )
                    )
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalWikiContext provides wikiCtx) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                    state.error != null -> {
                        Text(
                            text = state.error!!.userMessage(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        MochiOutlinedButton(onClick = { viewModel.retry() }) {
                            Text(stringResource(org.mochios.android.R.string.common_retry))
                        }
                    }
                    else -> {
                        EditFields(
                            isNew = viewModel.isNew,
                            title = state.title,
                            slug = state.slug,
                            comment = state.comment,
                            bodyField = bodyField,
                            onTitleChange = viewModel::setTitle,
                            onSlugChange = viewModel::setSlug,
                            onCommentChange = viewModel::setComment,
                            onBodyFieldChange = { tfv ->
                                bodyField = tfv
                                if (tfv.text != state.content) viewModel.setContent(tfv.text)
                            },
                            onInsertAttachment = {
                                savedCursor = bodyField.selection.end
                                insertDialogOpen = true
                            },
                            onOpenAttachments = {
                                val slug = state.slug.ifEmpty { return@EditFields }
                                navController.navigate(WikisApp.attachments(viewModel.wikiId, slug))
                            },
                        )
                    }
                }
            }
        }
    }

    if (state.showPreview) {
        // The sheet is composed outside the Scaffold body, so it needs the wiki
        // context handed to it directly - MarkdownContent resolves attachment
        // URLs against it and raises without one.
        CompositionLocalProvider(LocalWikiContext provides wikiCtx) {
        MarkdownPreviewSheet(onDismiss = { viewModel.togglePreview() }) {
            Text(
                text = state.title.ifEmpty {
                    stringResource(R.string.wikis_editor_preview_untitled)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            // The wiki's own renderer: it resolves attachment URLs against the
            // context this screen provides.
            MarkdownContent(content = state.content)
        }
        }
    }

    InsertAttachmentDialog(
        open = insertDialogOpen,
        viewModel = viewModel,
        cursor = savedCursor,
        onDismiss = { insertDialogOpen = false },
        onInserted = { newCursor ->
            insertDialogOpen = false
            bodyField = bodyField.copy(
                text = viewModel.uiState.value.content,
                selection = TextRange(newCursor.coerceIn(0, viewModel.uiState.value.content.length)),
            )
        },
    )
}


@Composable
private fun EditFields(
    isNew: Boolean,
    title: String,
    slug: String,
    comment: String,
    bodyField: TextFieldValue,
    onTitleChange: (String) -> Unit,
    onSlugChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onBodyFieldChange: (TextFieldValue) -> Unit,
    onInsertAttachment: () -> Unit,
    onOpenAttachments: () -> Unit,
) {
    // Title first: on a new page the address below is derived from it, so the
    // field that leads has to be the one that is typed first.
    MochiTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text(stringResource(R.string.wikis_editor_title_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    if (isNew) {
        MochiTextField(
            value = slug,
            onValueChange = onSlugChange,
            label = { Text(stringResource(R.string.wikis_editor_slug_label)) },
            placeholder = { Text(stringResource(R.string.wikis_editor_slug_hint)) },
            singleLine = true,
            supportingText = { Text(stringResource(R.string.wikis_editor_slug_help)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
    }

    MochiTextField(
        value = bodyField,
        onValueChange = onBodyFieldChange,
        label = { Text(stringResource(R.string.wikis_editor_content_label)) },
        placeholder = { Text(stringResource(R.string.wikis_editor_content_placeholder)) },
        minLines = 18,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions.Default,
    )

    // Docked under the box it writes into, so the markup lands where the eye
    // already is.
    MarkdownToolbar(
        body = bodyField,
        onBodyChange = onBodyFieldChange,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        // A wiki's own errands: pull a file into the text, or go manage them.
        MarkdownToolbarSeparator()
        MochiIconButton(onClick = onInsertAttachment) {
            Icon(
                Icons.Filled.Image,
                contentDescription = stringResource(R.string.wikis_editor_insert),
                modifier = Modifier.size(20.dp),
            )
        }
        MochiIconButton(onClick = onOpenAttachments, enabled = slug.isNotBlank()) {
            Icon(
                Icons.Filled.AttachFile,
                contentDescription = stringResource(R.string.wikis_editor_attachments),
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (!isNew) {
        Spacer(Modifier.height(12.dp))
        MochiTextField(
            value = comment,
            onValueChange = onCommentChange,
            label = { Text(stringResource(R.string.wikis_editor_summary_label)) },
            placeholder = { Text(stringResource(R.string.wikis_editor_summary_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
