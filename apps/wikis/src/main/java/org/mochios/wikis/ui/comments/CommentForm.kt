// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochios.wikis.R
import java.io.File

/**
 * Compose surface for new top-level comments AND in-thread replies. Mirrors
 * `apps/wikis/web/src/features/wiki/comment-form.tsx`:
 *
 *  - Multi-line text field (minimum 3 rows).
 *  - Below the field: file chips for each picked attachment, with image
 *    previews via Coil's [AsyncImage] from the content URI.
 *  - Footer row: paperclip (attach), optional Cancel (when [onCancel] is
 *    provided), and a send button that fires either via IME action or tap.
 *
 * The send button is disabled when [body] is blank — matches web's
 * `disabled={!body.trim()}` behaviour, but also remains enabled when files
 * are queued so attachment-only posts work (web's send is body-only).
 *
 * @param initialText Seed value for the text field. Used for the reply
 *                    textarea's quote-on-select pre-fill, which arrives from
 *                    [CommentsViewModel.requestStartReply] via
 *                    [CommentsUiState.replyDraft].
 * @param onSubmit Called with `(body, files?)` when the user taps Send. The
 *                 caller is responsible for clearing state on success — this
 *                 component resets [body] and [files] locally after each
 *                 submission so the UX matches web.
 * @param onCancel If non-null, a Cancel button is rendered to the left of
 *                 Send. Reply textareas pass a non-null cancel; the top-level
 *                 compose form passes null.
 * @param placeholder Placeholder shown when the field is empty.
 * @param autoFocus If true, the text field requests focus on first composition.
 * @param onTextChange Optional notifier so callers (the reply form) can mirror
 *                     the user's edits back into shared state. Top-level form
 *                     leaves this null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentForm(
    onSubmit: (body: String, files: List<File>?) -> Unit,
    initialText: String = "",
    onCancel: (() -> Unit)? = null,
    placeholder: String = stringResource(R.string.wikis_comment_form_placeholder_new),
    autoFocus: Boolean = false,
    onTextChange: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var body by remember(initialText) { mutableStateOf(initialText) }
    val files = remember { mutableStateListOf<File>() }

    // Whenever the caller seeds a new value (e.g. quote-on-select drops a
    // freshly-quoted draft into the reply textarea), reset the local field.
    LaunchedEffect(initialText) {
        if (body != initialText) body = initialText
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        for (uri in uris) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            val temp = File(context.cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            files.add(temp)
        }
    }

    val canSend = body.isNotBlank() || files.isNotEmpty()

    fun handleSubmit() {
        val trimmed = body.trim()
        if (trimmed.isBlank() && files.isEmpty()) return
        val attachments = if (files.isNotEmpty()) files.toList() else null
        onSubmit(trimmed, attachments)
        body = ""
        files.clear()
        onTextChange?.invoke("")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = body,
            onValueChange = {
                body = it
                onTextChange?.invoke(it)
            },
            placeholder = { Text(placeholder) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            minLines = 3,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        )

        if (files.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                files.forEachIndexed { index, file ->
                    FileChip(
                        file = file,
                        onRemove = { files.removeAt(index) },
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = stringResource(R.string.wikis_comment_form_attach),
                )
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.wikis_comment_action_cancel))
                }
            }
            IconButton(
                onClick = { handleSubmit() },
                enabled = canSend,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.wikis_comment_form_send),
                )
            }
        }
    }
}

@Composable
private fun FileChip(
    file: File,
    onRemove: () -> Unit,
) {
    val isImage = remember(file.name) {
        val ext = file.extension.lowercase()
        ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (isImage) {
            AsyncImage(
                model = file,
                contentDescription = file.name,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Icon(
                Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = file.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(20.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.wikis_comment_form_remove_attachment),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
