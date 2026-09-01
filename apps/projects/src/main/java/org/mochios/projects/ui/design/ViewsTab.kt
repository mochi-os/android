// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.ClassListItem
import org.mochios.android.ui.components.ViewFieldOption
import org.mochios.android.ui.components.ViewListItem
import org.mochios.android.ui.components.ViewListLabels
import org.mochios.android.ui.components.ViewListTab
import org.mochios.projects.R
import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectField
import org.mochios.projects.model.ProjectView

@Composable
fun ViewsTab(
    views: List<ProjectView>,
    classes: List<ProjectClass>,
    fields: Map<String, List<ProjectField>>,
    viewModel: DesignViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val projectDetails = uiState.projectDetails
    val context = LocalContext.current

    ViewListTab(
        views = views.map { view -> view.toListItem() },
        classes = classes.map { cls -> ClassListItem(cls.id, cls.name, cls.rank) },
        fields = fields.mapValues { (_, classFields) ->
            classFields.map { field ->
                ViewFieldOption(field.id, field.name, field.fieldtype, field.isSortable)
            }
        },
        labels = ViewListLabels(
            addAction = stringResource(R.string.projects_views_add),
            empty = stringResource(R.string.projects_views_empty),
            emptySubtitle = stringResource(R.string.projects_views_empty_subtitle),
            addDialogTitle = stringResource(R.string.projects_views_add_dialog_title),
            editDialogTitle = stringResource(R.string.projects_views_edit_dialog_title),
            deleteTitle = stringResource(R.string.projects_views_delete_title),
            deleteMessage = { viewName ->
                context.getString(R.string.projects_views_delete_message, viewName)
            },
            byField = { fieldName -> context.getString(R.string.projects_views_by, fieldName) },
            sortedBy = { direction -> context.getString(R.string.projects_views_sorted, direction) },
            typeBoard = stringResource(R.string.projects_views_type_board),
            typeList = stringResource(R.string.projects_views_type_list),
            moveUp = stringResource(R.string.projects_views_move_up),
            moveDown = stringResource(R.string.projects_views_move_down),
            nameLabel = stringResource(R.string.projects_class_name),
            typeLabel = stringResource(R.string.projects_views_type),
            columnsField = stringResource(R.string.projects_views_columns_field),
            rowsField = stringResource(R.string.projects_views_rows_field),
            borderField = stringResource(R.string.projects_views_border_field),
            filter = stringResource(R.string.projects_views_filter),
            sortBy = stringResource(R.string.projects_views_sort_by),
            direction = stringResource(R.string.projects_views_direction),
            directionAsc = stringResource(R.string.projects_views_direction_asc),
            directionDesc = stringResource(R.string.projects_views_direction_desc),
            filterClasses = stringResource(R.string.projects_views_filter_classes),
            none = stringResource(R.string.projects_create_template_none)
        ),
        sortOptions = listOf(
            "number" to stringResource(R.string.projects_views_sort_number),
            "created" to stringResource(R.string.projects_views_sort_created),
            "updated" to stringResource(R.string.projects_views_sort_updated),
            "rank" to stringResource(R.string.projects_views_sort_rank)
        ),
        onCreateView = { draft ->
            viewModel.createView(
                name = draft.name,
                viewtype = draft.viewtype,
                columns = draft.columns,
                rows = draft.rows,
                filter = draft.filter,
                sort = draft.sort,
                direction = draft.direction,
                classes = draft.classes,
                border = draft.border
            )
        },
        onUpdateView = { viewId, draft ->
            viewModel.updateView(
                viewId = viewId,
                name = draft.name,
                viewtype = draft.viewtype,
                columns = draft.columns,
                rows = draft.rows,
                filter = draft.filter,
                sort = draft.sort,
                direction = draft.direction,
                classes = draft.classes,
                border = draft.border
            )
        },
        onDeleteView = { viewId -> viewModel.deleteView(viewId) },
        onReorderViews = { order -> viewModel.reorderViews(order) },
        preview = projectDetails?.let { details ->
            { view, modifier ->
                DesignPreview(
                    project = details,
                    view = view?.toProjectView(),
                    modifier = modifier
                )
            }
        }
    )
}

private fun ProjectView.toListItem() = ViewListItem(
    id = id,
    name = name,
    viewtype = viewtype,
    filter = filter,
    columns = columns,
    rows = rows,
    fields = fields,
    sort = sort,
    direction = direction,
    classes = classes,
    rank = rank,
    border = border
)

private fun ViewListItem.toProjectView() = ProjectView(
    id = id,
    name = name,
    viewtype = viewtype,
    filter = filter,
    columns = columns,
    rows = rows,
    fields = fields,
    sort = sort,
    direction = direction,
    classes = classes,
    rank = rank,
    border = border
)
