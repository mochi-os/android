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
        assertEquals("Anyone", subjectLabel(AccessSubjectRule("*", "view"), labels))
        assertEquals("Signed in", subjectLabel(AccessSubjectRule("+", "view"), labels))
        assertEquals("Ada", subjectLabel(AccessSubjectRule(ada, "view", name = "Ada"), labels))
        assertEquals("6MF-gfL-93a", subjectLabel(AccessSubjectRule(ada, "view"), labels))
        assertEquals("6MF-gfL-93a", subjectLabel(AccessSubjectRule(ada, "view", name = " "), labels))
        assertEquals("Friends", subjectLabel(AccessSubjectRule("@friends", "view", name = "Friends"), labels))
    }
}
