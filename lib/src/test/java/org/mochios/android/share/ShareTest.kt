// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.share

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Sharing through the system share sheet: what other apps receive and what
 * Mochi's own destination reads apart; and that destination, Mochi in the
 * sheet, reading what it is handed and opening Feeds' new post or Chat's
 * choice of chat with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ShareTest {

    @get:Rule
    val rule = createComposeRule()

    private val application = RuntimeEnvironment.getApplication()
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    /** The next activity started that [wanted] picks; the test's own host activity is passed over. */
    private fun started(wanted: (Intent) -> Boolean): Intent? {
        while (true) {
            val intent = shadowOf(application).nextStartedActivity ?: return null
            if (wanted(intent)) return intent
        }
    }

    /** What the system share sheet opened last was opened for. */
    private fun sheet(): Intent {
        val chooser = started { it.action == Intent.ACTION_CHOOSER }
        assertNotNull("the share sheet opens", chooser)
        return IntentCompat.getParcelableExtra(chooser!!, Intent.EXTRA_INTENT, Intent::class.java)!!
    }

    /** The app launch [deliver] made last. */
    private fun launched(): Intent = started { it.action == Intent.ACTION_VIEW }!!

    @Test
    fun `an item's words go before its address, a blank line between, and the address once`() {
        assertEquals("Stand-up\n\nhttps://here.example/e", shareMessage("Stand-up\n", "https://here.example/e"))
        assertEquals("https://here.example/e", shareMessage("", "https://here.example/e"))
        assertEquals("Stand-up", shareMessage(" Stand-up ", ""))
        assertEquals("See https://here.example/e", shareMessage("See https://here.example/e", "https://here.example/e"))
    }

    @Test
    fun `an item is shared by its words and address, which Mochi reads apart`() {
        share(activity, Shared(text = "Teapot\n£12.00\n\nA blue pot", url = "https://here.example/market/listings/l1"))
        val send = sheet()
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("Teapot\n£12.00\n\nA blue pot\n\nhttps://here.example/market/listings/l1", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Teapot", send.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("Teapot\n£12.00\n\nA blue pot", send.getStringExtra(EXTRA_WORDS))
        assertEquals("https://here.example/market/listings/l1", send.getStringExtra(EXTRA_ADDRESS))
    }

    @Test
    fun `a whole entity goes to other apps as its join link, and to Mochi by name and page`() {
        share(activity, Shared(text = "Books", url = "https://here.example/forums/fp1", link = "mochi://12D3KooWPeer/f1"))
        val send = sheet()
        assertEquals("mochi://12D3KooWPeer/f1", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Books", send.getStringExtra(EXTRA_WORDS))
        assertEquals("https://here.example/forums/fp1", send.getStringExtra(EXTRA_ADDRESS))
    }

    @Test
    fun `a share a view model readies opens the sheet`() {
        val shares = MutableSharedFlow<Shared>(extraBufferCapacity = 1)
        rule.setContent { ShareSheet(shares) }
        rule.waitForIdle()
        shares.tryEmit(Shared(text = "Recipes", url = "https://here.example/wikis/w1", link = "mochi://12D3KooWPeer/w1"))
        rule.waitForIdle()
        assertEquals("mochi://12D3KooWPeer/w1", sheet().getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `the links carry the words, the address and the images' token escaped`() {
        assertEquals("feeds/share?text=Tea+%26+cake&url=https%3A%2F%2Fhere.example%2Fe%3Fa%3D1", feedShareLink("Tea & cake", "https://here.example/e?a=1"))
        assertEquals("chat/share?text=Hi&images=t1", chatShareLink("Hi", "", "t1"))
        assertEquals("chat/share", chatShareLink("", ""))
    }

    /** [uri] readable as [bytes], as another app's provider would serve it. */
    private fun served(uri: String, bytes: String): Uri {
        val image = Uri.parse(uri)
        shadowOf(application.contentResolver).registerInputStream(image, ByteArrayInputStream(bytes.toByteArray()))
        return image
    }

    @Test
    fun `a share is kept on disk by its token alone, so it outlives the process, and is taken once`() {
        // The source's name could climb out of the share's folder; it is never used.
        val image = served("content://photos.example/..%2F..%2Fevil.sh", "pixels")
        val token = SharedImages.hold(application, listOf(image))
        val held = File(SharedImages.folder(application), token)
        assertEquals(listOf("image-1"), held.list().orEmpty().toList())
        assertEquals("pixels", File(held, "image-1").readText())
        // Nothing of the share is kept in memory: a process started afresh
        // has only the token the link carries, and the disk.
        val taken = SharedImages.take(application, token)
        assertEquals(1, taken.size)
        assertEquals(File(SharedImages.folder(application), "$token.taken/image-1"), File(taken.single().path!!))
        assertEquals("pixels", File(SharedImages.folder(application), "$token.taken/image-1").readText())
        assertEquals(emptyList<Uri>(), SharedImages.take(application, token))
        assertEquals("", SharedImages.hold(application, emptyList()))
        assertEquals("", SharedImages.hold(application, listOf(Uri.parse("content://photos.example/gone"))))
    }

    @Test
    fun `taken files go once the composer lets them go`() {
        val token = SharedImages.hold(application, listOf(served("content://photos.example/1", "pixels")))
        SharedImages.take(application, token)
        SharedImages.release(application, token)
        assertEquals(emptyList<String>(), SharedImages.folder(application).list().orEmpty().filter { it.startsWith(token) })
    }

    @Test
    fun `a token names no folder but a share`() {
        // The shares' folder exists, as it does once anything was shared, so a climb out of it resolves.
        SharedImages.folder(application).mkdirs()
        val secret = File(application.cacheDir, "secret").apply { mkdirs() }
        File(secret, "key").writeText("private")
        assertEquals(emptyList<Uri>(), SharedImages.take(application, "../secret"))
        assertEquals(emptyList<Uri>(), SharedImages.take(application, "forged"))
        assertEquals(emptyList<Uri>(), SharedImages.take(application, null))
        assertTrue(File(secret, "key").exists())
    }

    @Test
    fun `a share left a day is cleared as the next one is taken`() {
        val old = SharedImages.hold(application, listOf(served("content://photos.example/1", "old")))
        val fresh = SharedImages.hold(application, listOf(served("content://photos.example/2", "fresh")))
        File(SharedImages.folder(application), old).setLastModified(System.currentTimeMillis() - SharedImages.KEPT - 60_000)
        assertEquals(1, SharedImages.take(application, fresh).size)
        assertFalse(File(SharedImages.folder(application), old).exists())
        assertTrue(File(SharedImages.folder(application), "$fresh.taken").exists())
    }

    private fun send(type: String = "text/plain", setup: Intent.() -> Unit = {}): Intent =
        Intent(Intent.ACTION_SEND).apply {
            this.type = type
            setup()
        }

    @Test
    fun `text alone is shared as its words`() {
        assertEquals(Received("Lunch at one?"), received(application, send { putExtra(Intent.EXTRA_TEXT, " Lunch at one? ") }))
    }

    @Test
    fun `a page address alone is the address, its title the words`() {
        assertEquals(
            Received("", "https://news.example/a?b=1"),
            received(application, send { putExtra(Intent.EXTRA_TEXT, "https://news.example/a?b=1") }),
        )
        assertEquals(
            Received("A headline", "https://news.example/a"),
            received(application, send {
                putExtra(Intent.EXTRA_SUBJECT, "A headline")
                putExtra(Intent.EXTRA_TEXT, "https://news.example/a")
            }),
        )
    }

    @Test
    fun `a subject is the first line, unless the text holds it`() {
        assertEquals(
            Received("Minutes\nWe agreed on Tuesday."),
            received(application, send {
                putExtra(Intent.EXTRA_SUBJECT, "Minutes")
                putExtra(Intent.EXTRA_TEXT, "We agreed on Tuesday.")
            }),
        )
        assertEquals(
            Received("Minutes: we agreed on Tuesday."),
            received(application, send {
                putExtra(Intent.EXTRA_SUBJECT, "Minutes")
                putExtra(Intent.EXTRA_TEXT, "Minutes: we agreed on Tuesday.")
            }),
        )
        assertEquals(Received("Minutes"), received(application, send { putExtra(Intent.EXTRA_SUBJECT, "Minutes") }))
    }

    @Test
    fun `Mochi's own share is read by its words and address, not the join link`() {
        val shared = send {
            putExtra(Intent.EXTRA_TEXT, "mochi://12D3KooWPeer/f1")
            putExtra(EXTRA_WORDS, "Books")
            putExtra(EXTRA_ADDRESS, "https://here.example/forums/fp1")
        }
        assertEquals(Received("Books", "https://here.example/forums/fp1"), received(application, shared))
    }

    @Test
    fun `images are read from the stream or the streams, each once`() {
        val first = Uri.parse("content://photos.example/1")
        val second = Uri.parse("content://photos.example/2")
        val one = send("image/jpeg") { putExtra(Intent.EXTRA_STREAM, first) }
        assertEquals(Received("", images = listOf(first)), received(application, one))
        val many = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            clipData = ClipData.newRawUri(null, first).apply { addItem(ClipData.Item(second)) }
            putExtra(Intent.EXTRA_TEXT, "The garden")
        }
        assertEquals(Received("The garden", images = listOf(first, second)), received(application, many))
    }

    @Test
    fun `a file Mochi alone may read is never taken`() {
        val providers = application.packageManager.getPackageInfo(application.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
        val own = providers.first().authority.split(';').first()
        val shared = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(Uri.parse("content://$own/attachments/secret"), Uri.parse("file:///data/data/x"), Uri.parse("content://photos.example/1")),
            )
        }
        assertEquals(listOf(Uri.parse("content://photos.example/1")), received(application, shared)?.images)
    }

    @Test
    fun `a share with nothing in it, or not a share, is not read`() {
        assertNull(received(application, send()))
        assertNull(received(application, send("text/plain") { putExtra(Intent.EXTRA_TEXT, "  ") }))
        assertNull(received(application, Intent(Intent.ACTION_VIEW).apply { putExtra(Intent.EXTRA_TEXT, "Hi") }))
    }

    /** The link the launch [intent] carries, from its nonce-gated notification URI. */
    private fun link(intent: Intent): String {
        val part = intent.data!!.encodedSchemeSpecificPart
        assertTrue(part, part.startsWith("notification?"))
        val query = part.substringAfter('?').split('&').associate { pair -> pair.substringBefore('=') to Uri.decode(pair.substringAfter('=')) }
        assertTrue("a nonce gates it", query["nonce"].orEmpty().isNotEmpty())
        return query.getValue("link")
    }

    @Test
    fun `Post to a feed opens Feeds' new post in Feeds, with the words, the address and the images`() {
        val image = served("content://photos.example/1", "pixels")
        assertTrue(deliver(activity, Received("Books", "https://here.example/forums/fp1", listOf(image)), Destination.FEED))
        val started = launched()
        assertEquals("${application.packageName}.MochiFeedsLauncher", started.component?.className)
        assertEquals("mochi", started.data?.scheme)
        val link = link(started)
        val token = Uri.parse("mochi:/$link").getQueryParameter("images").orEmpty()
        assertEquals(feedShareLink("Books", "https://here.example/forums/fp1", token), link)
        // The images were copied while the grant lasted: the composer reads this app's own copies.
        assertNull(started.clipData)
        assertEquals(1, SharedImages.take(application, token).size)
    }

    @Test
    fun `Send in a chat opens Chat's choice of chat, and text alone grants nothing`() {
        assertTrue(deliver(activity, Received("Lunch at one?"), Destination.CHAT))
        val started = launched()
        assertEquals("${application.packageName}.MochiChatLauncher", started.component?.className)
        assertEquals(chatShareLink("Lunch at one?", ""), link(started))
        assertNull(started.clipData)
        assertEquals(0, started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test
    fun `the chooser offers a feed and a chat`() {
        val chosen = mutableListOf<Destination>()
        rule.setContent {
            ShareChooser(onFeed = { chosen += Destination.FEED }, onChat = { chosen += Destination.CHAT }, onDismiss = {})
        }
        rule.onNodeWithText(application.getString(R.string.share_feed)).performClick()
        rule.onNodeWithText(application.getString(R.string.share_chat)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(Destination.FEED, Destination.CHAT), chosen)
    }
}
