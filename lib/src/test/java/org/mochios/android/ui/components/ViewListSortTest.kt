// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A view's row names its sort order as a whole phrase for each direction,
 * never the stored `asc` or `desc`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ViewListSortTest {

    @get:Rule
    val rule = createComposeRule()

    private val labels = ViewListLabels(
        addAction = "Add view",
        empty = "No views",
        emptySubtitle = "",
        deleteTitle = "Delete view?",
        deleteMessage = { name -> "Delete $name?" },
        byField = { name -> "by $name" },
        typeBoard = "Board",
        typeList = "List",
        dragRow = "Drag",
        moveUp = "Up",
        moveDown = "Down",
        nameLabel = "Name",
        typeLabel = "Type",
        columnsField = "Columns",
        rowsField = "Rows",
        borderField = "Border",
        filter = "Filter",
        sortBy = "Sort by",
        direction = "Direction",
        directionAsc = "Ascending",
        directionDesc = "Descending",
        filterClasses = "Classes",
        none = "None",
    )

    private fun view(id: String, viewtype: String, direction: String, rank: Int) = ViewListItem(
        id = id,
        name = id,
        viewtype = viewtype,
        filter = "",
        columns = "",
        rows = "",
        fields = "",
        sort = "priority",
        direction = direction,
        classes = emptyList(),
        rank = rank,
        border = "",
    )

    @Test
    fun `each direction is named by its own phrase`() {
        rule.setContent {
            ViewListTab(
                views = listOf(view("first", "board", "desc", 1), view("second", "list", "asc", 2)),
                fields = emptyMap(),
                labels = labels,
                onAddView = {},
                onEditView = {},
                onDeleteView = {},
                onReorderViews = {},
            )
        }
        rule.onNodeWithText("Board · sorted descending").assertExists()
        rule.onNodeWithText("List · sorted ascending").assertExists()
    }
}
