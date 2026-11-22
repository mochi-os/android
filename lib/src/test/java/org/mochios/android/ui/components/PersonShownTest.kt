// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.account.MochiAccount
import org.mochios.android.ui.AccountPicker
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A person the caller has no name for is shown by the fingerprint of their
 * id, in the person picker and in an access rule, never by the id itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class PersonShownTest {

    @get:Rule
    val rule = createComposeRule()

    private val ada = "12C83B6Phg9Ap8bK58uqrzWKBYRXAo9pSzsHzSJnEsAnCnRpdob"

    private fun picker(name: String?) {
        rule.setContent {
            PersonPicker(
                selectedId = ada,
                selectedName = name,
                members = emptyList(),
                onSelect = {},
                onClear = {},
                onSearch = { emptyList() },
            )
        }
    }

    @Test
    fun `a picked person with no name shows their fingerprint`() {
        picker(null)
        rule.onNodeWithText("6MF-gfL-93a").assertExists()
        rule.onNodeWithText(ada).assertDoesNotExist()
    }

    @Test
    fun `a picked person with a name shows it`() {
        picker("Ada")
        rule.onNodeWithText("Ada").assertExists()
    }

    private val labels = AccessLabels(
        sectionTitle = "Access",
        empty = "None",
        dialogTitle = "Add",
        tabUsers = "Users",
        tabGroups = "Groups",
        tabOther = "Other",
        searchUsers = "Search",
        selectGroup = "Group",
        noGroups = "No groups",
        selectRule = "Rule",
        subjectAnyone = "Anyone",
        subjectAuthenticated = "Signed in",
        anyoneDesc = "",
        authenticatedDesc = "",
        selected = { name -> name },
    )

    @Test
    fun `an access rule names its subject, never by id`() {
        assertEquals("Anyone", subjectLabel(AccessSubjectRule("*", "view"), labels, "Unknown group"))
        assertEquals("Signed in", subjectLabel(AccessSubjectRule("+", "view"), labels, "Unknown group"))
        assertEquals("Ada", subjectLabel(AccessSubjectRule(ada, "view", name = "Ada"), labels, "Unknown group"))
        assertEquals("6MF-gfL-93a", subjectLabel(AccessSubjectRule(ada, "view"), labels, "Unknown group"))
        assertEquals("6MF-gfL-93a", subjectLabel(AccessSubjectRule(ada, "view", name = " "), labels, "Unknown group"))
        assertEquals("Friends", subjectLabel(AccessSubjectRule("@friends", "view", name = "Friends"), labels, "Unknown group"))
    }

    @Test
    fun `an account with no name is offered by its fingerprint`() {
        rule.setContent {
            AccountPicker(
                accounts = listOf(MochiAccount.Snapshot(identity = ada, name = "", server = "https://example.com", fingerprint = null, session = "s")),
                onPick = {},
                onDifferent = {},
            )
        }
        rule.onNodeWithText("6MF-gfL-93a").assertExists()
        rule.onNodeWithText(ada).assertDoesNotExist()
    }

    @Test
    fun `a group the server did not name is an unknown group, never its id`() {
        assertEquals("Unknown group", subjectLabel(AccessSubjectRule("@g1", "view"), labels, "Unknown group"))
        assertEquals("Unknown group", subjectLabel(AccessSubjectRule("@g1", "view", name = ""), labels, "Unknown group"))
    }

    @Test
    fun `an unnamed group's rule row reads unknown group`() {
        rule.setContent {
            AccessRulesSection(
                rules = listOf(AccessSubjectRule("@g1", "view")),
                levels = listOf("view"),
                defaultLevel = "view",
                levelLabel = { level -> level },
                labels = labels,
                users = emptyList(),
                groups = emptyList(),
                onSearchUsers = {},
                onLoadGroups = {},
                onSetAccess = { _, _, _ -> },
                onRevoke = { _, _ -> },
            )
        }
        rule.onNodeWithText("Unknown group").assertExists()
        rule.onNodeWithText("@g1").assertDoesNotExist()
    }

    /** The add dialog's confirmation of the subject chosen in it. */
    @Test
    fun `a chosen subject with no name is confirmed by fingerprint or as an unknown group`() {
        val chosen = labels.copy(selected = { name -> "Chosen: $name" })
        assertEquals("Chosen: 6MF-gfL-93a", chosenLabel(ada, "", chosen, "Unknown group"))
        assertEquals("Chosen: Unknown group", chosenLabel("@g1", "", chosen, "Unknown group"))
        assertEquals("Chosen: Ada", chosenLabel(ada, "Ada", chosen, "Unknown group"))
        assertEquals("Chosen: Anyone", chosenLabel("*", "", chosen, "Unknown group"))
    }
}
