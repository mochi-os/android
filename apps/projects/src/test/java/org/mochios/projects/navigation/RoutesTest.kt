// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.navigation

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
 * The route builders against the module's real graph: a subscribed project's
 * ids are its owner's server's, so each route must match the screen it names
 * and hand that screen back every id as it was given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutesTest {

    private val navigation = NavHostController(RuntimeEnvironment.getApplication()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = ProjectsApp.ROUTER) { projectsNavGraph(this@apply, onOpenNotifications = {}) }
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
        check(ProjectsApp.project(id), ProjectsApp.PROJECT, "projectId")
        check(ProjectsApp.projectObject(id, id), ProjectsApp.PROJECT_OBJECT, "projectId", "objectId")
        check(ProjectsApp.projectSettings(id), ProjectsApp.PROJECT_SETTINGS, "projectId")
        check(ProjectsApp.projectDesign(id), ProjectsApp.PROJECT_DESIGN, "projectId")
        check(ProjectsApp.projectDesignClass(id, id), ProjectsApp.PROJECT_DESIGN_CLASS, "projectId", "classId")
        check(ProjectsApp.projectDesignCreateClass(id), ProjectsApp.PROJECT_DESIGN_CREATE_CLASS, "projectId")
        check(ProjectsApp.projectDesignView(id, id), ProjectsApp.PROJECT_DESIGN_VIEW, "projectId", "viewId")
        check(ProjectsApp.projectDesignCreateView(id), ProjectsApp.PROJECT_DESIGN_CREATE_VIEW, "projectId")
        check(
            ProjectsApp.projectDesignCreateField(id, id),
            ProjectsApp.PROJECT_DESIGN_CREATE_FIELD,
            "projectId", "classId",
        )
        check(
            ProjectsApp.projectDesignOption(id, id, id, id),
            ProjectsApp.PROJECT_DESIGN_OPTION,
            "projectId", "classId", "fieldId", "optionId",
        )
        check(
            ProjectsApp.projectAddColumn(id, id, id),
            ProjectsApp.PROJECT_ADD_COLUMN,
            "projectId", "classId", "fieldId",
        )
        check(
            ProjectsApp.projectDesignField(id, id, id),
            ProjectsApp.PROJECT_DESIGN_FIELD,
            "projectId", "classId", "fieldId",
        )
        check(
            ProjectsApp.diffViewer(id, id, id, id),
            ProjectsApp.DIFF_VIEWER,
            "projectId", "repo", "source", "target",
        )
        check(
            ProjectsApp.createObject(id, parent = id, presetValues = mapOf(id to id), copy = id),
            ProjectsApp.CREATE_OBJECT,
            "projectId", "parent", "field", "value", "copy",
        )
    }

    private companion object {
        /** Every character that splits or ends a route, and a space. */
        const val AWKWARD = "a/b?c#d&e=f%g h+i"
    }
}
