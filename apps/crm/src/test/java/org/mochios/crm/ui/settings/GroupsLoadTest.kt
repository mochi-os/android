// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.settings

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.crm.CrmServer
import org.mochios.crm.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The access dialog's groups: loading from the moment they are asked for, so
 * the dialog shows a spinner rather than "no groups", and a failed read kept
 * for the dialog to show rather than taken for a CRM with none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GroupsLoadTest {

    private lateinit var server: CrmServer

    @Before
    fun begin() {
        server = CrmServer(RuntimeEnvironment.getApplication())
    }

    @After
    fun end() = server.stop()

    @Test
    fun `groups load at once, and groups that cannot be read are said, not taken for none`() {
        server.refuse("-/groups", "Groups unavailable", status = 500)
        val model = CrmSettingsViewModel(SavedStateHandle(mapOf("crmId" to "c1")), server.repository())
        model.loadGroups()
        assertTrue("THE POINT: loading from the call, before the answer", model.uiState.value.isLoadingGroups)
        until("the groups") { !model.uiState.value.isLoadingGroups }
        assertEquals(MochiError.ServerError(500, "Groups unavailable"), model.uiState.value.groupsError)
        assertTrue(model.uiState.value.groups.isEmpty())
    }

    @Test
    fun `groups that load are kept with no failure`() {
        server.data("-/groups", """{"groups": [{"id": "g1", "name": "Friends"}]}""")
        val model = CrmSettingsViewModel(SavedStateHandle(mapOf("crmId" to "c1")), server.repository())
        model.loadGroups()
        until("the groups") { !model.uiState.value.isLoadingGroups }
        assertEquals(listOf("Friends"), model.uiState.value.groups.map { group -> group.name })
        assertEquals(null, model.uiState.value.groupsError)
    }
}
