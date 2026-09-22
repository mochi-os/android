// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.mochios.android.ui.components.EntityListRow
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.people.R
import org.mochios.people.model.PersonInformation
import org.mochios.people.model.User
import androidx.compose.material.icons.outlined.PersonAddAlt
import org.mochios.android.R as MochiR

/**
 * Adds a contact: a row for one typed in by hand, then the directory search
 * for people already on Mochi, each hit offering to save it as a contact and
 * to invite it as a friend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    onBack: () -> Unit,
    onNewContact: () -> Unit,
    onContactsChanged: () -> Unit,
    onLinked: () -> Unit = onContactsChanged,
    viewModel: AddContactViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val preview = uiState.preview

    LaunchedEffect(uiState.linked) {
        if (uiState.linked) onLinked()
    }

    // Back steps out of the preview first, so a mistaken tap on a result costs
    // one press rather than the whole search.
    fun goBack() {
        when {
            preview != null -> viewModel.closePreview()
            uiState.contactsChanged -> onContactsChanged()
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
                            ?: stringResource(R.string.people_add_contact_title),
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = { goBack() }) {
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
                        uiState.actionError?.let { error ->
                            Text(
                                text = error.userMessage(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        PreviewActions(
                            state = viewModel.state(preview.targetUser),
                            busy = uiState.busyUserId == preview.targetUser.id,
                            ready = !preview.isLoading,
                            onAdd = { viewModel.addToContacts(preview.targetUser) },
                            onInvite = { viewModel.invite(preview.targetUser) },
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
                PreviewBody(preview = preview, onRetry = viewModel::retryPreview)
            } else {
                SearchBody(
                    state = uiState,
                    stateOf = viewModel::state,
                    onQueryChange = viewModel::updateSearchQuery,
                    onRetry = viewModel::retrySearch,
                    onTapResult = viewModel::openPreview,
                    onAct = { user ->
                        when (viewModel.state(user)) {
                            AddContactState.NONE -> viewModel.addToContacts(user)
                            AddContactState.IN_CONTACTS, AddContactState.PENDING ->
                                viewModel.invite(user)
                            else -> Unit
                        }
                    },
                    onNewContact = onNewContact,
                )
            }
        }
    }
}

@Composable
private fun SearchBody(
    state: AddContactUiState,
    stateOf: (User) -> AddContactState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onTapResult: (User) -> Unit,
    onAct: (User) -> Unit,
    onNewContact: () -> Unit,
) {
    val hasQuery = state.searchQuery.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        EntityListRow(
            name = stringResource(R.string.people_contact_new_title),
            seed = "new-contact",
            icon = Icons.Outlined.PersonAddAlt,
            onClick = onNewContact,
        )
        Spacer(modifier = Modifier.height(12.dp))
        MochiTextField(
            value = state.searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.people_add_contact_search_placeholder)) },
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
                    EmptyHint(title = stringResource(R.string.people_add_contact_search_start))
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
                    InlineErrorState(error = state.searchError, onRetry = onRetry)
                }
                state.searchResults.isEmpty() -> {
                    EmptyHint(
                        title = stringResource(R.string.people_contacts_no_people_found),
                        description = stringResource(R.string.people_contacts_try_different_search),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.searchResults, key = { user -> user.id }) { user ->
                            AddContactRow(
                                user = user,
                                state = stateOf(user),
                                busy = state.busyUserId == user.id,
                                onSelect = { onTapResult(user) },
                                onAct = { onAct(user) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddContactRow(
    user: User,
    state: AddContactState,
    busy: Boolean,
    onSelect: () -> Unit,
    onAct: () -> Unit,
) {
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
        // One button per row — the row's own tap opens the profile, where both
        // actions are offered side by side.
        RowActionButton(state = state, busy = busy, onClick = onAct)
    }
}

@Composable
private fun RowActionButton(
    state: AddContactState,
    busy: Boolean,
    onClick: () -> Unit,
) {
    when (state) {
        AddContactState.SELF -> MochiOutlinedButton(onClick = {}, enabled = false) {
            Text(stringResource(R.string.people_contacts_thats_you))
        }
        AddContactState.FRIEND -> MochiOutlinedButton(onClick = {}, enabled = false) {
            Text(stringResource(R.string.people_contacts_friend))
        }
        AddContactState.INVITED -> MochiOutlinedButton(onClick = {}, enabled = false) {
            Text(stringResource(R.string.people_contacts_invited))
        }
        AddContactState.PENDING -> MochiButton(onClick = onClick, enabled = !busy) {
            ButtonContent(busy = busy, icon = Icons.Default.Check) {
                Text(stringResource(R.string.people_contacts_accept))
            }
        }
        AddContactState.IN_CONTACTS -> MochiOutlinedButton(onClick = onClick, enabled = !busy) {
            Text(stringResource(R.string.people_contacts_invite))
        }
        AddContactState.NONE -> MochiButton(onClick = onClick, enabled = !busy) {
            ButtonContent(busy = busy, icon = Icons.Default.PersonAdd) {
                Text(stringResource(R.string.people_add_contact_add))
            }
        }
    }
}

@Composable
private fun PreviewActions(
    state: AddContactState,
    busy: Boolean,
    ready: Boolean,
    onAdd: () -> Unit,
    onInvite: () -> Unit,
) {
    val fill = Modifier.fillMaxWidth()
    when (state) {
        AddContactState.SELF -> MochiButton(onClick = {}, enabled = false, modifier = fill) {
            Text(stringResource(R.string.people_contacts_thats_you))
        }
        AddContactState.FRIEND -> MochiButton(onClick = {}, enabled = false, modifier = fill) {
            Text(stringResource(R.string.people_contacts_friend))
        }
        AddContactState.INVITED -> MochiButton(onClick = {}, enabled = false, modifier = fill) {
            Text(stringResource(R.string.people_contacts_invited))
        }
        AddContactState.PENDING -> MochiButton(
            onClick = onInvite,
            enabled = ready && !busy,
            modifier = fill,
        ) {
            ButtonContent(busy = busy, icon = Icons.Default.Check) {
                Text(stringResource(R.string.people_contacts_accept))
            }
        }
        AddContactState.IN_CONTACTS -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MochiOutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.people_add_contact_in_contacts))
            }
            MochiButton(
                onClick = onInvite,
                enabled = ready && !busy,
                modifier = Modifier.weight(1f),
            ) {
                ButtonContent(busy = busy, icon = Icons.AutoMirrored.Filled.Send) {
                    Text(stringResource(R.string.people_contacts_invite))
                }
            }
        }
        AddContactState.NONE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MochiButton(
                onClick = onAdd,
                enabled = ready && !busy,
                modifier = Modifier.weight(1f),
            ) {
                ButtonContent(busy = busy, icon = Icons.Default.PersonAdd) {
                    Text(stringResource(R.string.people_add_contact_add))
                }
            }
            MochiOutlinedButton(
                onClick = onInvite,
                enabled = ready && !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.people_contacts_invite))
            }
        }
    }
}

/** A button's label behind its icon, or a spinner while the call is in flight. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ButtonContent(
    busy: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: @Composable () -> Unit,
) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(ButtonDefaults.IconSize),
            strokeWidth = 2.dp,
        )
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    }
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    label()
}

@Composable
private fun PreviewBody(
    preview: AddContactPreview,
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
                InlineErrorState(error = preview.error, onRetry = onRetry)
            }
            preview.information != null -> {
                PreviewProfile(user = preview.targetUser, info = preview.information)
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
private fun EmptyHint(title: String, description: String? = null) {
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
        if (description != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
