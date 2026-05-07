package org.mochios.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

data class MentionSuggestion(
    val id: String,
    val name: String
)

/**
 * Text field with @mention autocomplete support.
 * When user types @, queries [onSearch] for matching users.
 * Selecting a suggestion inserts @[name] at the cursor.
 */
@OptIn(FlowPreview::class)
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
    singleLine: Boolean = false
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    var suggestions by remember { mutableStateOf<List<MentionSuggestion>>(emptyList()) }
    var mentionQuery by remember { mutableStateOf<String?>(null) }
    var mentionStart by remember { mutableStateOf(-1) }

    // Sync external value changes
    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    // Debounce mention search
    LaunchedEffect(mentionQuery) {
        snapshotFlow { mentionQuery }
            .debounce(200)
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
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onValueChange(newValue.text)

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
            modifier = Modifier.fillMaxWidth()
        )

        if (suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                    items(suggestions.take(5)) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Replace @query with @[name]
                                    val text = textFieldValue.text
                                    val replacement = "@[${suggestion.name}] "
                                    val before = text.substring(0, mentionStart)
                                    val after = text.substring(
                                        (mentionStart + 1 + (mentionQuery?.length ?: 0))
                                            .coerceAtMost(text.length)
                                    )
                                    val newText = before + replacement + after
                                    val newCursor = before.length + replacement.length
                                    textFieldValue = TextFieldValue(newText, TextRange(newCursor))
                                    onValueChange(newText)
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
