// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.model.Comment
import org.mochios.feeds.model.PollOption
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.PostCopy
import org.mochios.feeds.model.PostData
import org.mochios.feeds.model.PostMedia
import org.mochios.feeds.model.PostPerson
import org.mochios.feeds.model.PostPoll
import org.mochios.feeds.model.PostQuote
import org.mochios.feeds.model.PublishAccount
import org.mochios.feeds.model.Source
import org.mochios.feeds.ui.find.FollowMenu
import org.mochios.feeds.ui.find.LookupResult
import org.mochios.feeds.ui.find.PersonRow
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.Lookup
import org.mochios.feeds.model.Person
import org.mochios.feeds.ui.post.CommenterDialog
import org.mochios.feeds.ui.post.PollEditor
import org.mochios.feeds.ui.post.PostFeaturesFields
import org.mochios.feeds.ui.post.PublishSwitches
import org.mochios.feeds.ui.settings.SourceCard
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the Android feeds show of posts from and on other networks, and the
 * controls that make them. No display qualifiers: a dialog holding a text
 * field never settles under them in Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostNetworksScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a post says who boosted it, who wrote it elsewhere, and that it is pinned and for followers`() {
        val post = Post(
            pinned = 10,
            visibility = "followers",
            data = PostData(
                person = PostPerson(name = "Alice", address = "@alice@there.example"),
                boost = PostPerson(address = "@bob@there.example"),
            ),
        )
        rule.setContent { PostHeadline(post) }
        rule.onNodeWithText("Boosted by @bob@there.example").assertIsDisplayed()
        rule.onNodeWithText("Alice").assertIsDisplayed()
        rule.onNodeWithText("@alice@there.example").assertIsDisplayed()
        rule.onNodeWithText("Pinned").assertIsDisplayed()
        rule.onNodeWithText("Followers only").assertIsDisplayed()
    }

    @Test
    fun `a post names where else it is, and how often it was boosted`() {
        rule.setContent {
            PostCopies(copies = listOf(PostCopy(address = "@me@here.example", network = "fediverse")), boosts = 2)
        }
        rule.onNodeWithText("Also on Fediverse").assertIsDisplayed()
        rule.onNodeWithText("Boosted 2 times").assertIsDisplayed()
    }

    @Test
    fun `a quoted post shows its author, its text and its network`() {
        rule.setContent {
            QuoteCard(PostQuote(network = "bluesky", text = "Quoted words", author = PostPerson(name = "Carol", address = "carol.bsky.social")))
        }
        rule.onNodeWithText("Carol").assertIsDisplayed()
        rule.onNodeWithText("carol.bsky.social").assertIsDisplayed()
        rule.onNodeWithText("Quoted words").assertIsDisplayed()
        rule.onNodeWithText("Bluesky").assertIsDisplayed()
    }

    @Test
    fun `a sensitive post's media wait behind a button until the reader asks`() {
        rule.setContent {
            PostMediaGrid(media = listOf(PostMedia(url = "https://there.example/a.png", type = "image")), sensitive = true)
        }
        rule.onNodeWithText("Show sensitive media").performClick()
        rule.onAllNodesWithText("Show sensitive media").assertCountEquals(0)
    }

    private val poll = PostPoll(
        options = listOf(PollOption("Tea", 3), PollOption("Coffee", 1)),
        voters = 4,
    )

    @Test
    fun `an open poll takes one choice and votes it`() {
        var voted: List<Int>? = null
        rule.setContent { PostPollView(poll = poll, voted = emptyList(), canVote = true, busy = false, onVote = { voted = it }) }
        rule.onNodeWithText("Vote").assertIsNotEnabled()
        rule.onNodeWithText("Tea").performClick()
        rule.onNodeWithText("Coffee").performClick()
        rule.onNodeWithText("Vote").assertIsEnabled().performClick()
        assertEquals(listOf(1), voted)
        rule.onNodeWithText("4 votes").assertIsDisplayed()
    }

    @Test
    fun `an open poll the reader voted in shows each option's share and no vote`() {
        rule.setContent { PostPollView(poll = poll.copy(expires = 300), voted = listOf(0), canVote = true, busy = false, onVote = {}, now = 200) }
        rule.onNodeWithText("75%").assertIsDisplayed()
        rule.onNodeWithText("25%").assertIsDisplayed()
        rule.onAllNodesWithText("Vote").assertCountEquals(0)
    }

    @Test
    fun `a closed poll shows the shares and says it is closed`() {
        rule.setContent { PostPollView(poll = poll.copy(expires = 100), voted = emptyList(), canVote = true, busy = false, onVote = {}, now = 200) }
        rule.onNodeWithText("75%").assertIsDisplayed()
        rule.onAllNodesWithText("Vote").assertCountEquals(0)
        rule.onNodeWithText("4 votes · Closed").assertIsDisplayed()
        rule.onAllNodesWithText("See results").assertCountEquals(0)
    }

    @Test
    fun `a reader who may still vote sees the results first, and goes back to the vote`() {
        var voted: List<Int>? = null
        rule.setContent { PostPollView(poll = poll, voted = emptyList(), canVote = true, busy = false, onVote = { voted = it }) }
        rule.onAllNodesWithText("75%").assertCountEquals(0)
        rule.onNodeWithText("Coffee").performClick()
        rule.onNodeWithText("See results").performClick()
        rule.onNodeWithText("75%").assertIsDisplayed()
        rule.onNodeWithText("25%").assertIsDisplayed()
        // No vote from the results.
        rule.onAllNodesWithText("Vote").assertCountEquals(0)
        rule.onNodeWithText("Hide results").performClick()
        rule.onAllNodesWithText("75%").assertCountEquals(0)
        // Back to the vote, the choice as it was.
        rule.onNodeWithText("Vote").assertIsEnabled().performClick()
        assertEquals(listOf(1), voted)
    }

    @Test
    fun `no link is offered where the results already show`() {
        rule.setContent { PostPollView(poll = poll, voted = listOf(0), canVote = true, busy = false, onVote = {}) }
        rule.onAllNodesWithText("See results").assertCountEquals(0)
    }

    @Test
    fun `a reader who cannot vote sees the results and no link`() {
        rule.setContent { PostPollView(poll = poll, voted = emptyList(), canVote = false, busy = false, onVote = {}) }
        rule.onNodeWithText("75%").assertIsDisplayed()
        rule.onAllNodesWithText("See results").assertCountEquals(0)
    }

    @Test
    fun `a warning hides what follows until the reader opens its strip`() {
        rule.setContent {
            var revealed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.foundation.layout.Column {
                PostWarning(warning = "Spoilers", revealed = revealed, onToggle = { revealed = !revealed })
                if (revealed) Text("Behind the warning")
            }
        }
        rule.onAllNodesWithText("Behind the warning").assertCountEquals(0)
        rule.onNodeWithText("Show post").assertIsDisplayed()
        // A tap anywhere on the strip opens it, the warning's own text too.
        rule.onNodeWithText("Spoilers").performClick()
        rule.onNodeWithText("Behind the warning").assertIsDisplayed()
        rule.onNodeWithText("Hide post").performClick()
        rule.onAllNodesWithText("Behind the warning").assertCountEquals(0)
    }

    @Test
    fun `the composer's publish switches are preset from the feed's list, by address`() {
        var toggled: Pair<String, Boolean>? = null
        rule.setContent {
            PublishSwitches(
                accounts = listOf(PublishAccount(id = "b", identifier = "zed@there.example"), PublishAccount(id = "a", identifier = "amy@there.example")),
                chosen = listOf("b"),
                onToggle = { id, on -> toggled = id to on },
            )
        }
        rule.onNodeWithText("zed@there.example").assertIsOn()
        rule.onNodeWithText("amy@there.example").assertIsOff().performClick()
        assertEquals("a" to true, toggled)
    }

    @Test
    fun `the composer chooses who may see the post, its warning and its sensitive mark`() {
        var visibility = ""
        var sensitive = false
        rule.setContent {
            PostFeaturesFields(
                visibility = "public",
                warning = "",
                sensitive = false,
                onVisibility = { visibility = it },
                onWarning = {},
                onSensitive = { sensitive = it },
            )
        }
        rule.onNodeWithText("Public").performClick()
        rule.onNodeWithText("Unlisted").performClick()
        assertEquals("unlisted", visibility)
        rule.onNodeWithText("Sensitive media").performClick()
        assertEquals(true, sensitive)
    }

    @Test
    fun `a poll keeps two options at least, and adds up to eight`() {
        var added = 0
        rule.setContent {
            PollEditor(
                poll = PollDraft(options = listOf("Tea", "Coffee")),
                onOption = { _, _ -> },
                onAdd = { added++ },
                onRemoveOption = {},
                onMultiple = {},
                onDuration = {},
                onRemove = {},
            )
        }
        rule.onAllNodesWithContentDescriptionPrefix("Remove option").assertCountEquals(0)
        rule.onNodeWithText("Add option").performClick()
        assertEquals(1, added)
        rule.onNodeWithText("1 day").assertIsDisplayed()
    }

    @Test
    fun `a source followed through an account says how the follow stands, and is not read on demand`() {
        rule.setContent {
            SourceCard(
                source = Source(id = "s", type = "person/posts", name = "Alice", url = "https://there.example/u/a", account = "h1", state = "pending"),
                onEdit = {}, onRemove = {}, onPoll = {},
            )
        }
        rule.onNodeWithText("Posts on the fediverse").assertIsDisplayed()
        rule.onNodeWithText("Follow pending").assertIsDisplayed()
        rule.onAllNodesWithText("https://there.example/u/a").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Poll").assertCountEquals(0)
    }

    @Test
    fun `a person found elsewhere is followed into the feed chosen`() {
        var followed: Pair<String, String>? = null
        rule.setContent {
            LookupResult(
                lookup = Lookup(kind = "group", network = "fediverse", id = "https://there.example/c/books", name = "Books", address = "!books@there.example"),
                query = "!books@there.example",
                feeds = listOf(Feed(id = "f1", name = "Mine")),
                busy = false,
                onFollow = { feed, query -> followed = feed.id to query },
            )
        }
        rule.onNodeWithText("!books@there.example · Fediverse · Community").assertIsDisplayed()
        rule.onNodeWithText("Follow").performClick()
        rule.onNodeWithText("Mine").performClick()
        assertEquals("f1" to "https://there.example/c/books", followed)
    }

    @Test
    fun `someone already followed says so, and with no feed of one's own there is no follow`() {
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                PersonRow(Person(contact = "c", name = "Zoe", handle = "@zoe@there.example", network = "fediverse", followed = true), feeds = listOf(Feed(id = "f1")), busy = false, onFollow = { _, _ -> })
                FollowMenu(label = "Nobody", feeds = emptyList(), busy = false, onFollow = {})
            }
        }
        rule.onNodeWithText("Following").assertIsDisplayed()
        rule.onAllNodesWithText("Nobody").assertCountEquals(0)
    }

    @Test
    fun `a report carries the reason the owner gives`() {
        var reason: String? = null
        rule.setContent { CommenterDialog(action = "report", author = "Dave", onDismiss = {}, onConfirm = { reason = it }) }
        rule.onNodeWithText("Report Dave?").assertIsDisplayed()
        rule.onNodeWithText("Reason").performTextInput("Spam")
        rule.onNodeWithText("Report").performClick()
        assertEquals("Spam", reason)
    }

    @Test
    fun `only a commenter from another network offers the owner mute, block and report`() {
        var action: String? = null
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                CommentItem(
                    comment = Comment(id = "c1", user = "https://there.example/u/e", authorName = "Eve", body = "Hi"),
                    depth = 0, avatarUrl = "", feedId = "f1", isEditing = false, editText = "",
                    onEditTextChange = {}, onSaveEdit = {}, onCancelEdit = {}, onReply = {}, onEdit = {}, onDelete = {},
                    onReact = {}, canManage = true, onModerate = { action = it },
                )
                CommentItem(
                    comment = Comment(id = "c2", user = "1local", authorName = "Frank", body = "Hi"),
                    depth = 0, avatarUrl = "", feedId = "f1", isEditing = false, editText = "",
                    onEditTextChange = {}, onSaveEdit = {}, onCancelEdit = {}, onReply = {}, onEdit = {}, onDelete = {},
                    onReact = {}, canManage = true, onModerate = { action = it },
                )
            }
        }
        rule.onAllNodesWithContentDescription("More options").assertCountEquals(1)
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Block Eve").performClick()
        assertEquals("block", action)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionPrefix(prefix: String) =
    onAllNodes(androidx.compose.ui.test.hasContentDescription(prefix, substring = true))

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescription(label: String) =
    onAllNodes(androidx.compose.ui.test.hasContentDescription(label))
