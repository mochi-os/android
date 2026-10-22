// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.IdentityRow
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.Truncate
import org.mochios.forums.R

/** Tell the manager what a community or sensitive change came to. */
@Composable
internal fun CommunityEvents(viewModel: CommunityViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is CommunityEvent.Failure -> (event.error as? MochiError.Unknown)?.let {
                    it.message ?: resources.getString(event.fallback)
                } ?: event.error.userMessage()
                is CommunityEvent.Done -> resources.getString(event.message)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * The forum as a community on the fediverse: published on a community name
 * the owner takes on one of the domains they may take names on, or withdrawn,
 * which frees the name and tells the followers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunitySection(viewModel: CommunityViewModel) {
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val current = state ?: return
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    val domains = viewModel.domains(current)
    val chosen = domain.ifEmpty { domains.firstOrNull().orEmpty() }
    Section(
        title = stringResource(R.string.forums_community),
        action = if (current.published) {
            {
                MochiOutlinedButton(onClick = { viewModel.withdraw() }, enabled = !busy) {
                    Text(stringResource(R.string.forums_community_withdraw))
                }
            }
        } else null,
    ) {
        if (current.published) {
            IdentityRow(label = stringResource(R.string.forums_community_address_label)) {
                DataChip(value = current.identifier)
            }
            IdentityRow(label = stringResource(R.string.forums_community_link)) {
                DataChip(value = current.address, truncate = Truncate.MIDDLE)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MochiTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.forums_community_name)) },
                    placeholder = { Text(stringResource(R.string.forums_community_name_hint)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (domains.size > 1) {
                    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                        MochiTextField(
                            value = chosen,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.forums_community_domain)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            domains.forEach { item ->
                                MochiDropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        open = false
                                        domain = item
                                    },
                                )
                            }
                        }
                    }
                }
                MochiButton(
                    onClick = {
                        viewModel.publish(name, chosen)
                        name = ""
                    },
                    enabled = name.isNotBlank() && chosen.isNotEmpty() && !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    }
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.forums_community_publish))
                }
            }
        }
    }
}

/**
 * Whether every post of the forum is sensitive: readers here see them behind
 * a warning, and the community says so to other networks.
 */
@Composable
internal fun SensitiveSection(viewModel: CommunityViewModel) {
    val sensitive by viewModel.sensitive.collectAsState()
    val busy by viewModel.busy.collectAsState()
    Section(title = stringResource(R.string.forums_content)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = sensitive, enabled = !busy, role = Role.Switch, onValueChange = { viewModel.setSensitive(it) }),
        ) {
            Text(stringResource(R.string.forums_sensitive), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = sensitive, onCheckedChange = null, enabled = !busy)
        }
    }
}
