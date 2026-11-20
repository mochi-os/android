// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every transport posts a push through [postPushNotification]: one channel,
 * icon and activity resolver, one URI, one fallback tag, and a tap nonce kept
 * under the notification's tag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushDeliveryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val screen = Any()

    /** An app id as a server running published apps sends it: an entity id, not a slug. */
    private val published = "12254aHfG39LqrizXg8Wv7"

    @Before
    fun begin() {
        manager.cancelAll()
        shadowOf(manager).setNotificationsEnabled(true)
        context.getSharedPreferences("mochi_notification_taps", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun end() {
        VisibleEntity.hide(screen)
    }

    private fun push(tag: String = "t1", link: String = "/chat/c1", app: String = published, account: String? = "a1") =
        Push(title = "Ann", body = "Hello", link = link, tag = tag, app = app, id = "n1", account = account)

    private fun post(push: Push, live: Boolean = false, read: MutableList<String> = mutableListOf()) =
        postPushNotification(context, push, { live }, { id -> read += id })

    private fun posted(tag: String): Notification? = shadowOf(manager).getNotification(tag, tag.hashCode())

    private fun tap(notification: Notification): Intent = shadowOf(notification.contentIntent).savedIntent

    private fun query(intent: Intent): Map<String, String> {
        val part = intent.data!!.encodedSchemeSpecificPart
        assertEquals("notification", part.substringBefore('?'))
        return part.substringAfter('?').split('&').associate { pair ->
            pair.substringBefore('=') to Uri.decode(pair.substringAfter('='))
        }
    }

    /** A published app's id is no slug: the link names the app. */
    @Test
    fun `a push from a published app is routed by its link`() {
        post(push())
        val notification = posted("t1")!!
        assertEquals("chat", notification.channelId)
        assertEquals("${context.packageName}.MochiChatLauncher", tap(notification).component?.className)
    }

    @Test
    fun `the notification carries its link and account for cancelling`() {
        post(push())
        val extras = posted("t1")!!.extras
        assertEquals("/chat/c1", extras.getString(EXTRA_LINK))
        assertEquals("a1", extras.getString(EXTRA_ACCOUNT))
    }

    @Test
    fun `the tap opens the link with a nonce kept under the tag`() {
        post(push(link = "/chat/c1?x=1&y"))
        val tap = tap(posted("t1")!!)
        assertEquals(Intent.ACTION_VIEW, tap.action)
        assertEquals("mochi", tap.data!!.scheme)
        val query = query(tap)
        assertEquals("/chat/c1?x=1&y", query["link"])
        assertEquals("n1", query["id"])
        val nonces = NonceStore(context)
        assertTrue(nonces.consume(query["nonce"]))
        assertFalse("single use", nonces.consume(query["nonce"]))
    }

    /** The server posts one tag per thread; each post replaces the last, which fires no delete intent. */
    @Test
    fun `posting a tag again retires the replaced notification's nonce`() {
        post(push())
        val first = query(tap(posted("t1")!!))["nonce"]
        post(push())
        val second = query(tap(posted("t1")!!))["nonce"]
        val nonces = NonceStore(context)
        assertFalse(nonces.consume(first))
        assertTrue(nonces.consume(second))
    }

    @Test
    fun `a push with notifications off posts nothing and issues no nonce`() {
        shadowOf(manager).setNotificationsEnabled(false)
        post(push())
        assertNull(posted("t1"))
        val stored = context.getSharedPreferences("mochi_notification_taps", Context.MODE_PRIVATE)
            .getString("nonces", "").orEmpty()
        assertEquals(emptyList<Outstanding>(), noncesFromStored(stored))
    }

    /** The two transports once disagreed: the UnifiedPush instance in one, "mochi" in the other. */
    @Test
    fun `a push with no tag posts under the one fallback tag`() {
        post(push(tag = ""))
        assertTrue(posted(FALLBACK_TAG) != null)
    }

    @Test
    fun `a push about the chat on screen is not posted, and is read`() {
        VisibleEntity.show(screen, "chat", "c1", true, "socket")
        val read = mutableListOf<String>()
        post(push(), live = true, read = read)
        assertNull(posted("t1"))
        assertEquals(listOf("n1"), read)
    }

    @Test
    fun `the notification URI is opaque and encodes each value`() {
        val uri = notificationUri("/feeds/f1?a=b&c", "i&d", "n=1")
        assertEquals("mochi:notification?link=%2Ffeeds%2Ff1%3Fa%3Db%26c&id=i%26d&nonce=n%3D1", uri.toString())
        assertEquals("mochi:notification?link=%2Ffeeds&nonce=n", notificationUri("/feeds", null, "n").toString())
    }
}
