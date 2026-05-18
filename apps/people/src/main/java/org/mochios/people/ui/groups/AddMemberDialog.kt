package org.mochios.people.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.people.R
import org.mochios.people.model.GroupMemberType

/**
 * Add-member dialog: debounced search across local users + groups.
 *
 * Mirrors the web `MemberDialog` two-step flow: tap a result to select it
 * (highlight only, no commit), then confirm in a second step showing the
 * picked user/group with an "Add to group" action. The search list keeps
 * its query and results in view-model state, so the Back button returns
 * to exactly where the user left off.
 */
@Composable
fun AddMemberDialog(
    state: GroupDetailViewModel.UiState,
    onSearch: (String) -> Unit,
    onPick: (GroupDetailViewModel.SearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedItem by remember { mutableStateOf<GroupDetailViewModel.SearchResult?>(null) }
    val confirming = selectedItem != null

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = {
            Text(
                if (confirming) stringResource(R.string.people_groups_add_member_confirm_title)
                else stringResource(R.string.people_member_add)
            )
        },
        text = {
            val picked = selectedItem
            if (picked != null) {
                ConfirmRow(picked)
            } else {
                SearchStep(
                    state = state,
                    onSearch = onSearch,
                    onSelect = { selectedItem = it },
                )
            }
        },
        confirmButton = {
            val picked = selectedItem
            if (picked != null) {
                TextButton(
                    onClick = { onPick(picked) },
                    enabled = !state.isSaving,
                ) {
                    Text(stringResource(R.string.people_groups_add_member_confirm_action))
                }
            } else {
                TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                    Text(stringResource(R.string.people_common_close))
                }
            }
        },
        dismissButton = {
            if (confirming) {
                TextButton(
                    onClick = { selectedItem = null },
                    enabled = !state.isSaving,
                ) {
                    Text(stringResource(R.string.people_groups_add_member_confirm_back))
                }
            }
        },
    )
}

@Composable
private fun SearchStep(
    state: GroupDetailViewModel.UiState,
    onSearch: (String) -> Unit,
    onSelect: (GroupDetailViewModel.SearchResult) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            placeholder = { Text(stringResource(R.string.people_member_search_placeholder)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        when {
            state.searchQuery.isBlank() -> {
                Text(
                    text = stringResource(R.string.people_member_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.searchLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.people_friends_searching))
                }
            }

            state.searchError != null -> {
                Text(
                    text = state.searchError.userMessage(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.searchResults.isEmpty() -> {
                Text(
                    text = stringResource(R.string.people_friends_no_people_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.searchResults, key = { "${it.type}:${it.id}" }) { result ->
                        SearchRow(
                            result = result,
                            enabled = !state.isSaving,
                            onClick = { onSelect(result) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(result: GroupDetailViewModel.SearchResult) {
    val typePillText = stringResource(
        when (result.type) {
            GroupMemberType.USER -> R.string.people_member_type_user
            GroupMemberType.GROUP -> R.string.people_member_type_group
        }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (result.type) {
            GroupMemberType.USER -> EntityAvatar(
                name = result.name,
                seed = result.id,
                size = 40.dp,
            )
            GroupMemberType.GROUP -> Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = result.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(8.dp))
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(typePillText) },
            leadingIcon = {
                Icon(
                    imageVector = when (result.type) {
                        GroupMemberType.USER -> Icons.Default.Person
                        GroupMemberType.GROUP -> Icons.Default.Group
                    },
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun SearchRow(
    result: GroupDetailViewModel.SearchResult,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val typePillText = stringResource(
        when (result.type) {
            GroupMemberType.USER -> R.string.people_member_type_user
            GroupMemberType.GROUP -> R.string.people_member_type_group
        }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (result.type) {
            GroupMemberType.USER -> EntityAvatar(
                name = result.name,
                seed = result.id,
                size = 32.dp,
            )
            GroupMemberType.GROUP -> Box(
                modifier = Modifier
                    .size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = result.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(8.dp))
        AssistChip(
            onClick = onClick,
            enabled = enabled,
            label = { Text(typePillText) },
            leadingIcon = {
                Icon(
                    imageVector = when (result.type) {
                        GroupMemberType.USER -> Icons.Default.Person
                        GroupMemberType.GROUP -> Icons.Default.Group
                    },
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
    }
}
