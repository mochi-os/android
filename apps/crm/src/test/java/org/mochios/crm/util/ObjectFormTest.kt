// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.crm.model.CrmField

class ObjectFormTest {

    /** A contact class as the server hands it over, not in rank order. */
    private val contact = listOf(
        CrmField(id = "owner", name = "Owner", fieldtype = "user", rank = 5),
        CrmField(id = "name", name = "Name", fieldtype = "text", rank = 0),
        CrmField(id = "stage", name = "Stage", fieldtype = "enumerated", rank = 2),
        CrmField(id = "notes", name = "Notes", fieldtype = "text", rank = 1),
        CrmField(id = "email", name = "Email", fieldtype = "text", rank = 3),
    )

    @Test
    fun `an object's form shows every field of its class`() {
        assertEquals(
            setOf("name", "notes", "stage", "email", "owner"),
            formFields(contact).map { field -> field.id }.toSet(),
        )
    }

    @Test
    fun `an object's form lists its fields in the class's order`() {
        // A board listing the stage and owner on its cards used to put those
        // two ahead of the name in the form of a contact opened from it.
        assertEquals(
            listOf("name", "notes", "stage", "email", "owner"),
            formFields(contact).map { field -> field.id },
        )
    }
}
