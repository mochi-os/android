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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mochios.people.model.XmppContact
import org.robolectric.annotation.Config

/**
 * A contact gains an email, a telephone, an address, a handle, a messaging
 * address or a field it has one of from one Add menu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private var form = ContactForm(name = "Ada")
    private var xmpp = emptyList<XmppContact>()
    private val ended = mutableListOf<XmppContact>()
    // The contact's XMPP standing as the screen holds it, which a save changes.
    private val answer = mutableStateOf(emptyList<XmppContact>())

    private fun show() {
        rule.setContent {
            var state by remember { mutableStateOf(form) }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ContactFields(
                    form = state,
                    books = emptyList(),
                    onChange = {
                        state = it
                        form = it
                    },
                    xmpp = answer.value,
                    onEndXmpp = { ended += it },
                )
            }
        }
    }

    /** Ada with an XMPP address on the user's contact list there, or off it. */
    private fun listed(subscription: String) {
        form = ContactForm(name = "Ada", messaging = listOf(MessagingEntry(value = "dave@example.org", network = NETWORK_XMPP)))
        xmpp = listOf(XmppContact(account = "x1", identifier = "me@example.com", address = "dave@example.org", subscription = subscription))
        answer.value = xmpp
    }

    /** Types [value] into the messaging address, then leaves it for the name. */
    private fun edit(value: String) {
        val address = rule.onNode(hasSetTextAction() and hasText("dave@example.org"))
        address.performScrollTo().performClick()
        address.performTextReplacement(value)
        rule.onNode(hasSetTextAction() and hasText("Ada")).performScrollTo().performClick()
        rule.waitForIdle()
    }

    /** Taps the messaging address's remove button. */
    private fun remove() {
        rule.onAllNodesWithContentDescription("Remove").onLast().performScrollTo().performClick()
        rule.waitForIdle()
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
        assertEquals(
            listOf("Email", "Telephone", "Address", "Handle", "Messaging address", "Nickname", "Birthday", "Organisation", "Job title", "Website", "Note"),
            open(),
        )
    }

    // THE POINT: a field a contact has one of shows only when the card fills
    // it or it is added, as on the web, not as an empty box on every contact.
    @Test
    fun `the single fields show only when filled or added, and go when removed`() {
        form = ContactForm(name = "Ada", organisation = "Analytical Engines")
        show()
        rule.onNodeWithText("Organisation").assertExists()
        for (empty in listOf("Nickname", "Birthday", "Job title", "Website", "Note")) {
            rule.onNodeWithText(empty).assertDoesNotExist()
        }
        assertEquals(
            listOf("Email", "Telephone", "Address", "Handle", "Messaging address", "Nickname", "Birthday", "Job title", "Website", "Note"),
            open(),
        )
        rule.onNode(hasText("Job title") and hasAnyAncestor(isPopup())).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Job title").assertExists()
        // Removing the organisation empties it, so the save drops it, and hides it.
        rule.onAllNodesWithContentDescription("Remove")[0].performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals("", form.organisation)
        rule.onNodeWithText("Organisation").assertDoesNotExist()
    }

    // THE POINT: the network is chosen first and the address under it, as on
    // the web, and choosing one puts the cursor in the address: below the
    // field, a network chosen seemed to do nothing.
    @Test
    fun `a messaging address asks for its network first, then takes the cursor to the address`() {
        show()
        open()
        rule.onNode(hasText("Messaging address") and hasAnyAncestor(isPopup())).performClick()
        rule.waitForIdle()
        val network = rule.onNodeWithContentDescription("Network").getUnclippedBoundsInRoot()
        val address = rule.onAllNodes(hasSetTextAction()).onLast()
        assertTrue("the network is above the address", network.top < address.getUnclippedBoundsInRoot().top)
        rule.onNodeWithContentDescription("Network").performScrollTo().performClick()
        rule.onNode(hasText("XMPP") and hasAnyAncestor(isPopup())).performClick()
        rule.waitForIdle()
        address.assertIsFocused()
        assertEquals("xmpp", form.messaging.single().network)
    }

    @Test
    fun `a handle asks for its network first too`() {
        show()
        open()
        rule.onNode(hasText("Handle") and hasAnyAncestor(isPopup())).performClick()
        rule.waitForIdle()
        val network = rule.onNodeWithContentDescription("Network").getUnclippedBoundsInRoot()
        val handle = rule.onAllNodes(hasSetTextAction()).onLast().getUnclippedBoundsInRoot()
        assertTrue("the network is above the handle", network.top < handle.top)
    }

    // THE POINT: an XMPP address on the user's contact list there, removed
    // from the card, asks whether to take them off the list too, keeping
    // them unless told, where it used to leave them there with no switch.
    @Test
    fun `removing an XMPP address on the contact list takes them off it when told`() {
        listed("both")
        show()
        remove()
        rule.onNode(hasText("dave@example.org") and hasAnyAncestor(isDialog())).assertExists()
        rule.onNode(hasText("Remove") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.waitForIdle()
        assertEquals(listOf("dave@example.org"), ended.map { it.address })
        assertTrue(form.messaging.isEmpty())
    }

    @Test
    fun `removing an XMPP address on the contact list keeps them there otherwise`() {
        listed("both")
        show()
        remove()
        rule.onNodeWithText("Keep").performClick()
        rule.waitForIdle()
        assertTrue(ended.isEmpty())
        assertTrue(form.messaging.isEmpty())
    }

    @Test
    fun `changing an XMPP address on the contact list asks, once the field is left`() {
        listed("both")
        show()
        rule.waitForIdle()
        // The saved card no longer names the address, so neither does the contact.
        val address = rule.onNode(hasSetTextAction() and hasText("dave@example.org"))
        address.performScrollTo().performClick()
        address.performTextReplacement("dave@example.com")
        answer.value = emptyList()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasText("Ada")).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNode(hasText("dave@example.org") and hasAnyAncestor(isDialog())).assertExists()
        rule.onNode(hasText("Remove") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.waitForIdle()
        assertEquals(listOf("dave@example.org"), ended.map { it.address })
        assertEquals(listOf("dave@example.com"), form.messaging.map { it.value })
    }

    @Test
    fun `a changed XMPP address is asked about once, and an unchanged one not at all`() {
        listed("both")
        show()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasText("dave@example.org")).performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and hasText("Ada")).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNode(isDialog()).assertDoesNotExist()
        edit("dave@example.com")
        rule.onNodeWithText("Keep").performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasText("dave@example.com")).performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and hasText("Ada")).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNode(isDialog()).assertDoesNotExist()
        assertTrue(ended.isEmpty())
    }

    @Test
    fun `removing an XMPP address not on the contact list asks nothing`() {
        listed("none")
        show()
        remove()
        rule.onNodeWithText("Keep").assertDoesNotExist()
        assertTrue(ended.isEmpty())
        assertTrue(form.messaging.isEmpty())
    }

    @Test
    fun `a contact with no details has no Details heading`() {
        show()
        rule.onNodeWithText("Details").assertDoesNotExist()
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
