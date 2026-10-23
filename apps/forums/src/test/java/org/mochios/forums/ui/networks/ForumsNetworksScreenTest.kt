// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.networks

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.forums.model.Crosspost
import org.mochios.forums.model.NetworkAccount
import org.mochios.forums.model.Post
import org.mochios.forums.ui.components.Crossposts
import org.mochios.forums.ui.components.PostLinkCard
import org.mochios.forums.ui.components.SensitiveCover
import org.mochios.forums.ui.find.JoinCommunity
import org.mochios.forums.ui.find.LemmyAccounts
import org.mochios.forums.ui.newpost.PostLinkField
import org.mochios.forums.ui.newpost.PostMarksFields
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the Android forums show of link posts, sensitive posts and communities
 * on other networks. No display qualifiers: a field under them never settles
 * in Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumsNetworksScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a sensitive post's content waits behind a cover until the reader shows it`() {
        rule.setContent {
            SensitiveCover(sensitive = true, key = "p1") { Text("The post itself") }
        }
        rule.onAllNodesWithText("The post itself").assertCountEquals(0)
        rule.onNodeWithText("This post is marked sensitive").assertIsDisplayed()
        rule.onNodeWithText("Show").performClick()
        rule.onNodeWithText("The post itself").assertIsDisplayed()
    }

    @Test
    fun `a post that is not sensitive shows its content at once`() {
        rule.setContent {
            SensitiveCover(sensitive = false, key = "p1") { Text("The post itself") }
        }
        rule.onNodeWithText("The post itself").assertIsDisplayed()
    }

    @Test
    fun `a link post's card names its site and its title, or its host where the page said nothing`() {
        rule.setContent {
            Column {
                PostLinkCard(Post(id = "a", url = "https://there.example/a", embed = """{"title": "A page", "site": "There"}"""))
                PostLinkCard(Post(id = "b", url = "https://www.elsewhere.example/b"))
                PostLinkCard(Post(id = "c", url = "javascript:alert(1)"))
            }
        }
        rule.onNodeWithText("There").assertIsDisplayed()
        rule.onNodeWithText("A page").assertIsDisplayed()
        rule.onNodeWithText("elsewhere.example").assertIsDisplayed()
        rule.onNodeWithText("https://www.elsewhere.example/b").assertIsDisplayed()
        rule.onAllNodesWithText("javascript:alert(1)").assertCountEquals(0)
    }

    @Test
    fun `the same link posted in other forums is listed by forum, each opening its post`() {
        var opened: Crosspost? = null
        val cross = Crosspost(post = "p2", title = "Linked", forum = "f2", forumName = "Other forum", fingerprint = "fp2")
        rule.setContent { Crossposts(crossposts = listOf(cross), onOpen = { opened = it }) }
        rule.onNodeWithText("Also posted in").assertIsDisplayed()
        rule.onNodeWithText("Other forum").performClick()
        assertEquals(cross, opened)
    }

    @Test
    fun `a link that is not a web address says so under the field`() {
        rule.setContent { PostLinkField(link = "there.example", onLink = {}) }
        rule.onNodeWithText("The link must be a web address").assertIsDisplayed()
    }

    @Test
    fun `a post's language is chosen in its own name, or left not given`() {
        var language: String? = null
        var sensitive: Boolean? = null
        rule.setContent {
            PostMarksFields(language = "", sensitive = false, onLanguage = { language = it }, onSensitive = { sensitive = it })
        }
        rule.onNodeWithText("Not given").performClick()
        rule.onNodeWithText("Français").performScrollTo().performClick()
        assertEquals("fr", language)
        rule.onNodeWithText("Sensitive").performClick()
        assertEquals(true, sensitive)
    }

    @Test
    fun `a community is joined as the account chosen where there are several`() {
        var joined: Pair<String, String>? = null
        rule.setContent {
            JoinCommunity(
                accounts = listOf(NetworkAccount(id = "h1", identifier = "amy@here.example"), NetworkAccount(id = "h2", identifier = "zed@here.example")),
                busy = false,
                onJoin = { address, account -> joined = address to account },
            )
        }
        rule.onNodeWithText("Community address").performTextInput("!books@lemmy.example")
        rule.onNodeWithText("amy@here.example").performClick()
        rule.onNodeWithText("zed@here.example").performClick()
        rule.onNodeWithText("Join").performClick()
        assertEquals("!books@lemmy.example" to "h2", joined)
    }

    @Test
    fun `a linked Lemmy account's communities are added and its settings imported`() {
        val calls = mutableListOf<String>()
        rule.setContent {
            LemmyAccounts(
                accounts = listOf(NetworkAccount(id = "l1", type = "lemmy", identifier = "me@lemmy.example")),
                busy = null,
                onAdd = { calls.add("add ${it.id}") },
                onImport = { calls.add("import ${it.id}") },
            )
        }
        rule.onNodeWithText("Lemmy").assertIsDisplayed()
        rule.onNodeWithText("Add communities").performClick()
        rule.onNodeWithText("Import settings").performClick()
        assertEquals(listOf("add l1", "import l1"), calls)
    }
}
