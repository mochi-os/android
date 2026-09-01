// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

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
import org.mochios.crm.R
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmView

@Composable
fun ViewsTab(
    views: List<CrmView>,
    classes: List<CrmClass>,
    fields: Map<String, List<CrmField>>,
    viewModel: DesignViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val crmDetails = uiState.crmDetails
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
            addAction = stringResource(R.string.crm_views_add),
            empty = stringResource(R.string.crm_views_empty),
            emptySubtitle = stringResource(R.string.crm_views_empty_subtitle),
            addDialogTitle = stringResource(R.string.crm_views_add_dialog_title),
            editDialogTitle = stringResource(R.string.crm_views_edit_dialog_title),
            deleteTitle = stringResource(R.string.crm_views_delete_title),
            deleteMessage = { viewName ->
                context.getString(R.string.crm_views_delete_message, viewName)
            },
            byField = { fieldName -> context.getString(R.string.crm_views_by, fieldName) },
            sortedBy = { direction -> context.getString(R.string.crm_views_sorted, direction) },
            typeBoard = stringResource(R.string.crm_views_type_board),
            typeList = stringResource(R.string.crm_views_type_list),
            moveUp = stringResource(R.string.crm_views_move_up),
            moveDown = stringResource(R.string.crm_views_move_down),
            nameLabel = stringResource(R.string.crm_class_name),
            typeLabel = stringResource(R.string.crm_views_type),
            columnsField = stringResource(R.string.crm_views_columns_field),
            rowsField = stringResource(R.string.crm_views_rows_field),
            borderField = stringResource(R.string.crm_views_border_field),
            filter = stringResource(R.string.crm_views_filter),
            sortBy = stringResource(R.string.crm_views_sort_by),
            direction = stringResource(R.string.crm_views_direction),
            directionAsc = stringResource(R.string.crm_views_direction_asc),
            directionDesc = stringResource(R.string.crm_views_direction_desc),
            filterClasses = stringResource(R.string.crm_views_filter_classes),
            none = stringResource(R.string.crm_create_template_none)
        ),
        sortOptions = listOf(
            "created" to stringResource(R.string.crm_views_sort_created),
            "updated" to stringResource(R.string.crm_views_sort_updated),
            "rank" to stringResource(R.string.crm_views_sort_rank)
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
        preview = crmDetails?.let { details ->
            { view, modifier ->
                DesignPreview(
                    crm = details,
                    view = view?.toCrmView(),
                    modifier = modifier
                )
            }
        }
    )
}

private fun CrmView.toListItem() = ViewListItem(
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

private fun ViewListItem.toCrmView() = CrmView(
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
