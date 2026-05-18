package org.mochios.wikis.ui.editor

import android.widget.Toast
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import org.mochios.android.api.userMessage
import org.mochios.wikis.R
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.MarkdownContent

/**
 * Single screen powering both the "Edit page" and "Create new page" flows.
 *
 * Mirrors `apps/wikis/web/src/features/wiki/page-editor.tsx`. The bulk of the
 * branching lives on the `isNew` flag carried by [PageEditorViewModel] — for
 * a new page we expose the slug field and the "Create page" submit, for an
 * existing page we expose the edit-summary field, the preview toggle, and
 * (when permissions allow) the Delete action.
 *
 * Toast and navigation are driven from [PageEditorEvent] so the ViewModel
 * stays composable-free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    navController: NavController,
    viewModel: PageEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val wikiCtx = LocalWikiContext.current

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
                        popUpTo(WikisApp.wikiHome(viewModel.wikiId)) { inclusive = false }
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
                        text = if (viewModel.isNew) {
                            stringResource(R.string.wikis_editor_title_new)
                        } else {
                            val name = state.originalTitle.ifEmpty { state.slug }
                            stringResource(R.string.wikis_editor_title_edit, name)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(org.mochios.android.R.string.common_back),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Action row (preview/edit, insert, attachments, cancel, delete, save).
            EditorActions(
                isNew = viewModel.isNew,
                showPreview = state.showPreview,
                isSaving = state.isSaving,
                isDeleting = state.isDeleting,
                canDelete = !viewModel.isNew && (wikiCtx?.permissions?.delete == true),
                onTogglePreview = { viewModel.togglePreview() },
                onOpenInsert = {
                    savedCursor = bodyField.selection.end
                    insertDialogOpen = true
                },
                onOpenAttachments = {
                    val slug = state.slug.ifEmpty { return@EditorActions }
                    navController.navigate(WikisApp.attachments(viewModel.wikiId, slug))
                },
                onCancel = { navController.popBackStack() },
                onDelete = {
                    val slug = state.slug.ifEmpty { return@EditorActions }
                    navController.navigate(WikisApp.pageDelete(viewModel.wikiId, slug))
                },
                onSave = {
                    viewModel.save(
                        invalidTitle = titleRequiredMsg,
                        invalidSlug = slugRequiredMsg,
                        createFailed = createFailedMsg,
                        editFailed = editFailedMsg,
                    )
                },
            )

            Spacer(Modifier.height(16.dp))

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
                    OutlinedButton(onClick = { viewModel.retry() }) {
                        Text(stringResource(org.mochios.android.R.string.common_retry))
                    }
                }
                state.showPreview -> {
                    Text(
                        text = state.title.ifEmpty {
                            stringResource(R.string.wikis_editor_preview_untitled)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))
                    MarkdownContent(content = state.content)
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
                    )
                }
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
private fun EditorActions(
    isNew: Boolean,
    showPreview: Boolean,
    isSaving: Boolean,
    isDeleting: Boolean,
    canDelete: Boolean,
    onTogglePreview: () -> Unit,
    onOpenInsert: () -> Unit,
    onOpenAttachments: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    val rowState = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rowState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onTogglePreview) {
            Icon(
                imageVector = if (showPreview) Icons.Filled.Edit else Icons.Filled.Visibility,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(
                    if (showPreview) R.string.wikis_editor_edit else R.string.wikis_editor_preview,
                )
            )
        }
        OutlinedButton(onClick = onOpenInsert) {
            Icon(
                Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.wikis_editor_insert))
        }
        if (!isNew) {
            OutlinedButton(onClick = onOpenAttachments) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.wikis_editor_attachments))
            }
        }
        OutlinedButton(onClick = onCancel) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.wikis_editor_cancel))
        }
        if (canDelete) {
            OutlinedButton(onClick = onDelete, enabled = !isDeleting) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.wikis_editor_delete))
            }
        }
        Button(onClick = onSave, enabled = !isSaving) {
            if (isSaving) {
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
                        isSaving && isNew -> R.string.wikis_editor_creating
                        isSaving -> R.string.wikis_editor_saving
                        isNew -> R.string.wikis_editor_create
                        else -> R.string.wikis_editor_save
                    }
                )
            )
        }
    }
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
) {
    if (isNew) {
        OutlinedTextField(
            value = slug,
            onValueChange = onSlugChange,
            label = { Text(stringResource(R.string.wikis_editor_slug_label)) },
            placeholder = { Text(stringResource(R.string.wikis_editor_slug_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
    }

    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text(stringResource(R.string.wikis_editor_title_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = bodyField,
        onValueChange = onBodyFieldChange,
        label = { Text(stringResource(R.string.wikis_editor_content_label)) },
        placeholder = { Text(stringResource(R.string.wikis_editor_content_placeholder)) },
        minLines = 18,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions.Default,
    )

    if (!isNew) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = comment,
            onValueChange = onCommentChange,
            label = { Text(stringResource(R.string.wikis_editor_summary_label)) },
            placeholder = { Text(stringResource(R.string.wikis_editor_summary_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
