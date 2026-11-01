// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.model.Comment
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.PostCopy
import org.mochios.feeds.model.PostEmoji
import org.mochios.feeds.model.PublishAccount
import org.mochios.feeds.model.Source
import org.mochios.feeds.ui.find.looksUpElsewhere
import org.mochios.feeds.ui.settings.pollable

/** The rules the Android feeds share with the web client for posts from and on other networks. */
class NetworksRulesTest {

    @Test
    fun `a post is marked pinned, followers only or unlisted, and a public one is not`() {
        assertTrue(hasBadges(Post(pinned = 10)))
        assertTrue(hasBadges(Post(visibility = "followers")))
        assertTrue(hasBadges(Post(visibility = "unlisted")))
        assertFalse(hasBadges(Post(visibility = "public")))
        assertFalse(hasBadges(Post()))
    }

    @Test
    fun `a post is boosted from an account when a fediverse copy carries its address`() {
        val account = PublishAccount(id = "a", type = "fediverse", identifier = "@me@here.example", hosted = true)
        val boosted = Post(copies = listOf(PostCopy(address = "@me@here.example", network = "fediverse")))
        assertTrue(boostedFrom(boosted, account))
        assertFalse(boostedFrom(Post(copies = listOf(PostCopy(address = "@me@here.example", network = "bluesky"))), account))
        assertFalse(boostedFrom(Post(copies = listOf(PostCopy(address = "@other@here.example", network = "fediverse"))), account))
        assertFalse(boostedFrom(boosted, null))
    }

    @Test
    fun `a post is on the fediverse where it has a copy there or comes from there`() {
        assertTrue(onFediverse(Post(copies = listOf(PostCopy(network = "fediverse")))))
        assertTrue(onFediverse(Post(remote = "https://there.example/1", author = "https://there.example/u/a")))
        assertFalse(onFediverse(Post(copies = listOf(PostCopy(network = "bluesky")))))
        assertFalse(onFediverse(Post(remote = "https://there.example/1")))
    }

    @Test
    fun `a post is boosted from the hosted account on its own network`() {
        val fediverse = PublishAccount(id = "f", type = "fediverse", identifier = "@me@here.example", hosted = true)
        val bluesky = PublishAccount(id = "b", type = "bluesky", identifier = "me.here.example", hosted = true)
        val accounts = listOf(fediverse, bluesky)
        val sky = Post(remote = "at://did:plc:alice/app.bsky.feed.post/1", author = "did:plc:alice")
        val fedi = Post(remote = "https://there.example/1", author = "https://there.example/u/a")
        assertEquals("b", boostAccount(sky, accounts)?.id)
        assertEquals("f", boostAccount(fedi, accounts)?.id)
        assertNull(boostAccount(sky, listOf(fediverse)))
        // A repost of the post on Bluesky under the account's handle is its boost there.
        assertTrue(boostedFrom(sky.copy(copies = listOf(PostCopy(address = "me.here.example", network = "bluesky"))), bluesky))
        assertFalse(boostedFrom(sky.copy(copies = listOf(PostCopy(address = "me.here.example", network = "fediverse"))), bluesky))
    }

    @Test
    fun `hosted fediverse and Bluesky accounts boost`() {
        val accounts = listOf(
            PublishAccount(id = "1", type = "fediverse", hosted = true),
            PublishAccount(id = "2", type = "fediverse", hosted = false),
            PublishAccount(id = "3", type = "bluesky", hosted = true),
            PublishAccount(id = "4", type = "matrix", hosted = true),
        )
        assertEquals(listOf("1", "3"), boostAccounts(accounts).map { it.id })
    }

    @Test
    fun `only hosted fediverse accounts follow a person there`() {
        val accounts = listOf(
            PublishAccount(id = "1", type = "fediverse", hosted = true),
            PublishAccount(id = "2", type = "fediverse", hosted = false),
            PublishAccount(id = "3", type = "bluesky", hosted = true),
        )
        assertEquals(listOf("1"), hostedFediverse(accounts).map { it.id })
    }

    @Test
    fun `a commenter is from another network when their id holds a colon`() {
        assertTrue(outsider(Comment(id = "c", user = "https://there.example/users/a")))
        assertFalse(outsider(Comment(id = "c", user = "1abcdef")))
    }

    @Test
    fun `a poll may be sent with two to eight options, every one written and no two alike`() {
        assertTrue(pollValid(null))
        assertTrue(pollValid(PollDraft(options = listOf("Tea", "Coffee"))))
        assertFalse(pollValid(PollDraft(options = listOf("Tea"))))
        assertFalse(pollValid(PollDraft(options = listOf("Tea", " "))))
        assertFalse(pollValid(PollDraft(options = listOf("Tea", "tea "))))
        assertFalse(pollValid(PollDraft(options = List(9) { "Option $it" })))
    }

    @Test
    fun `a post's custom emoji are drawn a line high, and a name it does not carry stays as written`() {
        val emoji = listOf(PostEmoji(name = "blob", url = "https://there.example/blob.png"), PostEmoji(name = "bad", url = "javascript:x"))
        assertEquals(
            "Hi <img src=\"https://there.example/blob.png\" alt=\":blob:\" width=\"1.2em\" height=\"1.2em\"> :other: :bad:",
            withEmoji("Hi :blob: :other: :bad:", emoji),
        )
        assertEquals("Hi :blob:", withEmoji("Hi :blob:", null))
    }

    @Test
    fun `a blurhash decodes to its average colour, and anything else to nothing`() {
        // The reference blurhash of a single flat colour: one component.
        val pixels = decodeBlurhash("00TI7A", 4, 4)!!
        assertEquals(16, pixels.size)
        assertTrue(pixels.all { it == pixels[0] })
        assertEquals(0xFF, pixels[0] ushr 24)
        assertTrue(isBlurhash("LEHV6nWB2yk8pyo0adR*.7kCMdnj"))
        assertFalse(isBlurhash("LEHV6nWB2yk8pyo0adR*.7kCMdn"))
        assertNull(decodeBlurhash("nope", 4, 4))
    }

    @Test
    fun `a search is looked up elsewhere when it is a handle, a did, a link or a domain`() {
        assertTrue(looksUpElsewhere("@alice@there.example"))
        assertTrue(looksUpElsewhere("alice@there.example"))
        assertTrue(looksUpElsewhere("!books@lemmy.example"))
        assertTrue(looksUpElsewhere("did:plc:abc"))
        assertTrue(looksUpElsewhere("https://there.example/@alice"))
        assertTrue(looksUpElsewhere("alice.bsky.social"))
        assertFalse(looksUpElsewhere("cats and dogs"))
        assertFalse(looksUpElsewhere("cooking"))
        assertFalse(looksUpElsewhere("mochi://peer/feed"))
        assertFalse(looksUpElsewhere("https://here.example/feeds/abc"))
    }

    @Test
    fun `a source read on a timer may be read now, and a follow through an account may not`() {
        assertTrue(pollable(Source(type = "rss")))
        assertTrue(pollable(Source(type = "tag/posts")))
        assertTrue(pollable(Source(type = "feed/memories")))
        assertTrue(pollable(Source(type = "person/posts")))
        assertFalse(pollable(Source(type = "person/posts", account = "a")))
        assertFalse(pollable(Source(type = "feed/posts")))
        assertFalse(pollable(Source(type = "account/timeline")))
    }
}
