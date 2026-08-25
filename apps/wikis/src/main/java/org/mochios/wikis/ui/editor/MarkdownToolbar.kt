// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.wikis.R

/**
 * The strip under the content field: the markdown a page is written in, then
 * the two ways to bring a file into it.
 *
 * Every button acts on the cursor rather than the end of the text, so it marks
 * up what the user has selected and leaves them where they can keep typing.
 * The row scrolls sideways because a narrow phone will not hold all of it.
 */
@Composable
fun MarkdownToolbar(
    body: TextFieldValue,
    onBodyChange: (TextFieldValue) -> Unit,
    onInsertAttachment: () -> Unit,
    onOpenAttachments: () -> Unit,
    attachmentsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(Icons.Filled.FormatBold, R.string.wikis_editor_format_bold) {
            onBodyChange(body.wrappedIn("**"))
        }
        ToolButton(Icons.Filled.FormatItalic, R.string.wikis_editor_format_italic) {
            onBodyChange(body.wrappedIn("_"))
        }
        ToolButton(Icons.Filled.Link, R.string.wikis_editor_format_link) {
            onBodyChange(body.linked())
        }
        ToolButton(Icons.Filled.Title, R.string.wikis_editor_format_heading) {
            onBodyChange(body.linesPrefixed("## "))
        }
        ToolButton(Icons.Filled.Code, R.string.wikis_editor_format_code) {
            onBodyChange(body.wrappedIn("`"))
        }
        ToolButton(Icons.Filled.FormatListBulleted, R.string.wikis_editor_format_list) {
            onBodyChange(body.linesPrefixed("- "))
        }

        // Marking up text and reaching for a file are different errands.
        VerticalDivider(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .height(20.dp),
        )

        ToolButton(Icons.Filled.Image, R.string.wikis_editor_insert, onClick = onInsertAttachment)
        ToolButton(
            Icons.Filled.AttachFile,
            R.string.wikis_editor_attachments,
            enabled = attachmentsEnabled,
            onClick = onOpenAttachments,
        )
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    MochiIconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = stringResource(labelRes), modifier = Modifier.size(20.dp))
    }
}

// ---- Cursor-aware markdown edits ----

/**
 * Put [prefix]/[suffix] around the selection. With nothing selected the pair
 * opens at the cursor and leaves it between them, so the next keystroke lands
 * inside the markers rather than after them.
 */
internal fun TextFieldValue.wrappedIn(
    prefix: String,
    suffix: String = prefix,
): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val wrapped = prefix + text.substring(start, end) + suffix
    val caret = if (start == end) start + prefix.length else start + wrapped.length
    return TextFieldValue(text.replaceRange(start, end, wrapped), TextRange(caret))
}

/**
 * A markdown link, with the half the user still has to write left selected -
 * the address when they had text in hand, the text when they did not.
 */
internal fun TextFieldValue.linked(): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val selected = text.substring(start, end)
    val label = selected.ifEmpty { LINK_LABEL }
    val link = "[$label]($LINK_TARGET)"
    val placeholderStart = if (selected.isEmpty()) start + 1 else start + label.length + 3
    val placeholder = if (selected.isEmpty()) label else LINK_TARGET
    return TextFieldValue(
        text = text.replaceRange(start, end, link),
        selection = TextRange(placeholderStart, placeholderStart + placeholder.length),
    )
}

/**
 * Put [marker] at the head of every line the selection touches, skipping any
 * that already carries it so a second tap does not stack markers up.
 */
internal fun TextFieldValue.linesPrefixed(marker: String): TextFieldValue {
    val lineStart = if (selection.min == 0) {
        0
    } else {
        text.lastIndexOf('\n', selection.min - 1).let { if (it < 0) 0 else it + 1 }
    }
    val lineEnd = text.indexOf('\n', selection.max).let { if (it < 0) text.length else it }
    val marked = text.substring(lineStart, lineEnd)
        .split("\n")
        .joinToString("\n") { line -> if (line.startsWith(marker)) line else marker + line }
    return TextFieldValue(
        text = text.replaceRange(lineStart, lineEnd, marked),
        selection = TextRange(lineStart + marked.length),
    )
}

/** Stand-ins a tap leaves selected, so typing replaces them. */
private const val LINK_LABEL = "text"
private const val LINK_TARGET = "url"
