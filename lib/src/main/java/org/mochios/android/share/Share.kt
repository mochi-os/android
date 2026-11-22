// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.net.URLEncoder
import java.util.UUID

// Sharing something an app holds hands over a copy of it: its words and the
// address of its page, through the system share sheet, with nothing tying
// what is sent back to where it came from, so a later edit never reaches an
// earlier share. Mochi is one of the sheet's destinations (ShareActivity),
// and posts it to a feed or sends it in a chat.

/**
 * What an app shares: its [text], the address [url] of its page, and for a
 * whole feed, forum, wiki, project or CRM the mochi:// [link] another server
 * joins it by, which is what other apps receive in its place.
 */
data class Shared(
    val text: String,
    val url: String = "",
    val link: String = "",
)

/** The words of a share as Mochi's own destination reads them, beside [Intent.EXTRA_TEXT]. */
const val EXTRA_WORDS = "org.mochios.share.WORDS"

/** The address of the shared item's page, as Mochi's own destination reads it. */
const val EXTRA_ADDRESS = "org.mochios.share.ADDRESS"

/**
 * A share as one message: its words, then after a blank line its address
 * where the words do not already hold it, as the web's shareMessage writes it.
 */
fun shareMessage(text: String, url: String): String {
    val words = text.trim()
    if (url.isEmpty() || words.contains(url)) return words
    return if (words.isEmpty()) url else "$words\n\n$url"
}

/**
 * The system share sheet for [shared]. Other apps receive its message, or the
 * mochi:// link of a whole entity; Mochi's own destination reads the words
 * and the address apart. Its first line names the sheet's preview: Android 10
 * and later ignore a chooser's title, and read "Sharing text" without one.
 */
fun chooser(shared: Shared): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shared.link.ifEmpty { shareMessage(shared.text, shared.url) })
        shared.text.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { title ->
            putExtra(Intent.EXTRA_TITLE, title)
        }
        putExtra(EXTRA_WORDS, shared.text)
        putExtra(EXTRA_ADDRESS, shared.url)
    }
    return Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Open the system share sheet for [shared]. */
fun share(context: Context, shared: Shared) {
    context.startActivity(chooser(shared))
}

/**
 * Open the system share sheet for each share [shares] sends: what a view model
 * readies once it has read what it needs, such as a whole entity's link.
 */
@Composable
fun ShareSheet(shares: Flow<Shared>) {
    val context = LocalContext.current
    LaunchedEffect(shares) {
        shares.collect { shared -> share(context, shared) }
    }
}

/**
 * Feeds' new post screen, filled in with [text] and then [url] beneath it,
 * with the images [SharedImages] holds under [images]. Nothing is posted until
 * the user posts it.
 */
fun feedShareLink(text: String, url: String, images: String = ""): String = link("feeds/share", text, url, images)

/**
 * Chat's choice of chat, whose message box the chosen one opens filled in
 * with [text] and [url], and with the images [SharedImages] holds under
 * [images]. Nothing is sent until the user sends it.
 */
fun chatShareLink(text: String, url: String, images: String = ""): String = link("chat/share", text, url, images)

private fun link(base: String, text: String, url: String, images: String): String {
    val query = listOfNotNull(
        text.takeIf { it.isNotEmpty() }?.let { "text=" + encode(it) },
        url.takeIf { it.isNotEmpty() }?.let { "url=" + encode(it) },
        images.takeIf { it.isNotEmpty() }?.let { "images=" + encode(it) },
    )
    return base + if (query.isEmpty()) "" else "?" + query.joinToString("&")
}

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

/**
 * Images shared to Mochi on their way to the composer that takes them, kept
 * on disk so a share outlives the process, as it does when signing in comes
 * between: each share's copies sit in a folder of this app's cache named by a
 * random token, which the share's link carries. A link names only a token, so
 * one from anywhere else, a notification's included, can name no file of its
 * own; a share is taken once, and its files go once the composer that took
 * them lets them go. Anything left a day, a share never taken or one whose
 * composer's process died, is cleared as the next share is taken.
 */
object SharedImages {
    /** How long a share's files are kept, whether taken or not. */
    internal const val KEPT = 24 * 60 * 60 * 1000L

    /** A token as [hold] makes one: nothing that could name another folder. */
    private val TOKEN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    /** The cache folder shares are kept in. */
    fun folder(context: Context): File = File(context.cacheDir, "shares")

    private fun taken(context: Context, token: String): File = File(folder(context), "$token.taken")

    /**
     * Copy [images] into a share of their own, each named by its place and
     * its type, never by the name its source gives it; answers the share's
     * token, or "" when none could be read.
     */
    fun hold(context: Context, images: List<Uri>): String {
        if (images.isEmpty()) return ""
        val token = UUID.randomUUID().toString()
        val share = File(folder(context), token)
        if (!share.mkdirs()) return ""
        val resolver = context.contentResolver
        images.forEachIndexed { index, image ->
            val extension = resolver.getType(image)?.let { type -> MimeTypeMap.getSingleton().getExtensionFromMimeType(type) }
            val file = File(share, "image-${index + 1}" + if (extension.isNullOrEmpty()) "" else ".$extension")
            // Another app's provider can fail any way it likes; that image is left out.
            val copied = try {
                resolver.openInputStream(image)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } } != null
            } catch (_: Exception) {
                false
            }
            if (!copied) file.delete()
        }
        if (share.listFiles().isNullOrEmpty()) {
            share.deleteRecursively()
            return ""
        }
        return token
    }

    /**
     * The files of the share [token] names, once, for this app's composers
     * to read; none for a token not held or already taken. Each keeps the
     * extension of its type, which names it and its upload. The composer
     * [release]s them when it is done.
     */
    fun take(context: Context, token: String?): List<Uri> {
        sweep(context)
        if (token == null || !TOKEN.matches(token)) return emptyList()
        val held = File(folder(context), token)
        val taken = taken(context, token)
        if (!held.isDirectory || !held.renameTo(taken)) return emptyList()
        // Kept a day from now, while the composer has it.
        taken.setLastModified(System.currentTimeMillis())
        return taken.listFiles().orEmpty().sortedBy { file -> file.name }.map { file -> Uri.fromFile(file) }
    }

    /** Delete the files the share [token] named, once the composer that took them is done. */
    fun release(context: Context, token: String?) {
        if (token == null || !TOKEN.matches(token)) return
        taken(context, token).deleteRecursively()
    }

    /** Clear every share left longer than [KEPT]. */
    private fun sweep(context: Context) {
        val now = System.currentTimeMillis()
        folder(context).listFiles()?.forEach { share ->
            if (now - share.lastModified() > KEPT) share.deleteRecursively()
        }
    }
}
