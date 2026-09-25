// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.categories

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.staff.R
import org.mochios.staff.model.Category
import org.mochios.staff.navigation.StaffApp
import org.mochios.staff.ui.components.StaffStatusBadge

/**
 * Port of `apps/staff/web/src/features/categories/categories-page.tsx`; the Add
 * action is mounted at the route level in StaffNavGraph, and both Add and Edit
 * open [CategoryFormScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    navController: NavController,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    // Categories surfaces are admin-gated server-side, but every staff role
    // can view the list. The "Add" topbar action is mounted at the route
    // level (see StaffNavGraph) so we don't re-wire it here; future
    // role-gated per-row affordances should read LocalStaffMe.current.
    @Suppress("unused")
    val me = org.mochios.staff.ui.components.LocalStaffMe.current

    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CategoriesEvent.Toast -> snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                is CategoriesEvent.Error -> {
                    val fallback = resources.getString(R.string.staff_categories_toast_save_failed)
                    val msg = event.error.userMessage().ifBlank { fallback }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CategoriesBody(
            state = state,
            onEdit = { category -> navController.navigate(StaffApp.categoryEdit(category.id)) },
            onDelete = viewModel::askDelete,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Delete confirmation
    val deleteTarget = state.deleteTarget
    if (deleteTarget != null) {
        MochiAlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = stringResource(R.string.staff_categories_delete_title),
            text = stringResource(R.string.staff_categories_delete_desc, deleteTarget.name),
            confirmText = stringResource(R.string.staff_categories_delete_confirm),
            onConfirm = viewModel::confirmDelete,
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

@Composable
private fun CategoriesBody(
    state: CategoriesUiState,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    when {
        state.isLoading && state.categories.isEmpty() -> LoadingState()
        state.categories.isEmpty() -> EmptyState(
            icon = Icons.Default.Category,
            title = stringResource(R.string.staff_categories_empty),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.categories, key = { category -> category.id }) { category ->
                CategoryCard(
                    category = category,
                    categories = state.categories,
                    onEdit = { onEdit(category) },
                    onDelete = { onDelete(category) },
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    categories: List<Category>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val parentName = category.parent?.let { parentId ->
        categories.firstOrNull { candidate -> candidate.id == parentId }?.name
    }
    val types = when {
        category.digital && category.physical -> stringResource(R.string.staff_categories_type_both)
        category.digital -> stringResource(R.string.staff_categories_type_digital)
        category.physical -> stringResource(R.string.staff_categories_type_physical)
        else -> stringResource(R.string.staff_categories_type_none)
    }
    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = category.slug,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StaffStatusBadge(
                    status = if (category.active) "active" else "inactive",
                    modifier = Modifier.padding(start = 8.dp),
                )
                CategoryMenu(onEdit = onEdit, onDelete = onDelete)
            }
            Spacer(Modifier.height(8.dp))
            if (parentName != null) {
                CategoryField(
                    label = stringResource(R.string.staff_categories_dialog_parent),
                    value = parentName,
                )
            }
            CategoryField(
                label = stringResource(R.string.staff_categories_col_types),
                value = types,
            )
            CategoryField(
                label = stringResource(R.string.staff_categories_dialog_position),
                value = category.position.toString(),
            )
        }
    }
}

@Composable
private fun CategoryField(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .width(80.dp)
                .padding(top = 2.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MochiIconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(MochiR.string.common_more_options),
            )
        }
        MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.staff_categories_action_edit)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            )
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.staff_categories_action_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                destructive = true,
            )
        }
    }
}
