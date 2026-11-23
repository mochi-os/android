// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.settings

import android.content.ClipboardManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.ui.components.AccessCandidate
import org.mochios.android.util.fingerprint
import org.mochios.android.format.formatFingerprint
import org.mochios.wikis.R
import org.mochios.wikis.model.User
import org.mochios.wikis.ui.TestWiki
import org.mochios.wikis.ui.ok
import org.mochios.wikis.ui.redirects.RedirectsBody
import org.mochios.wikis.ui.redirects.RedirectsViewModel
import org.mochios.wikis.ui.refused
import org.mochios.wikis.ui.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * The settings screen's tabs: a load that failed is said, with Retry, rather
 * than shown as an empty list or as the defaults; an action's refusal says
 * the server's reason in the snackbar and leaves the tab as it was; a replica
 * is named, never shown by its id; and a subscription's source is a value to
 * copy, taken from the wiki itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class SettingsTabsTest {

    @get:Rule
    val rule = createComposeRule()

    /** Whether each list or the settings can be read. */
    @Volatile private var readable = true

    /** The wiki's source: blank for a wiki of its own, the original's id for a replica. */
    @Volatile private var source = ""

    private val wiki = TestWiki { method, path ->
        when ("$method $path") {
            "GET /wikis/w1/-/info" ->
                ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki", "home": "home", "source": "$source"}, "fingerprint": "wfp", "permissions": {"manage": true}}""")
            "GET /wikis/w1/-/redirects" -> if (readable) {
                ok("""{"redirects": [{"source": "zeta", "target": "home"}, {"source": "Alpha", "target": "home"}, {"source": "beta", "target": "home"}]}""")
            } else {
                refused(500, "Redirects unavailable")
            }
            "POST /wikis/w1/-/redirect/set" -> refused(400, "Target page does not exist")
            "GET /wikis/w1/-/replicas" -> if (readable) {
                ok("""{"replicas": [{"id": "$REPLICA", "subscribed": 1700000000, "synced": 0}]}""")
            } else {
                refused(500, "Replicas unavailable")
            }
            "POST /wikis/w1/-/replica/remove" -> refused(403, "Not your replica")
            "GET /wikis/w1/-/access" -> if (readable) {
                ok("""{"rules": [{"subject": "owner-id", "operation": "manage", "grant": 1, "name": "Ann", "owner": true}]}""")
            } else {
                refused(500, "Access unavailable")
            }
            "POST /wikis/w1/-/access/set" -> refused(400, "Invalid subject")
            "GET /wikis/-/users/search" -> refused(500, "Directory down")
            "GET /wikis/w1/-/settings" -> if (readable) {
                // No source here: the tab takes it from the wiki's information.
                ok("""{"settings": {"home": "start"}}""")
            } else {
                refused(500, "Settings unavailable")
            }
            "POST /wikis/w1/-/sync" -> refused(429, "Synced too recently")
            else -> null
        }
    }

    @Before
    fun begin() {
        wiki.start()
    }

    @After
    fun end() = wiki.stop()

    private fun handle() = SavedStateHandle(mapOf("wikiId" to "w1"))

    private fun text(id: Int, vararg values: Any): String = wiki.context.getString(id, *values)

    private fun parent(): WikiSettingsViewModel {
        val model = WikiSettingsViewModel(handle(), wiki.repository)
        until { model.uiState.value.wiki != null }
        return model
    }

    private fun shown(text: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** Waits for [done]; reading the nodes is what lets the main thread run the answer it waits on. */
    private fun answered(done: () -> Boolean) = rule.waitUntil(5_000) {
        rule.onAllNodes(isRoot()).fetchSemanticsNodes()
        done()
    }

    private fun retry() {
        readable = true
        rule.onNodeWithText(text(MochiR.string.common_retry)).performClick()
    }

    // ---- Redirects ----

    @Test
    fun `a redirects load that failed is said with Retry, which loads them sorted by source`() {
        readable = false
        val model = RedirectsViewModel(handle(), wiki.repository)
        rule.setContent { RedirectsBody(viewModel = model, showAddDialog = false, onShowAddDialogChange = {}) }
        shown("Redirects unavailable")
        rule.onNodeWithText(text(R.string.wikis_redirects_empty)).assertDoesNotExist()

        retry()
        shown("zeta")
        assertEquals(listOf("Alpha", "beta", "zeta"), model.uiState.value.redirects.map { redirect -> redirect.source })
    }

    @Test
    fun `a create refused on an empty list says the server's reason, and the list stays as it was`() {
        val model = RedirectsViewModel(handle(), wiki.repository)
        until { !model.uiState.value.isLoading }
        val settings = parent()
        val said = wiki.collect(settings.snackbar)
        rule.setContent { RedirectsTab(parentViewModel = settings, redirectsViewModel = model) }
        rule.waitForIdle()

        model.create("old", "missing")
        answered { said.isNotEmpty() }
        assertEquals(
            WikiSettingsSnackbar(R.string.wikis_redirect_create_failed, error = MochiError.Unknown("Target page does not exist", 400)),
            said.single(),
        )
        assertNull(model.uiState.value.error)
        rule.onNodeWithText(text(MochiR.string.common_retry)).assertDoesNotExist()
    }

    // ---- Replicas ----

    @Test
    fun `a replicas load that failed is said with Retry, and a replica without a name is unknown, never its id`() {
        readable = false
        val model = ReplicasTabViewModel(handle(), wiki.repository)
        val settings = parent()
        rule.setContent { ReplicasTab(parentViewModel = settings, viewModel = model) }
        shown("Replicas unavailable")
        rule.onNodeWithText(text(R.string.wikis_replicas_empty_title)).assertDoesNotExist()

        retry()
        shown(text(R.string.wikis_replica_unknown))
        rule.onAllNodesWithText(REPLICA.take(10), substring = true).assertCountEquals(0)

        rule.onNodeWithContentDescription(text(R.string.wikis_replicas_remove_confirm_action)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.wikis_replicas_remove_confirm_message, text(R.string.wikis_replica_unknown))).assertExists()
    }

    @Test
    fun `a refused removal says the server's reason, and the list is not taken for a failed load`() {
        val model = ReplicasTabViewModel(handle(), wiki.repository)
        until { model.uiState.value.replicas.isNotEmpty() }
        val settings = parent()
        val said = wiki.collect(settings.snackbar)
        rule.setContent { ReplicasTab(parentViewModel = settings, viewModel = model) }
        rule.waitForIdle()

        model.remove(REPLICA) {}
        answered { said.isNotEmpty() }
        assertEquals(R.string.wikis_replicas_remove_failed, said.single().messageRes)
        assertEquals("Not your replica", said.single().error?.message)
        assertNull(model.uiState.value.error)
    }

    // ---- Access ----

    @Test
    fun `an access load that failed is said with Retry, which loads the rules`() {
        readable = false
        val model = AccessTabViewModel(handle(), wiki.repository)
        val settings = parent()
        rule.setContent { AccessTab(parentViewModel = settings, viewModel = model) }
        shown("Access unavailable")
        rule.onNodeWithText(text(MochiR.string.access_no_rules)).assertDoesNotExist()

        retry()
        shown("Ann")
    }

    @Test
    fun `a refused grant and a failed user search say what happened, and the rules are not taken for a failed load`() {
        val model = AccessTabViewModel(handle(), wiki.repository)
        until { model.uiState.value.subjects.isNotEmpty() }
        val said = wiki.collect(model.snackbar)

        model.setAccess("someone", "edit")
        until { said.size == 1 }
        assertEquals(AccessTabSnackbar(R.string.wikis_access_set_failed, error = MochiError.Unknown("Invalid subject", 400)), said[0])
        assertNull(model.uiState.value.error)

        model.searchUsers("al")
        until { said.size == 2 }
        assertEquals(
            "THE POINT: a failed search is not shown as no match",
            AccessTabSnackbar(R.string.wikis_access_search_failed, error = MochiError.ServerError(500, "Directory down")),
            said[1],
        )
    }

    @Test
    fun `a searched person is granted by their identity id and named by their name or fingerprint`() {
        val id = "1A2b3C4d5E6f7G8h9J0kLmNoPqRsTuVwXyZ1A2b3C4d5E6f7G8h9J"
        var candidates = emptyList<AccessCandidate>()
        rule.setContent { candidates = accessCandidates(listOf(User(id = "id1", name = "Ann"), User(id = id, name = ""))) }
        rule.waitForIdle()
        assertEquals(
            listOf(AccessCandidate("id1", "Ann"), AccessCandidate(id, formatFingerprint(fingerprint(id)))),
            candidates,
        )
    }

    // ---- Settings ----

    @Test
    fun `until the settings arrive none are shown, and a failed load is said with Retry`() {
        readable = false
        val model = SettingsTabViewModel(handle(), wiki.repository)
        val settings = parent()
        rule.setContent { SettingsTab(parentViewModel = settings, onWikiDeleted = {}, viewModel = model) }
        shown("Settings unavailable")
        rule.onNodeWithText(text(R.string.wikis_settings_section_home)).assertDoesNotExist()
        rule.onAllNodesWithText(text(R.string.wikis_settings_section_delete)).assertCountEquals(0)

        retry()
        shown("start")
        rule.onNodeWithText(text(R.string.wikis_settings_section_home)).assertExists()
    }

    @Test
    fun `a replica's subscription comes from the wiki, its source is copied with a tap, and it has no Delete`() {
        source = "src-wiki"
        val model = SettingsTabViewModel(handle(), wiki.repository)
        val settings = parent()
        rule.setContent { SettingsTab(parentViewModel = settings, onWikiDeleted = {}, viewModel = model) }
        shown("start")
        rule.onNodeWithText(text(R.string.wikis_settings_section_subscription)).assertExists()
        rule.onAllNodesWithText(text(R.string.wikis_settings_section_delete)).assertCountEquals(0)

        val clipboard = wiki.context.getSystemService(ClipboardManager::class.java)
        rule.onNodeWithText("src-wiki").performClick()
        rule.waitForIdle()
        assertEquals("src-wiki", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `a refused sync says the server's reason`() {
        source = "src-wiki"
        val model = SettingsTabViewModel(handle(), wiki.repository)
        until { !model.uiState.value.isLoading }
        val said = wiki.collect(model.snackbar)
        model.sync()
        until { said.isNotEmpty() }
        assertEquals(
            SettingsTabSnackbar(R.string.wikis_settings_sync_failed, error = MochiError.Unknown("Synced too recently", 429)),
            said.single(),
        )
    }

    private companion object {
        const val REPLICA = "1ReplicaEntityIdentifier0000000000000000000000000"
    }
}
