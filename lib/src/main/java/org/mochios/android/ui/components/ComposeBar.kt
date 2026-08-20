// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.files.rememberFileLabel

/**
 * Defaults for [ComposeBar], chiefly the keyboard-inset choice.
 *
 * The bar is the bottom-most thing on screen, so something has to lift it
 * clear of the keyboard — and which "something" depends entirely on the host.
 * Getting this wrong is invisible until you type: either the keyboard covers
 * the field, or the bar floats a keyboard's height above it.
 */
object ComposeBarDefaults {

    /**
     * For a bar sitting directly in a `Scaffold` — in its `bottomBar` slot or
     * as the last child of its content. This is the default.
     *
     * Both insets, because the bar is the bottom-most thing on screen and owes
     * whichever is taller: the keyboard when it is up, the navigation bar when
     * it is not. Taking only the keyboard left the field sitting on the gesture
     * bar at rest, which is what made the `bottomBar` hosts look cramped next to
     * the ones whose Scaffold content padding happened to cover it.
     *
     * A host that already pads its content with the Scaffold's `PaddingValues`
     * must also `consumeWindowInsets` them, or the navigation bar is counted
     * twice — that is the contract Scaffold documents.
     */
    val WindowInsets: WindowInsets
        @Composable get() = imeAndNavigationBars

    /**
     * For a bar inside a `MochiBottomSheet`, `BottomSheetScaffold`, or the
     * attachment lightbox. Those hosts already lift their content for the
     * keyboard; consuming the inset again would double the gap.
     */
    val NoWindowInsets: WindowInsets = WindowInsets(0)
}

/**
 * Whichever of the keyboard and the navigation bar is taller. Lives outside
 * [ComposeBarDefaults] because the object's own `WindowInsets` property would
 * shadow the type these extensions hang off.
 */
private val imeAndNavigationBars: WindowInsets
    @Composable get() = WindowInsets.ime.union(WindowInsets.navigationBars)

/**
 * Everything the attachment row needs, travelling together so that enabling
 * attachments is one parameter rather than six that must agree.
 *
 * The labels are the caller's because `lib` carries no translated strings for
 * them and every app already does — this component adds no new keys to a
 * catalogue that spans 103 locales.
 *
 * @param onMove supply to get reorder arrows on each chip when more than one
 *   attachment is pending; [moveUpLabel] and [moveDownLabel] are then required.
 */
data class ComposeBarAttachments(
    val pending: List<Uri>,
    val onAdd: (List<Uri>) -> Unit,
    val onRemove: (Uri) -> Unit,
    val resolveFileName: suspend (Uri) -> String,
    val addLabel: String,
    val fallbackLabel: String,
    val removeLabel: String,
    val onMove: ((Uri, Int) -> Unit)? = null,
    val moveUpLabel: String? = null,
    val moveDownLabel: String? = null,
)

/**
 * The app's one message / comment composer: an optional attachment button, a
 * text field, and a send button, over an optional row of attachment chips.
 *
 * Covers both shapes the apps need:
 *
 *  - **Message** — text and send only. Leave [attachments] null and the
 *    attachment button and chip row are absent (market's message thread, the
 *    game chat panels).
 *  - **Full** — pass [attachments] and the bar owns its own file picker,
 *    renders a chip per pending file, and removes one on tap (chat, forums,
 *    feeds, wikis).
 *
 * [banner] renders above everything — the "replying to ..." strip that
 * several callers put there.
 *
 * Mentions: supply [onSearchMentions] and the field becomes a
 * [MentionTextField]; otherwise it is a plain [MochiTextField].
 *
 * @param sendOnImeAction make the keyboard's action key send, and drop focus
 *   afterwards. Suits a single-line bar (the game chat pill); a multi-line
 *   comment field wants the return key to insert a newline instead.
 * @param trailingContent extra buttons between the field and send — wikis puts
 *   its Cancel there.
 * @param requireText refuse to send on attachments alone. Forums needs this —
 *   its server rejects a comment with files but no body — while chat is happy
 *   to send a bare attachment.
 * @param windowInsets which insets to consume. See [ComposeBarDefaults] — the
 *   default suits a Scaffold, and sheet/lightbox hosts want
 *   [ComposeBarDefaults.NoWindowInsets].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isSending: Boolean = false,
    sendLabel: String,
    attachments: ComposeBarAttachments? = null,
    requireText: Boolean = false,
    onSearchMentions: (suspend (String) -> List<MentionSuggestion>)? = null,
    focusRequester: FocusRequester? = null,
    errorMessage: String? = null,
    minLines: Int = 1,
    maxLines: Int = 4,
    sendOnImeAction: Boolean = false,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    windowInsets: WindowInsets = ComposeBarDefaults.WindowInsets,
    banner: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val filePicker = attachments?.let { config ->
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents(),
        ) { uris -> config.onAdd(uris) }
    }
    val pending = attachments?.pending.orEmpty()
    val hasContent =
        if (requireText) value.text.isNotBlank() else value.text.isNotBlank() || pending.isNotEmpty()
    val canSend = enabled && !isSending && hasContent

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            banner?.invoke()

            if (attachments != null && pending.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    pending.forEachIndexed { index, uri ->
                        AttachmentChip(
                            label = rememberFileLabel(
                                uri,
                                attachments.resolveFileName,
                                attachments.fallbackLabel,
                            ),
                            removeLabel = attachments.removeLabel,
                            moveUpLabel = attachments.moveUpLabel.takeIf {
                                attachments.onMove != null && index > 0
                            },
                            moveDownLabel = attachments.moveDownLabel.takeIf {
                                attachments.onMove != null && index < pending.lastIndex
                            },
                            onMoveUp = { attachments.onMove?.invoke(uri, -1) },
                            onMoveDown = { attachments.onMove?.invoke(uri, 1) },
                            onRemove = { attachments.onRemove(uri) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (filePicker != null && attachments != null) {
                    IconButton(onClick = { filePicker.launch("*/*") }, enabled = enabled) {
                        Icon(Icons.Default.AttachFile, contentDescription = attachments.addLabel)
                    }
                }

                val fieldModifier = Modifier
                    .weight(1f)
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                val placeholderSlot: (@Composable () -> Unit)? =
                    placeholder?.let { { Text(it) } }

                val submit = {
                    if (canSend) {
                        onSend()
                        if (sendOnImeAction) focusManager.clearFocus()
                    }
                }
                val keyboardOptions =
                    if (sendOnImeAction) KeyboardOptions(imeAction = ImeAction.Send)
                    else KeyboardOptions.Default

                if (onSearchMentions != null) {
                    // MentionTextField tracks its own selection, so the plain
                    // text is all it can round-trip.
                    MentionTextField(
                        value = value.text,
                        onValueChange = { onValueChange(TextFieldValue(it)) },
                        onSearch = onSearchMentions,
                        modifier = fieldModifier,
                        placeholder = placeholderSlot,
                        minLines = minLines,
                        maxLines = maxLines,
                    )
                } else {
                    MochiTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = fieldModifier,
                        placeholder = placeholderSlot,
                        enabled = enabled,
                        minLines = minLines,
                        maxLines = maxLines,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                    )
                }

                trailingContent?.invoke(this)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = submit, enabled = canSend) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = sendLabel)
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )
            }
        }
    }
}

/**
 * String convenience for callers that don't track selection — most of them.
 *
 * Holds the [TextFieldValue] itself rather than rebuilding one per
 * recomposition: a fresh `TextFieldValue(text)` carries a zero selection, so
 * the cursor would snap back to the start on every keystroke and the text
 * would come out reversed. An external change to [value] — a draft cleared
 * after send — is adopted with the cursor at the end.
 */
@Composable
fun ComposeBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isSending: Boolean = false,
    sendLabel: String,
    attachments: ComposeBarAttachments? = null,
    requireText: Boolean = false,
    onSearchMentions: (suspend (String) -> List<MentionSuggestion>)? = null,
    focusRequester: FocusRequester? = null,
    errorMessage: String? = null,
    minLines: Int = 1,
    maxLines: Int = 4,
    sendOnImeAction: Boolean = false,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    windowInsets: WindowInsets = ComposeBarDefaults.WindowInsets,
    banner: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    val current = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))

    ComposeBar(
        value = current,
        onValueChange = {
            field = it
            onValueChange(it.text)
        },
        onSend = onSend,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        isSending = isSending,
        sendLabel = sendLabel,
        attachments = attachments,
        requireText = requireText,
        onSearchMentions = onSearchMentions,
        focusRequester = focusRequester,
        errorMessage = errorMessage,
        minLines = minLines,
        maxLines = maxLines,
        sendOnImeAction = sendOnImeAction,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        windowInsets = windowInsets,
        banner = banner,
        trailingContent = trailingContent,
    )
}

/**
 * One pending attachment. Tapping the chip removes it; the reorder arrows are
 * their own buttons so they don't trigger the removal underneath.
 */
@Composable
private fun AttachmentChip(
    label: String,
    removeLabel: String,
    moveUpLabel: String?,
    moveDownLabel: String?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    AssistChip(
        onClick = onRemove,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = if (moveUpLabel != null || moveDownLabel != null) {
            {
                Row {
                    if (moveUpLabel != null) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(20.dp)) {
                            Icon(
                                Icons.Default.ExpandLess,
                                contentDescription = moveUpLabel,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    if (moveDownLabel != null) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(20.dp)) {
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = moveDownLabel,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        } else null,
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = removeLabel,
                modifier = Modifier.size(14.dp),
            )
        },
    )
}
