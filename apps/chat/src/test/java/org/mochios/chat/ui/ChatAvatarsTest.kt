// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.ui.chat.MessageBubble
import org.mochios.chat.ui.chat.messageAvatars
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A sender's avatar route names one of their messages, so a group chat that
 * built each message's own address had the image cache fetch and store the
 * same picture once per message.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class ChatAvatarsTest {

    @get:Rule
    val rule = createComposeRule()

    private val loaded = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        val context = RuntimeEnvironment.getApplication()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    loaded.add(chain.request.data.toString())
                    ErrorResult(null, chain.request, IllegalStateException("no images in tests"))
                })
            }.build()
        )
    }

    @After
    fun end() = SingletonImageLoader.reset()

    private val messages = listOf("ann", "bob", "ann", "bob", "ann").mapIndexed { n, member ->
        ChatMessage(id = "m$n", member = member, name = member, body = "Message $n", created = n.toLong())
    }

    @Test
    fun `each sender gets their first message's address`() {
        assertEquals(
            mapOf("ann" to "/chat/c1/-/m0/asset/avatar", "bob" to "/chat/c1/-/m1/asset/avatar"),
            messageAvatars(messages, "c1"),
        )
    }

    // The server refuses a message id that is not 32 lowercase letters and
    // digits, so none reaches the phone today; the address still escapes one,
    // since a slash or a question mark would send the request, with the
    // session, to whatever action the path then names.
    @Test
    fun `a message id cannot point the avatar at another action`() {
        val hostile = listOf("../../people/-/x", "m1?x=1").map { ChatMessage(id = it, member = "ann", name = "ann", body = "Hi") }
        // The server decodes the path before it routes, so a slash gets no
        // address at all, and the sender's next message stands in.
        assertEquals(mapOf("ann" to "/chat/c1/-/m1%3Fx%3D1/asset/avatar"), messageAvatars(hostile, "c1"))
    }

    // The URL resolves "." and ".." away, escaped or not.
    @Test
    fun `a message whose id is a dot segment does not stand in, and the sender's next one does`() {
        val dotted = listOf("..", ".", "m2").map { ChatMessage(id = it, member = "ann", name = "ann", body = "Hi") }
        assertEquals(mapOf("ann" to "/chat/c1/-/m2/asset/avatar"), messageAvatars(dotted, "c1"))
    }

    @Test
    fun `a group's messages ask for each sender's avatar once`() {
        val avatars = messageAvatars(messages, "c1")
        rule.setContent {
            Column {
                messages.forEach { message ->
                    MessageBubble(
                        message = message,
                        isOwn = false,
                        receipt = null,
                        seen = null,
                        onThread = null,
                        isGroup = true,
                        chatId = "c1",
                        selectionMode = false,
                        isSelected = false,
                        isSearchMatch = false,
                        searchQuery = "",
                        replyToMessage = null,
                        onStartSelect = {},
                        onToggleSelect = {},
                        onReply = {},
                        onDelete = {},
                        onReact = {},
                        onForward = {},
                        onEdit = {},
                        avatar = avatars[message.member],
                    )
                }
            }
        }
        rule.waitUntil(5_000) { loaded.size >= messages.size }
        rule.waitForIdle()
        assertEquals(listOf("/chat/c1/-/m0/asset/avatar", "/chat/c1/-/m1/asset/avatar"), loaded.toSet().sorted())
    }
}
