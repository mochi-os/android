// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.friends

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.HtmlContent
import org.mochios.people.R
import org.mochios.people.model.PersonInformation
import org.mochios.people.model.RelationshipStatus
import org.mochios.people.model.User
import org.mochios.android.R as MochiR

/**
 * Add-friend screen. Mirrors
 * `apps/people/web/src/features/friends/components/add-friend-dialog.tsx`: a
 * debounced search field over a paged list of matched users, whose row action
 * always routes through a profile-preview step (avatar + banner + bio + accent)
 * before the actual invite fires. From the preview the user confirms with
 * "Send invitation" / "Accept invite"; back returns to the list, and back again
 * leaves the screen.
 *
 * @param onBack leaves the screen with the friends list untouched.
 * @param onFriendsChanged leaves the screen after an accepted invite, so the
 *   caller can reload the list it will land on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendScreen(
    onBack: () -> Unit,
    onFriendsChanged: () -> Unit,
    viewModel: AddFriendViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val preview = uiState.preview

    // Back steps out of the preview first, so a mistaken tap on a result costs
    // one press rather than the whole search.
    fun goBack() {
        when {
            preview != null -> viewModel.closePreview()
            uiState.friendsChanged -> onFriendsChanged()
            else -> onBack()
        }
    }

    BackHandler { goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = preview?.targetUser?.name?.takeIf { name -> name.isNotBlank() }
                            ?: stringResource(R.string.people_add_friend),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (preview != null) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        uiState.inviteError?.let { error ->
                            Text(
                                text = error.userMessage(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        PreviewConfirmButton(
                            preview = preview,
                            invited = preview.targetUser.id in uiState.invitedUserIds,
                            adding = uiState.addingUserId == preview.targetUser.id,
                            onAddFriend = viewModel::addFriend,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (preview != null) {
                PreviewBody(
                    preview = preview,
                    onRetry = viewModel::retryPreview,
                )
            } else {
                SearchBody(
                    state = uiState,
                    onQueryChange = viewModel::updateSearchQuery,
                    onRetry = viewModel::retrySearch,
                    onTapResult = viewModel::openPreview,
                )
            }
        }
    }
}

@Composable
private fun SearchBody(
    state: AddFriendUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onTapResult: (User) -> Unit,
) {
    val hasQuery = state.searchQuery.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.people_add_friend_search_placeholder)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !hasQuery -> {
                    EmptyHint(
                        title = stringResource(R.string.people_add_friend_search_start),
                        description = stringResource(R.string.people_add_friend_search_hint),
                    )
                }
                state.searchLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.searchError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.searchError.userMessage(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onRetry) {
                            Text(stringResource(MochiR.string.common_retry))
                        }
                    }
                }
                state.searchResults.isEmpty() -> {
                    EmptyHint(
                        title = stringResource(R.string.people_friends_no_people_found),
                        description = stringResource(R.string.people_friends_try_different_search),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.searchResults, key = { user -> user.id }) { user ->
                            AddFriendRow(
                                user = user,
                                invited = user.id in state.invitedUserIds,
                                pending = state.addingUserId == user.id,
                                onSelect = { onTapResult(user) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFriendRow(
    user: User,
    invited: Boolean,
    pending: Boolean,
    onSelect: () -> Unit,
) {
    val effectiveStatus = if (invited) RelationshipStatus.INVITED else user.relationshipStatus
    val avatarUrl = "/people/${user.id}/-/avatar"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EntityAvatar(
            name = user.name,
            src = avatarUrl,
            seed = user.id,
            size = 40.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.fingerprintHyphens.isNotBlank()) {
                Text(
                    text = user.fingerprintHyphens,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AddFriendActionButton(
            status = effectiveStatus,
            pending = pending,
            onClick = onSelect,
        )
    }
}

@Composable
private fun AddFriendActionButton(
    status: RelationshipStatus,
    pending: Boolean,
    onClick: () -> Unit,
) {
    val enabled = !pending &&
        status != RelationshipStatus.FRIEND &&
        status != RelationshipStatus.INVITED &&
        status != RelationshipStatus.SELF

    when (status) {
        RelationshipStatus.SELF -> {
            OutlinedButton(onClick = {}, enabled = false) {
                Text(stringResource(R.string.people_friends_thats_you))
            }
        }
        RelationshipStatus.FRIEND -> {
            OutlinedButton(onClick = {}, enabled = false) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.people_friends_already_friends))
            }
        }
        RelationshipStatus.INVITED -> {
            OutlinedButton(onClick = {}, enabled = false) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.people_invitations_sent_toast))
            }
        }
        RelationshipStatus.PENDING -> {
            Button(onClick = onClick, enabled = enabled) {
                if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.people_add_friend_accept_invite))
                }
            }
        }
        RelationshipStatus.NONE -> {
            Button(onClick = onClick, enabled = enabled) {
                if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.people_add_friend))
                }
            }
        }
    }
}

@Composable
private fun PreviewBody(
    preview: AddFriendPreview,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            preview.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            preview.error != null && preview.information == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = preview.error.userMessage(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRetry) {
                        Text(stringResource(MochiR.string.common_retry))
                    }
                }
            }
            preview.information != null -> {
                PreviewProfile(
                    user = preview.targetUser,
                    info = preview.information,
                )
            }
        }
    }
}

@Composable
private fun PreviewProfile(
    user: User,
    info: PersonInformation,
) {
    val avatarUrl = avatarUrlFor(info, user)
    val bannerUrl = bannerUrlFor(info, user)
    val accent = info.style.accent
    val displayName = info.name.takeIf { name -> name.isNotBlank() } ?: user.name
    val fingerprint = info.fingerprint.takeIf { value -> value.isNotBlank() }
        ?: user.fingerprintHyphens

    if (bannerUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(bannerUrl)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.people_person_banner_alt, displayName),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f)
                .clip(RoundedCornerShape(8.dp)),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EntityAvatar(
            name = displayName,
            src = avatarUrl,
            seed = info.id.ifBlank { user.id },
            size = 64.dp,
            accent = accent,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fingerprint.isNotBlank()) {
                Text(
                    text = fingerprint,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (info.profile.isNotBlank()) {
        HtmlContent(html = info.profile)
    }
}

@Composable
private fun PreviewConfirmButton(
    preview: AddFriendPreview,
    invited: Boolean,
    adding: Boolean,
    onAddFriend: (User) -> Unit,
) {
    val user = preview.targetUser
    val effectiveStatus = when {
        invited -> RelationshipStatus.INVITED
        else -> user.relationshipStatus
    }
    // Disabled while the details fetch is still in flight so the screen doesn't
    // fire an invite before the user has seen the profile.
    val ready = !preview.isLoading
    val terminal = effectiveStatus == RelationshipStatus.FRIEND ||
        effectiveStatus == RelationshipStatus.INVITED ||
        effectiveStatus == RelationshipStatus.SELF
    val enabled = ready && !adding && !terminal
    val fill = Modifier.fillMaxWidth()

    when (effectiveStatus) {
        RelationshipStatus.SELF -> {
            Button(onClick = {}, enabled = false, modifier = fill) {
                Text(stringResource(R.string.people_friends_thats_you))
            }
        }
        RelationshipStatus.FRIEND -> {
            Button(onClick = {}, enabled = false, modifier = fill) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.people_friends_already_friends))
            }
        }
        RelationshipStatus.INVITED -> {
            Button(onClick = {}, enabled = false, modifier = fill) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.people_invitations_sent_toast))
            }
        }
        RelationshipStatus.PENDING -> {
            Button(
                onClick = { onAddFriend(user) },
                enabled = enabled,
                modifier = fill,
            ) {
                if (adding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.people_add_friend_accept_invite))
                }
            }
        }
        RelationshipStatus.NONE -> {
            Button(
                onClick = { onAddFriend(user) },
                enabled = enabled,
                modifier = fill,
            ) {
                if (adding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.people_common_send_invitation))
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun avatarUrlFor(
    info: PersonInformation,
    fallback: User,
): String? {
    val id = info.id.ifBlank { fallback.id }.ifBlank { return null }
    val version = info.avatar
    return if (version.isBlank()) "/people/$id/-/avatar" else "/people/$id/-/avatar?v=$version"
}

private fun bannerUrlFor(
    info: PersonInformation,
    fallback: User,
): String? {
    if (info.banner.isBlank()) return null
    val id = info.id.ifBlank { fallback.id }.ifBlank { return null }
    return "/people/$id/-/banner?v=${info.banner}"
}
