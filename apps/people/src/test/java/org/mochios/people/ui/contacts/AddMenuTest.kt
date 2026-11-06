// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A contact gains an email, a telephone, an address, a handle or a messaging address from one Add menu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private var form = ContactForm(name = "Ada")

    private fun show() {
        rule.setContent {
            var state by remember { mutableStateOf(form) }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ContactFields(form = state, books = emptyList(), onChange = {
                    state = it
                    form = it
                })
            }
        }
    }

    /** Opens the Add menu and answers the kinds it offers, in order. */
    private fun open(): List<String> {
        rule.onNodeWithText("Add").performScrollTo().performClick()
        return rule.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup()))
            .fetchSemanticsNodes()
            .map { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty() }
    }

    @Test
    fun `one Add button offers every kind, where five buttons wrapped`() {
        show()
        // THE POINT: one button, so nothing wraps however narrow the screen.
        for (old in listOf("Add email", "Add telephone", "Add address", "Add handle", "Add messaging address")) {
            rule.onNodeWithText(old).assertDoesNotExist()
        }
        assertEquals(listOf("Email", "Telephone", "Address", "Handle", "Messaging address"), open())
    }

    @Test
    fun `each kind the menu offers adds its row`() {
        show()
        for (kind in listOf("Email", "Telephone", "Address", "Handle", "Messaging address")) {
            open()
            rule.onNode(hasText(kind) and hasAnyAncestor(isPopup())).performClick()
            rule.waitForIdle()
        }
        assertEquals(1, form.emails.size)
        assertEquals(1, form.phones.size)
        assertEquals(1, form.addresses.size)
        assertEquals(1, form.handles.size)
        assertEquals(1, form.messaging.size)
    }
}
