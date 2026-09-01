// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.mochios.android.R

/**
 * Identity row with a fixed-width label so values align in a column.
 *
 * @param label Name of the field, in the fixed-width leading column.
 * @param content The value, laid out in the rest of the row.
 */
@Composable
fun IdentityRow(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        content()
    }
}

/**
 * A value that a pencil swaps for an inline editor; confirm saves the trimmed
 * text at once, cancel puts the original back.
 *
 * @param value Stored value, shown when not editing.
 * @param onSave Called with the trimmed draft when the tick is pressed.
 * @param editLabel Content description of the pencil.
 * @param modifier Modifier applied to the row.
 * @param saveLabel Content description of the confirm tick.
 * @param cancelLabel Content description of the cancel cross.
 * @param singleLine Whether the editor is one line; a multi-line editor opens
 *   three rows tall.
 * @param allowBlank Whether an empty draft may be saved.
 * @param placeholder Italic stand-in shown when [value] is blank; null renders
 *   a blank value as empty text.
 * @param clearLabel Content description of a button that empties the editor;
 *   null leaves the editor without one.
 * @param transform Applied to every keystroke, for fields that slugify or
 *   otherwise constrain what can be typed.
 */
@Composable
fun InlineTextEditor(
    value: String,
    onSave: (String) -> Unit,
    editLabel: String,
    modifier: Modifier = Modifier,
    saveLabel: String? = null,
    cancelLabel: String? = null,
    singleLine: Boolean = true,
    allowBlank: Boolean = true,
    placeholder: String? = null,
    clearLabel: String? = null,
    transform: (String) -> String = { text -> text }
) {
    var isEditing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }

    if (isEditing) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MochiTextField(
                value = draft,
                onValueChange = { text -> draft = transform(text) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                trailingIcon = if (clearLabel != null && draft.isNotEmpty()) {
                    {
                        MochiIconButton(onClick = { draft = "" }) {
                            Icon(Icons.Default.Close, contentDescription = clearLabel)
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.weight(1f)
            )
            MochiIconButton(
                onClick = {
                    onSave(draft.trim())
                    isEditing = false
                },
                enabled = allowBlank || draft.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = saveLabel ?: stringResource(R.string.common_save)
                )
            }
            MochiIconButton(onClick = {
                draft = value
                isEditing = false
            }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = cancelLabel ?: stringResource(R.string.common_cancel)
                )
            }
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            // fill = false keeps the pencil next to the value instead of pushed
            // to the far end, while the weight still lets a long value wrap.
            if (value.isBlank() && placeholder != null) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else {
                Text(text = value, modifier = Modifier.weight(1f, fill = false))
            }
            MochiIconButton(
                onClick = {
                    draft = value
                    isEditing = true
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = editLabel,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * An [IdentityRow] whose value is edited in place. See [InlineTextEditor] for
 * the editing behaviour.
 *
 * @param label Name of the field.
 * @param value Stored value.
 * @param onSave Called with the trimmed draft.
 * @param editLabel Content description of the pencil.
 * @param singleLine Whether the editor is one line.
 * @param allowBlank Whether an empty draft may be saved.
 * @param placeholder Italic stand-in shown when [value] is blank.
 * @param transform Applied to every keystroke.
 */
@Composable
fun EditableIdentityRow(
    label: String,
    value: String,
    onSave: (String) -> Unit,
    editLabel: String,
    singleLine: Boolean = true,
    allowBlank: Boolean = true,
    placeholder: String? = null,
    transform: (String) -> String = { text -> text }
) {
    IdentityRow(label = label) {
        InlineTextEditor(
            value = value,
            onSave = onSave,
            editLabel = editLabel,
            modifier = Modifier.weight(1f),
            singleLine = singleLine,
            allowBlank = allowBlank,
            placeholder = placeholder,
            transform = transform
        )
    }
}

/**
 * The banner editor: a multi-line box holding the notice a feed or forum shows
 * its readers, with Save enabled only once the draft has moved off what is
 * stored, and a Clear that empties the box without writing.
 *
 * @param title Section heading.
 * @param description Explanation under the heading.
 * @param hint Placeholder inside the empty box.
 * @param clearLabel Label of the clear button.
 * @param draft Current text.
 * @param stored Text the server holds, which Save is compared against.
 * @param onDraftChange Called as the box is typed in.
 * @param onSave Called with the draft when Save is pressed.
 */
@Composable
fun BannerSection(
    title: String,
    description: String,
    hint: String,
    clearLabel: String,
    draft: String,
    stored: String,
    onDraftChange: (String) -> Unit,
    onSave: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    Section(title = title, description = description) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            MochiTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text(hint) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MochiButton(
                    onClick = {
                        onSave(draft)
                        focusManager.clearFocus()
                    },
                    enabled = draft != stored
                ) {
                    Text(stringResource(R.string.common_save))
                }
                // Clear only empties the box — Save is what writes it, the same
                // as any other edit. Neutral rather than primary: it undoes
                // typing, it does not commit anything. Absent while the box is
                // empty: there is nothing to clear.
                if (draft.isNotEmpty()) {
                    MochiOutlinedButton(
                        onClick = {
                            onDraftChange("")
                            focusManager.clearFocus()
                        },
                        tone = MochiButtonTone.Neutral
                    ) {
                        Text(clearLabel)
                    }
                }
            }
        }
    }
}

/**
 * The section that ends a General tab: a heading whose only control deletes the
 * thing being configured, behind a confirmation.
 *
 * @param title Section heading.
 * @param buttonLabel Label of the delete button.
 * @param confirmTitle Title of the confirmation.
 * @param confirmMessage Body of the confirmation.
 * @param confirmLabel Label of the confirmation's destructive button.
 * @param onDelete Called once deletion is confirmed.
 * @param isDeleting Whether the delete is in flight; swaps the button for a
 *   spinner and refuses further presses.
 */
@Composable
fun DeleteSection(
    title: String,
    buttonLabel: String,
    confirmTitle: String,
    confirmMessage: String,
    confirmLabel: String,
    onDelete: () -> Unit,
    isDeleting: Boolean = false
) {
    var showConfirm by remember { mutableStateOf(false) }

    Section(
        title = title,
        headerAlignment = Alignment.CenterVertically,
        action = {
            MochiOutlinedButton(
                onClick = { showConfirm = true },
                enabled = !isDeleting,
                tone = MochiButtonTone.Neutral
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(buttonLabel)
                }
            }
        },
        content = {}
    )

    if (showConfirm) {
        MochiAlertDialog(
            onDismissRequest = { showConfirm = false },
            title = confirmTitle,
            text = confirmMessage,
            confirmText = confirmLabel,
            onConfirm = {
                showConfirm = false
                onDelete()
            },
            destructive = true,
            dismissText = stringResource(R.string.common_cancel),
        )
    }
}
