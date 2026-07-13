// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.mochios.android.R
import org.mochios.android.notifications.NotificationsUnreadStore

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationsBellEntryPoint {
    fun store(): NotificationsUnreadStore
}

/**
 * Bell icon + unread badge for use in every feature's TopAppBar. The
 * underlying [NotificationsUnreadStore] is a `@Singleton`, so all four
 * features' bells consume the same counter.
 *
 * Pass an `onClick` that navigates the host's NavController to
 * `MainActivity.ROUTE_NOTIFICATIONS`.
 */
@Composable
fun NotificationBell(onClick: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NotificationsBellEntryPoint::class.java,
        ).store()
    }
    LaunchedEffect(Unit) { store.ensureStarted() }
    val count by store.count.collectAsState()

    Box {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = stringResource(R.string.notifications_open),
            )
        }
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        }
    }
}
