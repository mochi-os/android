// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.mochios.crm.R
import org.mochios.crm.model.Activity
import org.mochios.crm.model.ActivityDisplay
import org.mochios.crm.model.CrmField
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An activity entry reads as what it changed, never as the ids it stores: the
 * server's names for parents, link targets, classes, options and people, and
 * anything else formatted by its field's type in the user's own formats.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityLineTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val format = Format(
        UserPreferences(
            dateFormat = DateFormat.DD_SLASH_MM_YYYY,
            numberFormat = NumberFormat.EUROPEAN_DOT_COMMA,
            timezone = "America/Los_Angeles",
        )
    )

    private val ada = "12C83B6Phg9Ap8bK58uqrzWKBYRXAo9pSzsHzSJnEsAnCnRpdob"

    /** The object's class's own fields. */
    private val fields = listOf(
        CrmField(id = "stage", name = "Stage", fieldtype = "enumerated"),
        CrmField(id = "owner", name = "Owner", fieldtype = "user"),
        CrmField(id = "due", name = "Due", fieldtype = "date"),
        CrmField(id = "amount", name = "Deal value", fieldtype = "number"),
        CrmField(id = "active", name = "Active", fieldtype = "checkbox"),
        CrmField(id = "tasks", name = "Tasks", fieldtype = "checklist"),
    )

    private fun lines(vararg items: Activity): List<ActivityLine> {
        var result = emptyList<ActivityLine>()
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                result = items.map { item -> activityLine(item, fields) }
            }
        }
        rule.waitForIdle()
        return result
    }

    private fun text(id: Int) = context.getString(id)

    @Test
    fun `a move names its parents by title, the top level as none, and an unnamed one as untitled`() {
        val (named, top, unnamed) = lines(
            Activity(id = "1", action = "moved", field = "parent", oldvalue = "p1", newvalue = "p2",
                display = ActivityDisplay(old = "Acme", new = "Globex")),
            Activity(id = "2", action = "moved", field = "parent", oldvalue = "p1", newvalue = "",
                display = ActivityDisplay(old = "Acme")),
            // An untitled or deleted parent: the server names nothing.
            Activity(id = "3", action = "moved", field = "parent", oldvalue = "", newvalue = "p3", display = ActivityDisplay()),
        )
        assertEquals(ActivityLine(text(R.string.crm_activity_moved), "Acme", "Globex"), named)
        assertEquals(ActivityLine(text(R.string.crm_activity_moved), "Acme", text(R.string.crm_parent_none)), top)
        assertEquals(
            ActivityLine(text(R.string.crm_activity_moved), text(R.string.crm_parent_none), text(R.string.crm_untitled)),
            unnamed,
        )
    }

    @Test
    fun `a link reads as its type and the object linked to`() {
        val (blocks, other) = lines(
            Activity(id = "1", action = "linked", field = "blocks", newvalue = "o9", display = ActivityDisplay(new = "Website")),
            Activity(id = "2", action = "linked", field = "follows", newvalue = "o9", display = ActivityDisplay()),
        )
        assertEquals(ActivityLine(text(R.string.crm_links_type_blocks), null, "Website"), blocks)
        assertEquals(ActivityLine(text(R.string.crm_activity_linked), null, text(R.string.crm_untitled)), other)
    }

    @Test
    fun `a class change names the classes`() {
        val (line) = lines(
            Activity(id = "1", action = "updated", field = "class", oldvalue = "company", newvalue = "contact",
                display = ActivityDisplay(old = "Company", new = "Contact")),
        )
        assertEquals(ActivityLine(text(R.string.crm_activity_class), "Company", "Contact"), line)
    }

    @Test
    fun `a field's change names the field as written and takes the server's names for its values`() {
        val (line) = lines(
            Activity(id = "1", action = "updated", field = "stage", oldvalue = "lead", newvalue = "won",
                display = ActivityDisplay(field = "Stage", type = "enumerated", old = "Lead", new = "Won")),
        )
        assertEquals(ActivityLine(context.getString(R.string.crm_activity_updated, "Stage"), "Lead", "Won"), line)
    }

    @Test
    fun `a value the server did not name is written by its field's type, never as an id`() {
        // As the server answers them: the field and its type named, the
        // values left to the client.
        fun named(field: String, type: String) = ActivityDisplay(field = field, type = type)
        val (due, amount, active, tasks, owner, stage) = lines(
            Activity(id = "1", action = "updated", field = "due", oldvalue = "", newvalue = "1965-03-02",
                display = named("Due", "date")),
            Activity(id = "2", action = "updated", field = "amount", oldvalue = "1234.5", newvalue = "-0.25",
                display = named("Deal value", "number")),
            Activity(id = "3", action = "updated", field = "active", oldvalue = "0", newvalue = "1",
                display = named("Active", "checkbox")),
            Activity(id = "4", action = "updated", field = "tasks", oldvalue = "",
                newvalue = """[{"id":"a","text":"Call","done":true},{"id":"b","text":"Mail","done":false}]""",
                display = named("Tasks", "checklist")),
            // No entity behind the value: the server names no one.
            Activity(id = "5", action = "updated", field = "owner", oldvalue = "", newvalue = ada,
                display = named("Owner", "user")),
            // Neither the type: an older server, read from the class's own fields.
            Activity(id = "6", action = "updated", field = "stage", oldvalue = "", newvalue = "x9"),
        )
        // The stored day, west of UTC too, and before 1970.
        assertEquals("02/03/1965", due.new)
        assertEquals(null, due.old)
        assertEquals("1.234,5", amount.old)
        assertEquals("-0,25", amount.new)
        assertEquals(text(R.string.crm_field_no), active.old)
        assertEquals(text(R.string.crm_field_yes), active.new)
        assertEquals("1/2", tasks.new)
        assertEquals(formatFingerprint(fingerprint(ada)), owner.new)
        // An option the class no longer has is not named by its id.
        assertEquals(null, stage.new)
        assertFalse(listOf(due, amount, active, tasks, owner, stage).any { line ->
            line.new == ada || line.new == "x9" || line.new?.startsWith("[") == true
        })
    }

    @Test
    fun `a field the design no longer has makes no claim about its values`() {
        val (line) = lines(
            Activity(id = "1", action = "updated", field = "gone", oldvalue = "a1", newvalue = "b2", display = ActivityDisplay()),
        )
        assertEquals(ActivityLine(text(R.string.crm_activity_made_change), null, null), line)
    }
}

private operator fun <T> List<T>.component6(): T = this[5]
