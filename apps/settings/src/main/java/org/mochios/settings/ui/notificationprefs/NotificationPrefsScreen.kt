// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownField
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.DestinationsAvailable
import org.mochios.settings.api.NotifCategory
import org.mochios.settings.api.NotifTopic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPrefsScreen(
    onBack: () -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (String) -> Unit,
    savedSignal: Long = 0L,
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
    val snack = sent?.let { count ->
        if (count == 0) {
            stringResource(R.string.notifprefs_no_destinations_configured)
        } else {
            pluralStringResource(R.plurals.notifprefs_test_sent, count, count)
        }
    }

    var deleting by remember { mutableStateOf<NotifCategory?>(null) }

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(savedSignal) {
        if (savedSignal != 0L) {
            viewModel.refresh()
        }
    }

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
                actions = {
                    if (state.tab == NotifTab.CATEGORIES) {
                        MochiIconButton(onClick = onAddCategory) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.notifprefs_add_category),
                            )
                        }
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
                        onEdit = { category -> onEditCategory(category.id) },
                        onDelete = { category -> deleting = category },
                        onTest = { category -> viewModel.testCategory(category) },
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
    onEdit: (NotifCategory) -> Unit,
    onDelete: (NotifCategory) -> Unit,
    onTest: (NotifCategory) -> Unit,
) {
    val visible = categories.filter { category -> category.id != "0" }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(visible, key = { category -> category.id }) { category ->
            CategoryCard(
                category = category,
                available = available,
                onEdit = { onEdit(category) },
                onDelete = { onDelete(category) },
                onTest = { onTest(category) },
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
) {
    var expanded by remember { mutableStateOf(false) }
    val options = destinationOptions(available)
    val checkedKeys = category.destinations.map { row -> row.type to row.target }.toSet()
    val selected = options.filter { (row, _) -> (row.type to row.target) in checkedKeys }
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (category.default == 1) {
                        Spacer(Modifier.size(8.dp))
                        DefaultBadge()
                    }
                }
                MochiTextButton(onClick = onTest) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.notifprefs_test))
                }
                MochiIconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.notifprefs_edit))
                }
                MochiIconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.notifprefs_delete),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (selected.isEmpty()) {
                            Modifier
                        } else {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { expanded = !expanded }
                        }
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected.isEmpty()) {
                    Text(
                        text = stringResource(R.string.notifprefs_no_destinations),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.notifprefs_destinations) +
                            " \u00b7 ${selected.size}/${options.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) {
                for ((_, label) in selected) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Marks the category new topics are filed under. */
@Composable
private fun DefaultBadge() {
    Text(
        text = stringResource(MochiR.string.settings_theme_default),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
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
                MochiDropdownField(
                    value = others.firstOrNull { other -> other.id == target }?.label.orEmpty(),
                    expanded = menu,
                    onExpandedChange = { open -> menu = open },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (other in others) {
                        MochiDropdownMenuItem(
                            text = { Text(other.label) },
                            onClick = {
                                target = other.id
                                menu = false
                            },
                            selected = other.id == target,
                        )
                    }
                }
            }
        },
        confirmText = stringResource(R.string.notifprefs_delete),
        onConfirm = { onConfirm(target) },
        destructive = true,
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
