// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.NaturalCompare
import org.mochios.feeds.R
import org.mochios.feeds.model.PublishAccount
import org.mochios.feeds.ui.component.POLL_DURATIONS
import org.mochios.feeds.ui.component.POLL_OPTIONS_MAXIMUM
import org.mochios.feeds.ui.component.POLL_OPTIONS_MINIMUM
import org.mochios.feeds.ui.component.PollDraft
import org.mochios.feeds.ui.component.VISIBILITIES

/**
 * Where a post publishes: the feed's accounts on other networks, each a switch
 * preset from the feed's list, for this post alone.
 */
@Composable
internal fun PublishSwitches(
    accounts: List<PublishAccount>,
    chosen: List<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (accounts.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.feeds_publish_to), style = MaterialTheme.typography.titleMedium)
        accounts.sortedWith(compareBy(NaturalCompare) { it.identifier }).forEach { account ->
            val on = account.id in chosen
            SwitchRow(label = account.identifier, checked = on, onChange = { onToggle(account.id, it) })
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The label of a visibility as the composer offers it. */
@Composable
internal fun visibilityLabel(visibility: String): String = when (visibility) {
    "unlisted" -> stringResource(R.string.feeds_visibility_unlisted)
    "followers" -> stringResource(R.string.feeds_visibility_followers)
    else -> stringResource(R.string.feeds_visibility_public)
}

/**
 * What a post is made with beyond its text: who may see it, a content warning
 * and the sensitive flag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostFeaturesFields(
    visibility: String,
    warning: String,
    sensitive: Boolean,
    onVisibility: (String) -> Unit,
    onWarning: (String) -> Unit,
    onSensitive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
            MochiTextField(
                value = visibilityLabel(visibility),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.feeds_visibility)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                VISIBILITIES.forEach { value ->
                    MochiDropdownMenuItem(
                        text = { Text(visibilityLabel(value)) },
                        onClick = {
                            open = false
                            onVisibility(value)
                        },
                    )
                }
            }
        }
        MochiTextField(
            value = warning,
            onValueChange = onWarning,
            label = { Text(stringResource(R.string.feeds_content_warning)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(label = stringResource(R.string.feeds_sensitive_media), checked = sensitive, onChange = onSensitive)
    }
}

/** How long a poll runs, in the reader's words. */
@Composable
internal fun pollDurationLabel(seconds: Int): String {
    val format = LocalFormat.current
    return when {
        seconds < 3600 -> (seconds / 60).let { pluralStringResource(R.plurals.feeds_poll_minutes, it, format.formatNumber(it)) }
        seconds < 86400 -> (seconds / 3600).let { pluralStringResource(R.plurals.feeds_poll_hours, it, format.formatNumber(it)) }
        else -> (seconds / 86400).let { pluralStringResource(R.plurals.feeds_poll_days, it, format.formatNumber(it)) }
    }
}

/** The poll a post is made with: its options, whether several may be chosen, and how long it runs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PollEditor(
    poll: PollDraft,
    onOption: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemoveOption: (Int) -> Unit,
    onMultiple: (Boolean) -> Unit,
    onDuration: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = LocalFormat.current
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.feeds_poll), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            MochiIconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.feeds_poll_remove), modifier = Modifier.size(18.dp))
            }
        }
        poll.options.forEachIndexed { index, option ->
            val number = format.formatNumber(index + 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MochiTextField(
                    value = option,
                    onValueChange = { onOption(index, it) },
                    label = { Text(stringResource(R.string.feeds_poll_option, number)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (poll.options.size > POLL_OPTIONS_MINIMUM) {
                    MochiIconButton(onClick = { onRemoveOption(index) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.feeds_poll_option_remove, number),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        if (poll.options.size < POLL_OPTIONS_MAXIMUM) {
            MochiOutlinedButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.feeds_poll_option_add))
            }
        }
        SwitchRow(label = stringResource(R.string.feeds_poll_multiple), checked = poll.multiple, onChange = onMultiple)
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
            MochiTextField(
                value = pollDurationLabel(poll.duration),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.feeds_poll_duration)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                POLL_DURATIONS.forEach { seconds ->
                    MochiDropdownMenuItem(
                        text = { Text(pollDurationLabel(seconds)) },
                        onClick = {
                            open = false
                            onDuration(seconds)
                        },
                    )
                }
            }
        }
    }
}
