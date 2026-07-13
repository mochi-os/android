// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.LoadingState
import org.mochios.staff.R
import org.mochios.staff.model.Category
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.dialog.CategoryEditDialog

/**
 * Staff Categories screen. Mirrors web's
 * `apps/staff/web/src/features/categories/categories-page.tsx`:
 *
 *  - Drawer-driven navigation via the parent [StaffLayout]'s [StaffSidebar].
 *  - Top-bar "Add" action (mounted at the StaffLayout level in StaffNavGraph)
 *    opens the create dialog.
 *  - Table-style list of every category with Name / Slug / Parent / Types /
 *    Position / Status columns, plus per-row Edit + Delete buttons.
 *  - Delete confirmation via lib's [ConfirmDialog].
 *
 * The dialog body itself lives in [CategoryEditDialog] (see the shared
 * dialog package) so both create and edit can be opened by the same shell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
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
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CategoriesEvent.Toast -> snackbarHostState.showSnackbar(context.getString(event.messageRes))
                is CategoriesEvent.Error -> {
                    val fallback = context.getString(R.string.staff_categories_toast_save_failed)
                    val msg = event.error.userMessage().ifBlank { fallback }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CategoriesBody(
            state = state,
            onEdit = viewModel::openEdit,
            onDelete = viewModel::askDelete,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Edit / create dialog
    if (state.dialogMode != null) {
        CategoryEditDialog(
            mode = state.dialogMode!!,
            form = state.form,
            categories = state.categories,
            submitting = state.submitting,
            onFormChange = viewModel::setForm,
            onSubmit = viewModel::submit,
            onCancel = viewModel::closeDialog,
        )
    }

    // Delete confirmation
    val deleteTarget = state.deleteTarget
    if (deleteTarget != null) {
        ConfirmDialog(
            title = stringResource(R.string.staff_categories_delete_title),
            message = stringResource(R.string.staff_categories_delete_desc, deleteTarget.name),
            confirmLabel = stringResource(R.string.staff_categories_delete_confirm),
            isDestructive = true,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun CategoriesBody(
    state: CategoriesUiState,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        when {
            state.isLoading && state.categories.isEmpty() -> LoadingState()
            state.categories.isEmpty() -> EmptyState(
                icon = Icons.Default.Category,
                title = stringResource(R.string.staff_categories_empty),
            )
            else -> {
                CategoriesHeader()
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.categories, key = { it.id }) { cat ->
                        CategoryRow(
                            category = cat,
                            categories = state.categories,
                            onEdit = { onEdit(cat) },
                            onDelete = { onDelete(cat) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.staff_categories_col_name),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(2f),
        )
        Text(
            text = stringResource(R.string.staff_categories_col_types),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.staff_categories_col_status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    categories: List<Category>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // Resolve the parent ID to the parent category's name. Fall back to the
    // standard "—" placeholder when the parent is unset OR the lookup misses
    // (parent may have been deleted, or the list page might have been
    // paginated before the parent loaded in earlier ports of this screen).
    val noneLabel = stringResource(R.string.staff_categories_type_none)
    val parentLabel = category.parent?.let { pid ->
        categories.firstOrNull { it.id == pid }?.name ?: noneLabel
    } ?: noneLabel
    val types = when {
        category.digital && category.physical -> stringResource(R.string.staff_categories_type_both)
        category.digital -> stringResource(R.string.staff_categories_type_digital)
        category.physical -> stringResource(R.string.staff_categories_type_physical)
        else -> stringResource(R.string.staff_categories_type_none)
    }
    val statusKey = if (category.active) "active" else "inactive"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(2f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = category.slug,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = parentLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = types, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = category.position.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                StaffStatusBadge(status = statusKey)
            }
        }
        Spacer(modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) {
                Text(stringResource(R.string.staff_categories_action_edit))
            }
            OutlinedButton(onClick = onDelete) {
                Text(stringResource(R.string.staff_categories_action_delete))
            }
            Spacer(modifier = Modifier.width(0.dp))
        }
    }
}
