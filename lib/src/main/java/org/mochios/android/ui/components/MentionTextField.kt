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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.theme.LocalSpacing
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
    // The mention being written is read from the value itself, whoever set
    // it, rather than remembered from the field's own edits: text the caller
    // replaces takes its mention, or the lack of one, with it.
    val mention = mentionAt(value)
    val start = mention?.start
    val query = mention?.query?.takeIf { it.isNotEmpty() }
    var lookup by remember { mutableStateOf<Lookup?>(null) }
    val search by rememberUpdatedState(onSearch)
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val spacing = LocalSpacing.current

    // A keystroke restarts the wait, so only a pause searches; what is shown
    // stays until the next results land.
    LaunchedEffect(start, query) {
        if (start == null || query == null) {
            lookup = null
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE)
        lookup = try {
            val found = search(query)
            // A search that swallowed its own cancellation must not land
            // over a newer query.
            ensureActive()
            Lookup(start, found, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Lookup(start, emptyList(), e.toMochiError().userMessage())
        }
    }

    Column(modifier = modifier) {
        MochiTextField(
            value = value,
            onValueChange = onValueChange,
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

        // Results belong to the mention they were found for: one begun
        // elsewhere in the text shows nothing until its own arrive.
        val shown = lookup?.takeIf { query != null && it.start == start }
        val failure = shown?.failure
        if (failure != null) {
            Text(
                text = failure,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.gap(12.dp), vertical = spacing.gap(8.dp))
            )
        } else if (shown != null && shown.suggestions.isNotEmpty()) {
            MochiCard(
                modifier = Modifier.fillMaxWidth(),
                shape = mochiPopupShape(),
                colors = CardDefaults.cardColors(
                    containerColor = mochiPopupContainerColor()
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = MochiPopupElevation)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                    items(shown.suggestions.take(5)) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Read at the tap, from the value the field
                                    // holds now, so the replacement indexes the
                                    // text it was found in.
                                    val now = current
                                    val at = mentionAt(now)?.takeIf { it.start == shown.start }
                                        ?: return@clickable
                                    change(mentioned(now, at, suggestion.name))
                                    lookup = null
                                }
                                .padding(horizontal = spacing.gap(12.dp), vertical = spacing.gap(8.dp))
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

/** An @mention being written: where its @ is, and what follows it up to the cursor. */
internal data class Mention(val start: Int, val query: String)

/** What a search for the mention whose @ is at [start] found, or the line saying why it failed. */
private class Lookup(val start: Int, val suggestions: List<MentionSuggestion>, val failure: String?)

/**
 * The mention [value]'s cursor is in, or null: an @ at the start of the text
 * or after a space or line break, with neither between it and the cursor.
 */
internal fun mentionAt(value: TextFieldValue): Mention? {
    val text = value.text
    val cursor = value.selection.start
    if (cursor <= 0 || cursor > text.length) return null
    val at = text.lastIndexOf('@', cursor - 1)
    if (at < 0) return null
    val query = text.substring(at + 1, cursor)
    if (query.contains(' ') || query.contains('\n')) return null
    if (at > 0 && text[at - 1] != ' ' && text[at - 1] != '\n') return null
    return Mention(at, query)
}

/**
 * [value] with [mention], which must be [mentionAt]'s answer for it, written
 * out as @[name] and a space, and the cursor after them.
 */
internal fun mentioned(value: TextFieldValue, mention: Mention, name: String): TextFieldValue {
    val text = value.text
    val replacement = "@[$name] "
    val before = text.substring(0, mention.start)
    val after = text.substring(mention.start + 1 + mention.query.length)
    return TextFieldValue(before + replacement + after, TextRange(before.length + replacement.length))
}
