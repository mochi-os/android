// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.mochios.android.ui.components.Section
import org.mochios.forums.R
import org.mochios.forums.model.ModerationSettings
import org.mochios.forums.ui.moderation.ModerationViewModel

/** How long typing must pause before the edit is saved. */
private const val SAVE_DEBOUNCE_MS = 700L

/**
 * Size of the inline number boxes. Wide enough for the longest value these
 * settings hold — a four-digit window duration in seconds — since the label
 * beside them is what needs the room.
 */
private val NUMBER_FIELD_WIDTH = 80.dp
private val NUMBER_FIELD_HEIGHT = 44.dp

/**
 * Moderation settings tab: pre-moderation toggles and rate limits. Backed by
 * [ModerationViewModel], which resolves the same `forumId` route argument the
 * settings screen was opened with. Edits save themselves — toggles on the spot,
 * typed numbers once the user pauses — and each save reports through [onMessage].
 *
 * @param onMessage receives a string resource to confirm a finished save; the
 *                  settings screen owns the snackbar that shows it.
 */
@Composable
fun ModerationTab(
    onMessage: (Int) -> Unit,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSettings() }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { messageRes ->
            onMessage(messageRes)
            viewModel.clearActionMessage()
        }
    }

    var moderationPosts by remember(uiState.settings) {
        mutableStateOf(uiState.settings?.moderationPosts ?: false)
    }
    var moderationComments by remember(uiState.settings) {
        mutableStateOf(uiState.settings?.moderationComments ?: false)
    }
    var moderationNew by remember(uiState.settings) {
        mutableStateOf(uiState.settings?.moderationNew ?: false)
    }
    var newUserDays by remember(uiState.settings) {
        mutableStateOf((uiState.settings?.newUserDays ?: 0).toString())
    }
    var postLimit by remember(uiState.settings) {
        mutableStateOf((uiState.settings?.postLimit ?: 0).toString())
    }
    var commentLimit by remember(uiState.settings) {
        mutableStateOf((uiState.settings?.commentLimit ?: 0).toString())
    }
    var limitWindow by remember(uiState.settings) {
        mutableStateOf((uiState.settings?.limitWindow ?: 3600).toString())
    }

    val saved = uiState.settings
    if (saved == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // The edits as they stand. Reads the state vars directly, so a row that
    // writes its var before calling this sees its own new value.
    fun draft() = ModerationSettings(
        moderationPosts = moderationPosts,
        moderationComments = moderationComments,
        moderationNew = moderationNew,
        newUserDays = newUserDays.toIntOrNull() ?: 0,
        postLimit = postLimit.toIntOrNull() ?: 0,
        commentLimit = commentLimit.toIntOrNull() ?: 0,
        limitWindow = limitWindow.toIntOrNull() ?: 3600,
    )

    // Typing saves once the user pauses; a per-keystroke POST would be a
    // request per digit. Toggles save on the spot instead (see below), so this
    // is keyed on the numeric fields only.
    LaunchedEffect(newUserDays, postLimit, commentLimit, limitWindow) {
        val edited = draft()
        if (edited == saved) return@LaunchedEffect
        delay(SAVE_DEBOUNCE_MS)
        viewModel.saveSettings(edited)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section(
            title = stringResource(R.string.forums_moderation_section_premoderation),
            description = stringResource(
                R.string.forums_moderation_section_premoderation_description
            ),
        ) {
            ToggleRow(
                title = stringResource(R.string.forums_moderation_require_posts),
                description = stringResource(R.string.forums_moderation_require_posts_description),
                checked = moderationPosts,
                onCheckedChange = { checked ->
                    moderationPosts = checked
                    viewModel.saveSettings(draft())
                },
            )
            ToggleRow(
                title = stringResource(R.string.forums_moderation_require_comments),
                description = stringResource(
                    R.string.forums_moderation_require_comments_description
                ),
                checked = moderationComments,
                onCheckedChange = { checked ->
                    moderationComments = checked
                    viewModel.saveSettings(draft())
                },
            )
            ToggleRow(
                title = stringResource(R.string.forums_moderation_require_new_users),
                description = stringResource(
                    R.string.forums_moderation_require_new_users_description
                ),
                checked = moderationNew,
                onCheckedChange = { checked ->
                    moderationNew = checked
                    viewModel.saveSettings(draft())
                },
            )
            // The threshold only bites while new users are pre-moderated, so it
            // sits inside that toggle's section rather than standing on its own.
            if (moderationNew) {
                Spacer(Modifier.height(12.dp))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.forums_moderation_new_user_days),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.width(12.dp))
                        NumberField(
                            value = newUserDays,
                            onValueChange = { value -> newUserDays = value },
                        )
                        Spacer(Modifier.width(8.dp))
                        UnitLabel(stringResource(R.string.forums_moderation_unit_days))
                    }
                }
            }
        }

        Section(
            title = stringResource(R.string.forums_moderation_section_ratelimiting),
            description = stringResource(
                R.string.forums_moderation_section_ratelimiting_description
            ),
        ) {
            LimitRow(
                label = stringResource(R.string.forums_moderation_post_limit),
                unit = stringResource(R.string.forums_moderation_unit_posts),
                value = postLimit,
                onValueChange = { value -> postLimit = value },
            )
            LimitRow(
                label = stringResource(R.string.forums_moderation_comment_limit),
                unit = stringResource(R.string.forums_moderation_unit_replies),
                value = commentLimit,
                onValueChange = { value -> commentLimit = value },
            )
            LimitRow(
                label = stringResource(R.string.forums_moderation_limit_window),
                unit = stringResource(R.string.forums_moderation_unit_seconds),
                value = limitWindow,
                onValueChange = { value -> limitWindow = value },
            )
        }
    }
}

/** Title over its explanation, with the switch trailing. */
@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sized like a Material list row: the setting name leads at bodyLarge
        // with its explanation as supporting text. Normal weight on purpose —
        // the section header carries the bold, and semi-bold here would flatten
        // the gap between the two.
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Label on the left, number box and its unit trailing. */
@Composable
private fun LimitRow(
    label: String,
    unit: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        NumberField(value = value, onValueChange = onValueChange)
        Spacer(Modifier.width(8.dp))
        UnitLabel(unit)
    }
}

/** Digits-only box sized for the numbers these settings hold. */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    // A plain OutlinedTextField can't go under its 56.dp minimum without
    // clipping its own padding, so this is a BasicTextField wearing the same
    // outline — the only way to a box this small that still centres its digits.
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { char -> char.isDigit() }) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .width(NUMBER_FIELD_WIDTH)
            .height(NUMBER_FIELD_HEIGHT)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clip(shape),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                innerTextField()
            }
        },
    )
}

/** The unit a number box is counted in — "days", "posts", "seconds". */
@Composable
private fun UnitLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
