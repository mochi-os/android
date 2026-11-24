// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The app cancels tray rows itself when a screen shows what they are about
 * and when everything is read. Each must take exactly the push rows meant,
 * and their tap nonces with them, and leave the rest of the tray alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemNotificationsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** An app id as a server running published apps sends it, in its tags too. */
    private val published = "12254aHfG39LqrizXg8Wv7"

    @Before
    fun begin() {
        manager.cancelAll()
        shadowOf(manager).setNotificationsEnabled(true)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("mochi_notification_taps", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun post(tag: String, link: String, account: String? = "a1") = postPushNotification(
        context,
        Push(title = "t", body = "b", link = link, tag = tag, app = published, id = "", account = account),
        { false },
        {},
    )

    /** A row that is not a push: the sign-in prompt, say. */
    private fun other(tag: String) {
        val notification = NotificationCompat.Builder(context, "other")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("x")
            .build()
        manager.notify(tag, 1, notification)
    }

    private fun tags(): Set<String> = manager.activeNotifications.mapNotNull { row -> row.tag }.toSet()

    private fun nonce(tag: String): String? {
        val row: Notification = manager.activeNotifications.first { it.tag == tag }.notification
        val part = shadowOf(row.contentIntent).savedIntent.data!!.encodedSchemeSpecificPart
        return part.substringAfter('?').split('&')
            .first { pair -> pair.startsWith("nonce=") }
            .let { pair -> Uri.decode(pair.substringAfter('=')) }
    }

    /** The server's tag names the app by id and the post, never the feed a screen shows. */
    @Test
    fun `a screen clears the rows whose link lies under what it shows`() {
        post("$published-post-p1", "/feeds/f1/p1")
        post("$published-post-p2", "/feeds/f1")
        post("$published-post-p3", "/feeds/f2/p3")
        post("$published-page-x", "/wikis/f1/x")
        other("prompt")
        val spent = nonce("$published-post-p1")

        SystemNotifications.cancelFor(context, "feeds", "f1")

        assertEquals(setOf("$published-post-p3", "$published-page-x", "prompt"), tags())
        assertFalse("its nonce is retired", NonceStore(context).consume(spent))
        assertTrue("another row's is not", NonceStore(context).consume(nonce("$published-post-p3")))
    }

    @Test
    fun `a screen naming more segments clears only what lies under them`() {
        post("$published-message-t1", "/market/c1/l1/t1")
        post("$published-message-t2", "/market/c1/l1/t2")
        SystemNotifications.cancelFor(context, "market", "c1/l1/t1")
        assertEquals(setOf("$published-message-t2"), tags())
    }

    @Test
    fun `a blank app or path clears nothing`() {
        post("$published-post-p1", "/feeds/f1/p1")
        SystemNotifications.cancelFor(context, "", "f1")
        SystemNotifications.cancelFor(context, "feeds", "")
        assertEquals(setOf("$published-post-p1"), tags())
    }

    /**
     * Read everywhere: the signed-in account's pushes go, and FCM's, which
     * are only ever that account's. The sign-in prompt is posted once, so
     * clearing it would leave a dead session unannounced.
     */
    @Test
    fun `everything read clears the account's pushes and nothing else`() {
        post("mine", "/chat/c1", account = "a1")
        post("fcm", "/chat/c2", account = null)
        post("theirs", "/chat/c3", account = "a2")
        other("a1")
        val spent = nonce("mine")

        SystemNotifications.cancelPushes(context, "a1")

        assertEquals(setOf("theirs", "a1"), tags())
        assertFalse(NonceStore(context).consume(spent))
    }

    @Test
    fun `cancelling a tag retires its nonce`() {
        post("t1", "/chat/c1")
        val spent = nonce("t1")
        SystemNotifications.cancel(context, "t1")
        assertEquals(emptySet<String>(), tags())
        assertFalse(NonceStore(context).consume(spent))
    }

    @Test
    fun `a link lies under the path of its app alone`() {
        assertTrue(linkUnder("/feeds/f1/p1", "feeds", listOf("f1")))
        assertTrue(linkUnder("/feeds/f1", "feeds", listOf("f1")))
        assertFalse(linkUnder("/feeds/f10", "feeds", listOf("f1")))
        assertFalse(linkUnder("/wikis/f1", "feeds", listOf("f1")))
        assertFalse(linkUnder("/feeds", "feeds", listOf("f1")))
        assertFalse(linkUnder("/feeds/f1", "feeds", listOf("f1", "p1")))
    }
}
