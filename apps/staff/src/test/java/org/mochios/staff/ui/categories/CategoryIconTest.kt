// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.categories

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.model.Category
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A category's icon can be emptied: the server leaves a field it is not sent
 * as it was, so an update sends the icon as typed, empty included, while a new
 * category with no icon sends none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoryIconTest {

    private val server = StaffServer { request ->
        val path = request.requestUrl!!.encodedPath
        when {
            path.endsWith("-/categories/update") || path.endsWith("-/categories/create") ->
                ok("""{"id": "c1", "name": "Shoes", "slug": "shoes"}""")
            else -> null
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    private fun fields(path: String) = server.made(path).single().body.readUtf8().split("&")

    @Test
    fun `an emptied icon is sent empty on update`() {
        val model = CategoryFormViewModel(SavedStateHandle(mapOf("id" to "c1")), server.repository)
        model.start(listOf(Category(id = "c1", name = "Shoes", slug = "shoes", icon = "shoe")))
        model.setForm(model.state.value.form.copy(icon = ""))
        model.submit()
        server.until { model.state.value.saved != null }
        val sent = fields("-/categories/update")
        assertTrue(sent.toString(), "icon=" in sent)
    }

    @Test
    fun `a new category with no icon sends none`() {
        val model = CategoryFormViewModel(SavedStateHandle(), server.repository)
        model.start(listOf(Category(id = "c0", name = "Hats", slug = "hats")))
        model.setForm(model.state.value.form.copy(name = "Shoes", slug = "shoes", icon = ""))
        model.submit()
        server.until { model.state.value.saved != null }
        assertFalse(fields("-/categories/create").any { field -> field.startsWith("icon=") })
    }
}
