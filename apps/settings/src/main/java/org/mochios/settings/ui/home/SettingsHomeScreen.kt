package org.mochios.settings.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.R as MochiR
import org.mochios.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenNotificationPrefs: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenTokens: () -> Unit,
    onOpenReplication: () -> Unit,
    onOpenSystemReplication: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenSystemStatus: () -> Unit,
    onOpenSystemUsers: () -> Unit,
    onOpenInterests: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenDomains: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenSystemDocuments: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_home_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenAccount),
                    headlineContent = { Text(stringResource(R.string.settings_home_account)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenPreferences),
                    headlineContent = { Text(stringResource(R.string.settings_home_preferences)) },
                    leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenDisplay),
                    headlineContent = { Text(stringResource(R.string.settings_home_display)) },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenInterests),
                    headlineContent = { Text(stringResource(R.string.settings_home_interests)) },
                    leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenAccounts),
                    headlineContent = { Text(stringResource(R.string.settings_home_accounts)) },
                    leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenNotifications),
                    headlineContent = { Text(stringResource(MochiR.string.notifications_open)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenNotificationPrefs),
                    headlineContent = { Text(stringResource(R.string.settings_home_notification_prefs)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenTokens),
                    headlineContent = { Text(stringResource(R.string.settings_home_tokens)) },
                    leadingContent = { Icon(Icons.Default.Key, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenSessions),
                    headlineContent = { Text(stringResource(R.string.settings_home_sessions)) },
                    leadingContent = { Icon(Icons.Default.Devices, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }

            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenDomains),
                    headlineContent = { Text(stringResource(R.string.settings_home_domains)) },
                    leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenReplication),
                    headlineContent = { Text(stringResource(R.string.settings_home_replication)) },
                    leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.settings_home_section_system),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenSystemStatus),
                    headlineContent = { Text(stringResource(R.string.settings_home_system_status)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.MonitorHeart,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenSystemUsers),
                    headlineContent = { Text(stringResource(R.string.settings_home_system_users)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenSystemSettings),
                    headlineContent = { Text(stringResource(R.string.settings_home_system_settings)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.SettingsApplications,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenSystemReplication),
                    headlineContent = { Text(stringResource(R.string.settings_home_system_replication)) },
                    leadingContent = { Icon(Icons.Default.Hub, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenSystemDocuments),
                    headlineContent = { Text(stringResource(R.string.settings_home_system_documents)) },
                    leadingContent = { Icon(Icons.Default.Article, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = { onOpenDocument("privacy") }),
                    headlineContent = { Text(stringResource(R.string.settings_home_privacy)) },
                    leadingContent = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = { onOpenDocument("rules") }),
                    headlineContent = { Text(stringResource(R.string.settings_home_rules)) },
                    leadingContent = { Icon(Icons.Default.Gavel, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = { onOpenDocument("terms") }),
                    headlineContent = { Text(stringResource(R.string.settings_home_terms)) },
                    leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onLogout),
                    headlineContent = { Text(stringResource(R.string.settings_home_logout)) },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
