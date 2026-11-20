// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.navigation

import android.os.Bundle
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The route builders against the module's real graph: a subscribed CRM's ids
 * are its owner's server's, so each route must match the screen it names and
 * hand that screen back every id as it was given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutesTest {

    private val navigation = NavHostController(RuntimeEnvironment.getApplication()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = CrmsApp.ROUTER) { crmsNavGraph(this@apply) }
    }

    /** Opens [built] and checks it reached [pattern] with each of [names] filled with [AWKWARD]. */
    private fun check(built: String, pattern: String, vararg names: String): Bundle {
        navigation.navigate(built)
        val entry = navigation.currentBackStackEntry!!
        assertEquals(built, pattern, entry.destination.route)
        val arguments = entry.arguments ?: Bundle()
        names.forEach { name -> assertEquals("$built: $name", AWKWARD, arguments.getString(name)) }
        return arguments
    }

    @Test
    fun `every builder hands its screen an awkward id unchanged`() {
        val id = AWKWARD
        check(CrmsApp.crm(id), CrmsApp.CRM, "crmId")
        check(CrmsApp.crmObject(id, id), CrmsApp.CRM_OBJECT, "crmId", "objectId")
        check(CrmsApp.crmSettings(id), CrmsApp.CRM_SETTINGS, "crmId")
        check(CrmsApp.crmDesign(id), CrmsApp.CRM_DESIGN, "crmId")
        check(CrmsApp.crmDesignClass(id, id), CrmsApp.CRM_DESIGN_CLASS, "crmId", "classId")
        check(CrmsApp.crmDesignCreateClass(id), CrmsApp.CRM_DESIGN_CREATE_CLASS, "crmId")
        check(CrmsApp.crmDesignView(id, id), CrmsApp.CRM_DESIGN_VIEW, "crmId", "viewId")
        check(CrmsApp.crmDesignCreateView(id), CrmsApp.CRM_DESIGN_CREATE_VIEW, "crmId")
        check(CrmsApp.crmDesignCreateField(id, id), CrmsApp.CRM_DESIGN_CREATE_FIELD, "crmId", "classId")
        check(
            CrmsApp.crmDesignOption(id, id, id, id),
            CrmsApp.CRM_DESIGN_OPTION,
            "crmId", "classId", "fieldId", "optionId",
        )
        check(CrmsApp.crmAddColumn(id, id, id), CrmsApp.CRM_ADD_COLUMN, "crmId", "classId", "fieldId")
        check(CrmsApp.crmDesignField(id, id, id), CrmsApp.CRM_DESIGN_FIELD, "crmId", "classId", "fieldId")
        check(
            CrmsApp.createObject(id, presetValues = mapOf(id to id), copy = id),
            CrmsApp.CREATE_OBJECT,
            "crmId", "field", "value", "copy",
        )
    }

    private companion object {
        /** Every character that splits or ends a route, and a space. */
        const val AWKWARD = "a/b?c#d&e=f%g h+i"
    }
}
