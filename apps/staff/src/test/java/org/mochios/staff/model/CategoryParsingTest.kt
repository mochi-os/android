// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mochios.android.api.ApiClient

class CategoryParsingTest {
    // `event_staff_categories_list` answers `select c.*`, so the flags arrive
    // as the 0/1 integers SQLite stores. The module's Retrofit must be built
    // on the shared Gson, whose Boolean adapter reads them.
    private val row = """{"id":"c1","name":"Books","slug":"books","digital":1,"physical":0,"active":1,"children":2}"""

    @Test
    fun sharedGsonReadsTheComptrollersIntegerFlags() {
        val category = ApiClient.provideGson().fromJson(row, Category::class.java)
        assertEquals("c1", category.id)
        assertTrue(category.digital)
        assertFalse(category.physical)
        assertTrue(category.active)
    }

    // The Gson the module used to build for itself rejects the same row, which
    // is why the DI module must inject the shared instance rather than one of
    // its own.
    @Test
    fun aPlainGsonRejectsTheSameRow() {
        try {
            GsonBuilder().create().fromJson(row, Category::class.java)
            fail("a plain Gson accepted an integer where the model declares a Boolean")
        } catch (_: JsonSyntaxException) {
            // expected
        }
    }
}
