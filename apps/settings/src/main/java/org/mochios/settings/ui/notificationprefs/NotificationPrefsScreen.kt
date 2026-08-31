// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.android.ui.components.MochiTextField
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.DestinationRow
import org.mochios.settings.api.DestinationsAvailable
import org.mochios.settings.api.NotifCategory
import org.mochios.settings.api.NotifTopic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPrefsScreen(
    onBack: () -> Unit,
    viewModel: NotificationPrefsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    // pluralStringResource is @Composable, so the count is held in state and
    // the sentence is built during composition rather than in the collector.
    var sent by remember { mutableStateOf<Int?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        scope.launch {
            viewModel.testSent.collect { sent = it }
        }
    }
    val snack = sent?.let { pluralStringResource(R.plurals.notifprefs_test_sent, it, it) }

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NotifCategory?>(null) }
    var deleting by remember { mutableStateOf<NotifCategory?>(null) }

    val snackbar = remember { SnackbarHostState() }

    // An error while content is on screen is shown over it rather than

    // replacing it: the full-screen arm below only fires when there is

    // nothing to show, and nothing on it can reach a refresh to clear it.

    LaunchedEffect(state.error) {

        val failure = state.error

        if (failure != null && (state.categories.isNotEmpty() || state.topics.isNotEmpty())) {

            snackbar.showSnackbar(failure.userMessage())

            viewModel.clearError()

        }

    }

    Scaffold(

        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifprefs_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                MochiTabRow(
                    tabs = listOf(
                        MochiTab(stringResource(R.string.notifprefs_tab_categories)),
                        MochiTab(stringResource(R.string.notifprefs_tab_topics)),
                    ),
                    selectedIndex = state.tab.ordinal,
                    onSelect = { index -> viewModel.setTab(NotifTab.entries[index]) },
                )
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    state.error != null && state.categories.isEmpty() && state.topics.isEmpty() -> ErrorState(
                        error = state.error!!,
                        onRetry = {
                            viewModel.refresh()
                            if (state.tab == NotifTab.TOPICS) {
                                viewModel.loadTopics()
                            }
                        },
                    )
                    state.tab == NotifTab.CATEGORIES -> CategoriesList(
                        categories = state.categories,
                        available = state.available,
                        onAdd = { creating = true },
                        onEdit = { editing = it },
                        onDelete = { deleting = it },
                        onTest = { viewModel.testCategory(it) },
                        onToggleDest = { cat, row, checked ->
                            viewModel.toggleDestination(cat, row, checked)
                        },
                    )
                    else -> TopicsList(
                        topics = state.topics,
                        categories = state.categories,
                        onSetCategory = { topic, id -> viewModel.setTopicCategory(topic, id) },
                        onRemove = { viewModel.removeTopic(it) },
                    )
                }
            }
            if (snack != null) {
                SnackBanner(snack) { sent = null }
            }
        }
    }

    if (creating) {
        CategoryNameDialog(
            initial = "",
            title = stringResource(R.string.notifprefs_new_category),
            onDismiss = { creating = false },
            onSave = { name ->
                viewModel.createCategory(name)
                creating = false
            },
        )
    }
    editing?.let { cat ->
        CategoryNameDialog(
            initial = cat.label,
            title = stringResource(R.string.notifprefs_edit_category),
            onDismiss = { editing = null },
            onSave = { name ->
                viewModel.renameCategory(cat, name)
                editing = null
            },
        )
    }
    deleting?.let { cat ->
        val others = state.categories.filter { it.id != cat.id }
        DeleteCategoryDialog(
            category = cat,
            others = others,
            onDismiss = { deleting = null },
            onConfirm = { reassignTo ->
                viewModel.deleteCategory(cat.id, reassignTo)
                deleting = null
            },
        )
    }
}

@Composable
private fun CategoriesList(
    categories: List<NotifCategory>,
    available: DestinationsAvailable,
    onAdd: () -> Unit,
    onEdit: (NotifCategory) -> Unit,
    onDelete: (NotifCategory) -> Unit,
    onTest: (NotifCategory) -> Unit,
    onToggleDest: (NotifCategory, DestinationRow, Boolean) -> Unit,
) {
    val visible = categories.filter { it.id != "0" }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("add") {
            MochiOutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.notifprefs_add_category))
            }
        }
        items(visible, key = { it.id }) { category ->
            CategoryCard(
                category = category,
                available = available,
                onEdit = { onEdit(category) },
                onDelete = { onDelete(category) },
                onTest = { onTest(category) },
                onToggleDest = { row, checked -> onToggleDest(category, row, checked) },
            )
        }
    }
}

@Composable
private fun CategoryCard(
    category: NotifCategory,
    available: DestinationsAvailable,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onToggleDest: (DestinationRow, Boolean) -> Unit,
) {
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                MochiIconButton(onClick = onTest) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.notifprefs_test))
                }
                MochiIconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.notifprefs_edit))
                }
                MochiIconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.notifprefs_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.notifprefs_destinations),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DestinationRows(
                category = category,
                available = available,
                onToggle = onToggleDest,
            )
        }
    }
}

@Composable
private fun DestinationRows(
    category: NotifCategory,
    available: DestinationsAvailable,
    onToggle: (DestinationRow, Boolean) -> Unit,
) {
    val checked = category.destinations.map { it.type to it.target }.toSet()

    @Composable
    fun destination(row: DestinationRow, label: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = (row.type to row.target) in checked,
                onCheckedChange = { onToggle(row, it) },
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }

    // Every destination is one row named for what it is: the browser's bell, a
    // device's in-app list ("S24U app"), the push account registered from a
    // device ("S24U push"), a push account bound to no device, or a feed. One
    // flat list sorted by name, which also keeps a device's rows together.
    val deviceFallback = stringResource(R.string.notifprefs_dest_device)
    val bound = available.devices.map { it.id }.toSet()
    val rows = buildList {
        add(DestinationRow(type = "web", target = "") to stringResource(R.string.notifprefs_dest_web))
        for (device in available.devices) {
            val name = device.label.ifBlank { deviceFallback }
            add(
                DestinationRow(type = "device", target = device.id) to
                    stringResource(R.string.notifprefs_device_app, name)
            )
            for (acc in available.accounts) {
                if (acc.device == device.id) {
                    add(
                        DestinationRow(type = "account", target = acc.id) to
                            stringResource(R.string.notifprefs_device_push, name)
                    )
                }
            }
        }
        for (acc in available.accounts) {
            if (acc.device.isNotBlank() && acc.device in bound) continue
            val name = if (acc.label.isNotBlank()) acc.label else if (acc.identifier.isNotBlank()) acc.identifier else acc.type
            // A push account bound to no device names its transport, so two
            // that share a phone's name can still be told apart.
            val push = acc.type == "browser" || acc.type == "unifiedpush" || acc.type == "fcm"
            add(DestinationRow(type = "account", target = acc.id) to (if (push && name != acc.type) "$name · ${acc.type}" else name))
        }
        for (feed in available.feeds) {
            add(DestinationRow(type = "rss", target = feed.id) to feed.name)
        }
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
    for ((row, label) in rows) destination(row, label)
}

@Composable
private fun TopicsList(
    topics: List<NotifTopic>,
    categories: List<NotifCategory>,
    onSetCategory: (NotifTopic, String?) -> Unit,
    onRemove: (NotifTopic) -> Unit,
) {
    if (topics.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.notifprefs_topics_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(topics, key = { "${it.app}/${it.topic}/${it.`object`}" }) { topic ->
            TopicRow(topic = topic, categories = categories, onSetCategory = onSetCategory, onRemove = onRemove)
        }
    }
}

@Composable
private fun TopicRow(
    topic: NotifTopic,
    categories: List<NotifCategory>,
    onSetCategory: (NotifTopic, String?) -> Unit,
    onRemove: (NotifTopic) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.label.ifBlank { topic.topic },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (topic.objectName.isNotBlank()) {
                    Text(
                        topic.objectName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (topic.appName.isNotBlank()) {
                    Text(
                        topic.appName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                MochiOutlinedButton(onClick = { menu = true }) {
                    val current = categories.firstOrNull { it.id == topic.category }
                    Text(current?.label ?: stringResource(R.string.notifprefs_unassigned))
                }
                MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.notifprefs_unassigned)) },
                        onClick = {
                            menu = false
                            onSetCategory(topic, null)
                        },
                        selected = topic.category == null,
                    )
                    for (cat in categories) {
                        MochiDropdownMenuItem(
                            text = { Text(cat.label) },
                            onClick = {
                                menu = false
                                onSetCategory(topic, cat.id)
                            },
                            selected = topic.category == cat.id,
                        )
                    }
                }
            }
            MochiIconButton(onClick = { onRemove(topic) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.notifprefs_remove))
            }
        }
    }
}

@Composable
private fun CategoryNameDialog(
    initial: String,
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            MochiTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.notifprefs_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmText = stringResource(MochiR.string.common_save),
        onConfirm = { onSave(name) },
        confirmEnabled = name.trim().isNotEmpty(),
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

@Composable
private fun DeleteCategoryDialog(
    category: NotifCategory,
    others: List<NotifCategory>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val preferred = others.firstOrNull { it.default == 1 } ?: others.firstOrNull { it.id != "0" } ?: others.firstOrNull()
    var target by remember { mutableStateOf(preferred?.id ?: "0") }
    var menu by remember { mutableStateOf(false) }
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.notifprefs_delete_title, category.label),
        content = {
            Column {
                Text(stringResource(R.string.notifprefs_reassign_label))
                Spacer(Modifier.height(8.dp))
                Box {
                    MochiOutlinedButton(onClick = { menu = true }) {
                        val cur = others.firstOrNull { it.id == target }
                        Text(cur?.label ?: "")
                    }
                    MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        for (c in others) {
                            MochiDropdownMenuItem(
                                text = { Text(c.label) },
                                onClick = {
                                    target = c.id
                                    menu = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmText = stringResource(R.string.notifprefs_delete),
        onConfirm = { onConfirm(target) },
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

@Composable
private fun SnackBanner(message: String, onDismiss: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(message) {
        kotlinx.coroutines.delay(3000)
        onDismiss()
    }
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        MochiCard(colors = CardDefaults.elevatedCardColors()) {
            Text(message, modifier = Modifier.padding(12.dp))
        }
    }
}
