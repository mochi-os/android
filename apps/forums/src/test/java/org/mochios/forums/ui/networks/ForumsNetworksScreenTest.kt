// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.networks

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.forums.model.Crosspost
import org.mochios.forums.model.Destination
import org.mochios.forums.model.NetworkAccount
import org.mochios.forums.model.Post
import org.mochios.forums.ui.components.CommunityDestination
import org.mochios.forums.ui.components.Crossposts
import org.mochios.forums.ui.components.ForumLabel
import org.mochios.forums.ui.components.ForumTitle
import org.mochios.forums.ui.components.destinationBanner
import org.mochios.forums.ui.components.linkImage
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
    fun `the address stays in the field until the community is joined`() {
        var joined: (() -> Unit)? = null
        rule.setContent {
            JoinCommunity(
                accounts = listOf(NetworkAccount(id = "h1", identifier = "amy@here.example")),
                busy = false,
                onJoin = { _, _, done -> joined = done },
            )
        }
        rule.onNodeWithText("Community address").performTextInput("!books@lemmy.example")
        rule.onNodeWithText("Join").performClick()
        // Not joined yet, or refused: what was typed is kept.
        rule.onNodeWithText("!books@lemmy.example").assertExists()
        joined!!()
        rule.waitForIdle()
        rule.onNodeWithText("!books@lemmy.example").assertDoesNotExist()
    }

    @Test
    fun `a community is joined as the account chosen where there are several`() {
        var joined: Pair<String, String>? = null
        rule.setContent {
            JoinCommunity(
                accounts = listOf(NetworkAccount(id = "h1", identifier = "amy@here.example"), NetworkAccount(id = "h2", identifier = "zed@here.example")),
                busy = false,
                onJoin = { address, account, _ -> joined = address to account },
            )
        }
        rule.onNodeWithText("Community address").performTextInput("!books@lemmy.example")
        rule.onNodeWithText("amy@here.example").performClick()
        rule.onNodeWithText("zed@here.example").performClick()
        rule.onNodeWithText("Join").performClick()
        assertEquals("!books@lemmy.example" to "h2", joined)
    }

    @Test
    fun `the community a search typed is joined as it stands, the search hiding the empty form`() {
        var joined: Pair<String, String>? = null
        rule.setContent {
            JoinCommunity(
                accounts = listOf(NetworkAccount(id = "h1", identifier = "amy@here.example")),
                busy = false,
                onJoin = { address, account, _ -> joined = address to account },
                typed = "!technology@lemmy.world",
            )
        }
        // THE POINT: the typed address is offered to join, with no field to fill again.
        rule.onNodeWithText("Community address").assertDoesNotExist()
        rule.onNodeWithText("!technology@lemmy.world").assertIsDisplayed()
        rule.onNodeWithText("Join").performClick()
        assertEquals("!technology@lemmy.world" to "h1", joined)
    }

    @Test
    fun `what may name a community elsewhere is told from a search for a forum here`() {
        for (query in listOf("!technology@lemmy.world", "@books@lemmy.example", "books@lemmy.example", "https://lemmy.world/c/technology")) {
            assertTrue(query, org.mochios.forums.ui.find.looksLikeCommunity(query))
        }
        for (query in listOf("technology", "my forum", "mochi://forum/x", "https://example.com/forums/abcdefghi", "")) {
            assertFalse(query, org.mochios.forums.ui.find.looksLikeCommunity(query))
        }
    }

    @Test
    fun `a linked Lemmy account's communities are added and its settings imported`() {
        val calls = mutableListOf<String>()
        rule.setContent {
            LemmyAccounts(
                accounts = listOf(NetworkAccount(id = "l1", type = "lemmy", identifier = "me@lemmy.example")),
                busy = emptySet(),
                onAdd = { calls.add("add ${it.id}") },
                onImport = { calls.add("import ${it.id}") },
            )
        }
        rule.onNodeWithText("Lemmy").assertIsDisplayed()
        rule.onNodeWithText("Add communities").performClick()
        rule.onNodeWithText("Import settings").performClick()
        assertEquals(listOf("add l1", "import l1"), calls)
    }

    @Test
    fun `a community's composer says where a post goes and as whom, and a forum of ours says nothing`() {
        rule.setContent {
            Column {
                CommunityDestination(Destination("!books@lemmy.example", "@me@lemmy.example"))
                CommunityDestination(null)
            }
        }
        rule.onNodeWithText("Posts to !books@lemmy.example as @me@lemmy.example").assertIsDisplayed()
        rule.onAllNodesWithText("Posts to", substring = true).assertCountEquals(1)
    }

    @Test
    fun `a comment bar's banner carries the community's line above the reply it is for`() {
        val reply: @androidx.compose.runtime.Composable () -> Unit = { Text("Replying to Bob") }
        assertSame(reply, destinationBanner(null, reply))
        val banner = destinationBanner(Destination("!books@lemmy.example", "@me@lemmy.example"), reply)!!
        rule.setContent { banner() }
        rule.onNodeWithText("Posts to !books@lemmy.example as @me@lemmy.example").assertIsDisplayed()
        rule.onNodeWithText("Replying to Bob").assertIsDisplayed()
    }

    // A Lemmy image post's link is the image itself: shown as the picture, as
    // its network shows it, never as a card naming its address.
    @Test
    fun `an image link shows as the image and not its address`() {
        val image = "https://lemmy.example/pictrs/image/abc.jpeg"
        rule.setContent { PostLinkCard(Post(id = "i", url = image, thumbnail = "https://lemmy.example/pictrs/image/small.jpeg")) }
        rule.onNodeWithTag("post-link-image").assertExists()
        rule.onAllNodesWithText(image).assertCountEquals(0)
        rule.onAllNodesWithText("lemmy.example").assertCountEquals(0)
    }

    @Test
    fun `an image link is known by its file, by Lemmy's image proxy, or by the type its network gave`() {
        assertTrue(linkImage(Post(url = "https://lemmy.example/pictrs/image/abc.jpeg")))
        assertTrue(linkImage(Post(url = "https://lemmy.example/api/v3/image_proxy?url=https%3A%2F%2Fthere.example%2Fa.png")))
        assertTrue(linkImage(Post(url = "https://there.example/picture", embed = """{"type": "image/webp"}""")))
        assertFalse(linkImage(Post(url = "https://there.example/story")))
        assertFalse(linkImage(Post(url = "https://there.example/share?url=https%3A%2F%2Fthere.example%2Fa.png")))
        assertFalse(linkImage(Post(url = "https://there.example/a.png", embed = """{"type": "text/html"}""")))
    }

    // The button tapped spins; the account's other one waits, as both write
    // its communities.
    @Test
    fun `a Lemmy account's import spinning leaves add without a spinner`() {
        val account = NetworkAccount(id = "l1", type = "lemmy", identifier = "me@lemmy.example")
        rule.setContent { LemmyAccounts(accounts = listOf(account), busy = setOf("import/l1"), onAdd = {}, onImport = {}) }
        val button = { name: String -> hasClickAction() and hasAnyDescendant(hasText(name)) }
        val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        rule.onAllNodes(spinner and hasAnyAncestor(button("Import settings")), useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodes(spinner and hasAnyAncestor(button("Add communities")), useUnmergedTree = true).assertCountEquals(0)
        rule.onNode(button("Add communities"), useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `a Lemmy account's add spinning leaves import without a spinner`() {
        val account = NetworkAccount(id = "l1", type = "lemmy", identifier = "me@lemmy.example")
        rule.setContent { LemmyAccounts(accounts = listOf(account), busy = setOf("add/l1"), onAdd = {}, onImport = {}) }
        val button = { name: String -> hasClickAction() and hasAnyDescendant(hasText(name)) }
        val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        rule.onAllNodes(spinner and hasAnyAncestor(button("Add communities")), useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodes(spinner and hasAnyAncestor(button("Import settings")), useUnmergedTree = true).assertCountEquals(0)
        rule.onNode(button("Import settings"), useUnmergedTree = true).assertIsNotEnabled()
    }

    // Two communities can share a title, so a community's screen titles it by
    // its handle too, and lists and cross-posts name its server.
    @Test
    fun `a community's title carries its handle, and a forum of ours its name alone`() {
        rule.setContent {
            Column {
                ForumTitle(name = "test", handle = "!test@lemmy.ml")
                ForumTitle(name = "Ours", handle = "")
            }
        }
        rule.onNodeWithText("!test@lemmy.ml").assertIsDisplayed()
        rule.onNodeWithText("Ours").assertIsDisplayed()
        rule.onAllNodesWithText("!", substring = true).assertCountEquals(1)
    }

    @Test
    fun `a post in a list names its community's server beside the name`() {
        rule.setContent {
            Column {
                ForumLabel("test", "lemmy.ml")
                ForumLabel("Ours", "")
            }
        }
        rule.onNodeWithText("lemmy.ml", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("lemmy", substring = true).assertCountEquals(1)
    }

    @Test
    fun `a cross-post in a community names its server`() {
        val cross = Crosspost(post = "p2", title = "Linked", forum = "https://lemmy.ml/c/test", forumName = "test", handle = "test@lemmy.ml", fingerprint = "mZxJ7b43h")
        rule.setContent { Crossposts(crossposts = listOf(cross), onOpen = {}) }
        rule.onNodeWithText("test · lemmy.ml").assertIsDisplayed()
    }
}
