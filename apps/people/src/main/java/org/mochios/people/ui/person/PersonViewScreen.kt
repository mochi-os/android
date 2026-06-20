// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.ui.components.LoadingState
import org.mochios.people.R
import org.mochios.android.R as MochiR

/**
 * Read-only profile view of a person other than the current user.
 *
 * Banner + avatar + name + fingerprint + bio markdown render at the top; an
 * accent-tinted chip / banner edge applies the person's chosen accent colour.
 * The friendship-state pill drives the primary action set (Add / Accept-Decline
 * / "Friends" badge / "You" badge) and a Message button opens chat via
 * [onMessage].
 *
 * For private profiles where the current user isn't a mutual friend, the
 * body collapses to a single info card; the header (avatar + name) still
 * renders so the result doesn't look broken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonViewScreen(
    onBack: () -> Unit,
    onMessage: (personId: String, personName: String) -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PersonViewEvent.Message -> onMessage(event.personId, event.personName)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.info?.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.people_person_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    LoadingState()
                }
            }
            uiState.error != null && uiState.info == null -> {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    ErrorState(error = uiState.error!!, onRetry = { viewModel.refresh() })
                }
            }
            uiState.info != null -> {
                PersonBody(
                    info = uiState.info!!,
                    friendState = uiState.friendState,
                    isMutating = uiState.isMutating,
                    error = uiState.error,
                    onAddFriend = viewModel::addFriend,
                    onAccept = viewModel::acceptInvite,
                    onDecline = viewModel::declineInvite,
                    onMessage = viewModel::message,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun PersonBody(
    info: org.mochios.people.model.PersonInformation,
    friendState: FriendState,
    isMutating: Boolean,
    error: org.mochios.android.api.MochiError?,
    onAddFriend: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Private profile + not a mutual friend → render avatar + name only.
    val gated = info.privacy == "private" && friendState !is FriendState.Friend &&
        friendState !is FriendState.Self

    val accent = info.style.accent
    val avatarUrl = avatarUrlFor(info)
    val bannerUrl = bannerUrlFor(info)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ---- Banner + avatar overlay ----
        Box(modifier = Modifier.fillMaxWidth()) {
            if (bannerUrl != null && !gated) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(bannerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(
                        R.string.people_person_banner_alt,
                        info.name,
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f)
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                )
            } else {
                // Empty surface so the avatar sits at a consistent height even
                // without a banner. Tint with the accent if there is one.
                Surface(
                    color = parseAccent(accent)?.copy(alpha = 0.12f)
                        ?: MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                ) {}
            }
            EntityAvatar(
                name = info.name,
                src = if (gated) null else avatarUrl,
                seed = info.id,
                size = 96.dp,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 16.dp, y = 48.dp),
            )
        }

        Spacer(Modifier.height(56.dp))

        // ---- Name + fingerprint + pill ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = parseAccent(accent) ?: MaterialTheme.colorScheme.onSurface,
                )
                if (info.fingerprint.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = info.fingerprint,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FriendStatePill(state = friendState)
        }

        Spacer(Modifier.height(16.dp))

        if (gated) {
            // Private profile + non-friend: minimal body.
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.people_person_private),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        // ---- Action buttons ----
        ActionRow(
            friendState = friendState,
            isMutating = isMutating,
            onAddFriend = onAddFriend,
            onAccept = onAccept,
            onDecline = onDecline,
            onMessage = onMessage,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // ---- Bio ----
        if (info.profile.isNotBlank()) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                HtmlContent(html = info.profile)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (error != null) {
            Text(
                text = error.userMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun FriendStatePill(state: FriendState) {
    when (state) {
        FriendState.Friend -> AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.people_person_state_friend)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )
        FriendState.Self -> AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.people_person_state_self)) },
        )
        FriendState.InvitedThem -> AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.people_person_state_invited)) },
        )
        is FriendState.InvitedByThem, FriendState.NotFriend -> Unit
    }
}

@Composable
private fun ActionRow(
    friendState: FriendState,
    isMutating: Boolean,
    onAddFriend: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (friendState) {
            FriendState.Self -> Unit
            FriendState.Friend -> {
                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.people_person_message))
                }
            }
            is FriendState.InvitedByThem -> {
                Button(
                    onClick = onAccept,
                    enabled = !isMutating,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.people_person_accept))
                }
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !isMutating,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.people_person_decline))
                }
            }
            FriendState.InvitedThem -> {
                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.people_person_message))
                }
            }
            FriendState.NotFriend -> {
                Button(
                    onClick = onAddFriend,
                    enabled = !isMutating,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isMutating) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.people_person_add_friend))
                    }
                }
                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.people_person_message))
                }
            }
        }
    }
}

private fun avatarUrlFor(info: org.mochios.people.model.PersonInformation): String? {
    if (info.avatar.isBlank()) return null
    val id = info.id.ifBlank { return null }
    return "/people/$id/-/avatar?v=${info.avatar}"
}

private fun bannerUrlFor(info: org.mochios.people.model.PersonInformation): String? {
    if (info.banner.isBlank()) return null
    val id = info.id.ifBlank { return null }
    return "/people/$id/-/banner?v=${info.banner}"
}

private fun parseAccent(hex: String?): Color? {
    val s = hex?.trim()?.removePrefix("#") ?: return null
    return try {
        when (s.length) {
            6 -> Color(
                red = s.substring(0, 2).toInt(16) / 255f,
                green = s.substring(2, 4).toInt(16) / 255f,
                blue = s.substring(4, 6).toInt(16) / 255f,
            )
            3 -> Color(
                red = (s[0].digitToInt(16) * 17) / 255f,
                green = (s[1].digitToInt(16) * 17) / 255f,
                blue = (s[2].digitToInt(16) * 17) / 255f,
            )
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
