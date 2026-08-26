// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.ComposeBarAttachments
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.wikis.R

/**
 * Compose surface for new comments and in-thread replies. Send stays enabled
 * when files are queued, so attachment-only posts work.
 */
@Composable
fun CommentForm(
    onSubmit: (body: String, files: List<Uri>?) -> Unit,
    resolveFileName: suspend (Uri) -> String,
    initialText: String = "",
    onCancel: (() -> Unit)? = null,
    placeholder: String = stringResource(R.string.wikis_comment_form_placeholder_new),
    autoFocus: Boolean = false,
    onTextChange: ((String) -> Unit)? = null,
    // Neither caller is a bottom bar — the new-comment form sits above the
    // comment list and the reply form sits inside it — so by default this
    // consumes nothing. Padding a mid-screen form by the keyboard height
    // would just push it around. See ComposeBarDefaults.
    windowInsets: WindowInsets = ComposeBarDefaults.NoWindowInsets,
    showDivider: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }

    var body by remember(initialText) { mutableStateOf(initialText) }
    val files = remember { mutableStateListOf<Uri>() }

    // Whenever the caller seeds a new value (e.g. quote-on-select drops a
    // freshly-quoted draft into the reply textarea), reset the local field.
    LaunchedEffect(initialText) {
        if (body != initialText) body = initialText
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }

    fun handleSubmit() {
        val trimmed = body.trim()
        if (trimmed.isBlank() && files.isEmpty()) return
        val attachments = if (files.isNotEmpty()) files.toList() else null
        onSubmit(trimmed, attachments)
        body = ""
        files.clear()
        onTextChange?.invoke("")
    }

    ComposeBar(
        value = body,
        onValueChange = {
            body = it
            onTextChange?.invoke(it)
        },
        onSend = { handleSubmit() },
        placeholder = placeholder,
        sendLabel = stringResource(R.string.wikis_comment_form_send),
        attachments = ComposeBarAttachments(
            pending = files.toList(),
            onAdd = { uris -> files.addAll(uris) },
            onRemove = { uri -> files.remove(uri) },
            resolveFileName = resolveFileName,
            addLabel = stringResource(R.string.wikis_comment_form_attach),
            fallbackLabel = stringResource(R.string.wikis_comment_form_attach),
            removeLabel = stringResource(R.string.wikis_comment_form_remove_attachment),
        ),
        focusRequester = focusRequester,
        windowInsets = windowInsets,
        showDivider = showDivider,
        trailingContent = onCancel?.let {
            {
                MochiTextButton(onClick = it) {
                    Text(stringResource(R.string.wikis_comment_action_cancel))
                }
            }
        },
    )
}
