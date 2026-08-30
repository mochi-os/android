// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.mochios.android.util.SEARCH_DEBOUNCE

data class MentionSuggestion(
    val id: String,
    val name: String
)

/**
 * Text field with @mention autocomplete: typing @ queries [onSearch], and
 * choosing a suggestion inserts @[name] at the cursor.
 *
 * The caller keeps only the text; the cursor is this field's own business.
 * Where a caller needs to place the cursor itself - a markdown toolbar marking
 * up a selection - it holds the [TextFieldValue] overload instead.
 */
@Composable
fun MentionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: suspend (query: String) -> List<MentionSuggestion>,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    singleLine: Boolean = false,
    fillHeight: Boolean = false
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    // An edit from elsewhere lands with the cursor at the end - there is no
    // better guess when only the text came across.
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    MentionTextField(
        value = field,
        onValueChange = { updated ->
            field = updated
            if (updated.text != value) onValueChange(updated.text)
        },
        onSearch = onSearch,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        maxLines = maxLines,
        minLines = minLines,
        singleLine = singleLine,
        fillHeight = fillHeight,
    )
}

/**
 * [MentionTextField] for a caller that drives the cursor as well as the text.
 */
@OptIn(FlowPreview::class)
@Composable
fun MentionTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: suspend (query: String) -> List<MentionSuggestion>,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    singleLine: Boolean = false,
    // When true the field stretches to fill a height-constrained [modifier], so
    // its border spans the full box rather than wrapping the text.
    fillHeight: Boolean = false
) {
    var suggestions by remember { mutableStateOf<List<MentionSuggestion>>(emptyList()) }
    var mentionQuery by remember { mutableStateOf<String?>(null) }
    var mentionStart by remember { mutableStateOf(-1) }

    // Debounce mention search
    LaunchedEffect(mentionQuery) {
        snapshotFlow { mentionQuery }
            .debounce(SEARCH_DEBOUNCE)
            .distinctUntilChanged()
            .collectLatest { query ->
                if (query != null && query.isNotEmpty()) {
                    suggestions = try { onSearch(query) } catch (_: Exception) { emptyList() }
                } else {
                    suggestions = emptyList()
                }
            }
    }

    Column(modifier = modifier) {
        MochiTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)

                // Detect @mention trigger
                val cursor = newValue.selection.start
                val text = newValue.text
                if (cursor > 0) {
                    // Find the @ before cursor
                    val beforeCursor = text.substring(0, cursor)
                    val atIndex = beforeCursor.lastIndexOf('@')
                    if (atIndex >= 0) {
                        val between = beforeCursor.substring(atIndex + 1)
                        // Only trigger if no space in the mention query and @ is at start or preceded by space
                        if (!between.contains(' ') && !between.contains('\n') &&
                            (atIndex == 0 || text[atIndex - 1] == ' ' || text[atIndex - 1] == '\n')
                        ) {
                            mentionQuery = between
                            mentionStart = atIndex
                        } else {
                            mentionQuery = null
                            suggestions = emptyList()
                        }
                    } else {
                        mentionQuery = null
                        suggestions = emptyList()
                    }
                } else {
                    mentionQuery = null
                    suggestions = emptyList()
                }
            },
            label = label,
            placeholder = placeholder,
            maxLines = maxLines,
            minLines = minLines,
            singleLine = singleLine,
            modifier = if (fillHeight) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                Modifier.fillMaxWidth()
            }
        )

        if (suggestions.isNotEmpty()) {
            MochiCard(
                modifier = Modifier.fillMaxWidth(),
                shape = mochiPopupShape(),
                colors = CardDefaults.cardColors(
                    containerColor = mochiPopupContainerColor()
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = MochiPopupElevation)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                    items(suggestions.take(5)) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Replace @query with @[name]
                                    val text = value.text
                                    val replacement = "@[${suggestion.name}] "
                                    val before = text.substring(0, mentionStart)
                                    val after = text.substring(
                                        (mentionStart + 1 + (mentionQuery?.length ?: 0))
                                            .coerceAtMost(text.length)
                                    )
                                    val newText = before + replacement + after
                                    val newCursor = before.length + replacement.length
                                    onValueChange(
                                        TextFieldValue(newText, TextRange(newCursor))
                                    )
                                    mentionQuery = null
                                    suggestions = emptyList()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = suggestion.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
