// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.ui.components.CountBadge
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.NotificationCard
import org.mochios.android.ui.components.NotificationCategoryItems
import org.mochios.home.R
import org.mochios.android.R as MochiR

/** The user's avatar as the people app serves it, or none before the identity is known. */
private fun avatar(identity: String): String? =
    if (identity.isBlank()) null else "/people/$identity/-/avatar"

/** The size of the avatar in the top bar, and beside the name in the menu. */
private val AVATAR = 32.dp

/**
 * The button that opens the user menu, as on the web: the user's avatar with
 * the unread count on its corner.
 */
@Composable
internal fun UserButton(name: String, identity: String, count: Int, onClick: () -> Unit) {
    val label = stringResource(R.string.home_menu_open)
    val shown = name.ifBlank { stringResource(R.string.home_menu_user) }
    Box {
        MochiIconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
            EntityAvatar(name = shown, src = avatar(identity), seed = identity.ifBlank { null }, size = AVATAR)
        }
        CountBadge(
            count = count,
            container = MaterialTheme.colorScheme.error,
            content = MaterialTheme.colorScheme.onError,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/**
 * The user menu, as the web's: who is signed in with a way to log out, then
 * the unread notifications with a way to mark them all read and to see every
 * one. Tapping a notification reads it and opens what it is about; each has a
 * picker for its category.
 */
@Composable
internal fun UserMenu(
    state: HomeMenuState,
    onLogout: () -> Unit,
    onReadAll: () -> Unit,
    onViewAll: () -> Unit,
    onOpen: (MochiNotification) -> Unit,
    onPick: (MochiNotification) -> Unit,
    onClosePicker: () -> Unit,
    onCategorise: (MochiNotification, String?) -> Unit,
    onManageCategories: () -> Unit,
) {
    val format = LocalFormat.current
    val count = state.unread.size
    // Nothing unread: the header says so and the empty list below it goes.
    val empty = !state.loading && count == 0
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val name = state.name.ifBlank { stringResource(R.string.home_menu_user) }
            EntityAvatar(name = name, src = avatar(state.identity), seed = state.identity.ifBlank { null }, size = AVATAR)
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            MochiIconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(MochiR.string.common_logout))
            }
        }
        // The heading starts where the name above it does: past the row's
        // padding, the avatar and the gap after it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(start = 16.dp + AVATAR + 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (empty) {
                    stringResource(R.string.home_menu_empty)
                } else {
                    // The count after the word, as the web writes it.
                    stringResource(MochiR.string.notifications_title) +
                        if (count > 0) " (${format.formatNumber(count)})" else ""
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (empty) FontWeight.Normal else FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            if (count > 0) {
                MochiIconButton(onClick = onReadAll) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(MochiR.string.notifications_mark_all_read))
                }
            }
            MochiIconButton(onClick = onViewAll) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.home_menu_view_all))
            }
        }
        state.error?.let { error ->
            Text(
                text = error.userMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        when {
            empty -> Unit
            state.loading && count == 0 -> Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.unread, key = { it.id }) { notification ->
                    NotificationCard(notification = notification, onClick = { onOpen(notification) }) {
                        CategoryButton(
                            notification = notification,
                            picker = state.picker?.takeIf { it.notification == notification.id },
                            onPick = onPick,
                            onClose = onClosePicker,
                            onCategorise = onCategorise,
                            onManage = onManageCategories,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One notification's category picker, as the web menu's: it loads the
 * categories and the notification's topic as it opens, says so when the
 * server holds no topic for it yet, and links to where categories are kept.
 */
@Composable
private fun CategoryButton(
    notification: MochiNotification,
    picker: CategoryPicker?,
    onPick: (MochiNotification) -> Unit,
    onClose: () -> Unit,
    onCategorise: (MochiNotification, String?) -> Unit,
    onManage: () -> Unit,
) {
    Box {
        MochiIconButton(onClick = { onPick(notification) }) {
            Icon(Icons.Default.Tune, contentDescription = stringResource(MochiR.string.notifications_change_category))
        }
        MochiDropdownMenu(expanded = picker != null, onDismissRequest = onClose) {
            val categories = picker?.categories
            val topic = picker?.topic
            when {
                picker == null -> Unit
                categories == null -> MochiDropdownMenuItem(
                    text = { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) },
                    onClick = {},
                    enabled = false,
                )
                picker.error != null -> MochiDropdownMenuItem(
                    text = { Text(picker.error.userMessage()) },
                    onClick = {},
                    enabled = false,
                )
                topic == null -> MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.home_menu_no_topic)) },
                    onClick = {},
                    enabled = false,
                )
                else -> NotificationCategoryItems(categories = categories, current = topic.category) { category ->
                    onCategorise(notification, category)
                }
            }
            HorizontalDivider()
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_manage_categories)) },
                onClick = onManage,
            )
        }
    }
}
