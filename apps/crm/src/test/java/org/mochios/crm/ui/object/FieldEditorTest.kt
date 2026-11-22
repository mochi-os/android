// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.format.formatFingerprint
import org.mochios.android.i18n.DateFormat
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.util.fingerprint
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.Person
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An item's form writes a value as the user reads one: a date as the day
 * stored, west of UTC and before 1970 too; a number in their format, typed
 * with a minus or a decimal comma and saved only once it is one; a person the
 * CRM has no name for by their fingerprint, never their id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class FieldEditorTest {

    @get:Rule
    val rule = createComposeRule()

    private val format = Format(
        UserPreferences(
            dateFormat = DateFormat.DD_SLASH_MM_YYYY,
            numberFormat = NumberFormat.EUROPEAN_DOT_COMMA,
            timezone = "America/Los_Angeles",
        )
    )

    private val ada = "12C83B6Phg9Ap8bK58uqrzWKBYRXAo9pSzsHzSJnEsAnCnRpdob"

    /** What the editors saved, in order. */
    private val saved = mutableListOf<String>()

    private fun show(canWrite: Boolean, vararg fields: Pair<CrmField, String>) {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                Column {
                    fields.forEach { (field, value) ->
                        FieldEditor(
                            field = field,
                            value = value,
                            options = emptyList(),
                            canWrite = canWrite,
                            people = listOf(Person(id = "bob", name = "Bob")),
                            onValueChange = { text -> saved += text },
                            onSearchUsers = { emptyList() },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun shown(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun type(text: String) {
        rule.onNode(hasSetTextAction()).performTextReplacement(text)
        rule.waitForIdle()
    }

    @Test
    fun `a number can be typed with a minus and a decimal comma, and only a number is saved`() {
        show(true, CrmField(id = "amount", name = "Amount", fieldtype = "number") to "")
        type("-")
        type(",")
        assertEquals("nothing is saved before it is a number", emptyList<String>(), saved)
        assertTrue("what was typed stays", shown(","))
        type("-1,5")
        type(".5")
        assertEquals(listOf("-1.5", "0.5"), saved)
    }

    @Test
    fun `a number that cannot be changed reads in the user's format`() {
        show(false, CrmField(id = "amount", name = "Amount", fieldtype = "number") to "1234.5")
        assertTrue(shown("1.234,5"))
        assertFalse(shown("1234.5"))
    }

    @Test
    fun `a date reads as the day stored, west of UTC and before 1970`() {
        show(
            false,
            CrmField(id = "born", name = "Born", fieldtype = "date") to "1965-03-02",
            CrmField(id = "due", name = "Due", fieldtype = "date") to "2026-11-17",
        )
        assertTrue(shown("02/03/1965"))
        assertTrue(shown("17/11/2026"))
        assertFalse(shown("—"))
    }

    @Test
    fun `a person the CRM has no name for reads as their fingerprint`() {
        show(
            false,
            CrmField(id = "owner", name = "Owner", fieldtype = "user") to ada,
            CrmField(id = "helper", name = "Helper", fieldtype = "user") to "bob",
        )
        assertTrue(shown(formatFingerprint(fingerprint(ada))))
        assertFalse(shown(ada))
        assertTrue(shown("Bob"))
    }
}
