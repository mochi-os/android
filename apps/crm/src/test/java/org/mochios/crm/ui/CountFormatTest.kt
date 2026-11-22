// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.crm.CrmServer
import org.mochios.crm.lib.ActiveViewStore
import org.mochios.crm.lib.CollapsedColumnsStore
import org.mochios.crm.model.CrmObject
import org.mochios.crm.ui.board.BoardCard
import org.mochios.crm.ui.board.columnCount
import org.mochios.crm.ui.board.nestedCount
import org.mochios.crm.ui.crm.CrmViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * Counts inside sentences used to be written by String.format, which uses the
 * language's own digits (Arabic-Indic in Arabic) and no grouping. They are
 * written as the user writes numbers, digits 0 to 9, as the web writes them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ar")
class CountFormatTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a board card's nested cards`() {
        var text = ""
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))) {
                text = nestedCount(1234)
            }
        }
        rule.waitForIdle()
        assertTrue(text, "1.234" in text)
        assertTrue(text, text.none { it in '٠'..'٩' })
    }

    @Test
    fun `a board column's count, and its shown of total`() {
        val format = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))
        val objects = List(1234) { index -> CrmObject(id = "o$index") }
        assertEquals("1.234", columnCount(objects, null, format))
        assertEquals("1.000/1.234", columnCount(objects, objects.take(1000).map { obj -> obj.id }.toSet(), format))
    }

    @Test
    fun `a collapsed card's child count`() {
        val server = CrmServer(RuntimeEnvironment.getApplication())
        val socket = server.socket()
        try {
            val model = CrmViewModel(
                SavedStateHandle(mapOf("crmId" to "c1")),
                server.repository(),
                socket,
                server.session,
                ActiveViewStore(RuntimeEnvironment.getApplication()),
                CollapsedColumnsStore(RuntimeEnvironment.getApplication()),
            )
            val parent = CrmObject(id = "p", objectClass = "company")
            val children = List(1000) { index -> CrmObject(id = "k$index", objectClass = "company", parent = "p") }
            rule.setContent {
                CompositionLocalProvider(LocalFormat provides Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))) {
                    // Past the deepest level drawn, so the children are counted, not drawn.
                    BoardCard(
                        obj = parent,
                        viewModel = model,
                        crmDetails = null,
                        people = emptyList(),
                        borderFieldId = null,
                        childrenByParent = mapOf("p" to children),
                        depth = 3,
                        onObjectClick = {},
                    )
                }
            }
            rule.waitForIdle()
            rule.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(MochiR.string.common_collapse))
                .performClick()
            rule.waitForIdle()
            assertTrue(rule.onAllNodesWithText("1.000").fetchSemanticsNodes().isNotEmpty())
        } finally {
            socket.disconnectAll()
            server.stop()
        }
    }
}
