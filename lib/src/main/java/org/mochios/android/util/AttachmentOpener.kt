// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody
import org.mochios.android.api.AssetHttpEntryPoint
import org.mochios.android.model.Attachment

/**
 * Downloads a session-gated chat / feed attachment to the app cache and hands it
 * to the system viewer via [AttachmentFileProvider]. Images and videos already
 * have dedicated in-app viewers; everything else (PDF, plain text, office
 * documents, archives, …) is opened with whichever installed app claims the
 * file's MIME type.
 */
object AttachmentOpener {

    private const val TAG = "AttachmentOpener"

    private const val AUTHORITY_SUFFIX = ".attachments"

    private const val CACHE_DIR = "attachments"

    /** Outcome of an [open] attempt, so the caller can surface the right message. */
    enum class OpenResult { OPENED, NO_APP, FAILED }

    /**
     * Fetches [url] through the authenticated asset client, caches it under
     * `cacheDir/attachments/`, and launches an `ACTION_VIEW` for [attachment].
     *
     * Safe to call from the main thread — the network and disk work runs on
     * [Dispatchers.IO]; only the activity launch happens on the caller's context.
     *
     * @return [OpenResult.OPENED] when a viewer was launched,
     *   [OpenResult.NO_APP] when no installed app handles the MIME type, or
     *   [OpenResult.FAILED] when the download itself failed.
     */
    suspend fun open(context: Context, url: String, attachment: Attachment): OpenResult {
        val file = try {
            withContext(Dispatchers.IO) { download(context, url, attachment) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download ${attachment.name}: ${e.message}")
            return OpenResult.FAILED
        }

        return launch(context, file, mimeType(attachment))
    }

    /**
     * Cache [body] under [fileName] and open it, for callers that already hold
     * the bytes rather than a URL.
     *
     * The market's purchased-asset download needs this: its endpoint answers
     * either a JSON envelope or the file itself depending on where the seller
     * hosts it, so the caller has to make the request, read the content type
     * and only then decide what to do — by which point the bytes are in hand.
     *
     * [mime] is coerced the same way as for an attachment, so a hostile or
     * absent type cannot turn into something the viewer will execute.
     */
    suspend fun openBytes(
        context: Context,
        fileName: String,
        mime: String?,
        body: ResponseBody,
    ): OpenResult {
        val file = try {
            withContext(Dispatchers.IO) { cacheBytes(context, fileName, body) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save $fileName: ${e.message}")
            return OpenResult.FAILED
        }
        return launch(context, file, coerceMimeType(mime.orEmpty()))
    }

    /**
     * Write [body] into the attachment cache under a sanitised [fileName] and
     * return the file, for a caller that wants to save now and open later.
     *
     * Blocking: call it off the main thread.
     */
    fun cacheBytes(context: Context, fileName: String, body: ResponseBody): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val safe = fileName.replace(UNSAFE_FILENAME, "_").takeLast(120).ifBlank { "download" }
        val target = File(dir, safe)
        body.use { source ->
            target.outputStream().use { out -> source.byteStream().copyTo(out) }
        }
        return target
    }

    /**
     * Open a file already written by [cacheBytes]. [mime] is coerced the same
     * way as for an attachment, so an absent or hostile type cannot become
     * something the viewer will execute.
     */
    fun openCached(context: Context, fileName: String, mime: String?): OpenResult {
        val file = File(File(context.cacheDir, CACHE_DIR), fileName)
        if (!file.exists()) return OpenResult.FAILED
        return launch(context, file, coerceMimeType(mime.orEmpty()))
    }

    /** Hand [file] to a viewer through the FileProvider. */
    private fun launch(context: Context, file: File, mime: String): OpenResult {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(view)
            OpenResult.OPENED
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can open ${file.name} ($mime)")
            OpenResult.NO_APP
        }
    }

    /** Anything outside this class is replaced before a value reaches a path. */
    private val UNSAFE_FILENAME = Regex("[^A-Za-z0-9._-]")

    private fun download(context: Context, url: String, attachment: Attachment): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val target = File(dir, cacheName(attachment))
        // Reuse an already-downloaded copy when its size matches the server's.
        if (target.exists() && attachment.size > 0 && target.length() == attachment.size) {
            return target
        }
        val client = EntryPointAccessors
            .fromApplication(context.applicationContext, AssetHttpEntryPoint::class.java)
            .assetHttpClient()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        return target
    }

    /**
     * Stable per-attachment cache filename, prefixed with the id to avoid
     * collisions.
     *
     * The id is sanitised on the same terms as the name. It arrives from the
     * server and is interpolated into a path that is written to before the
     * FileProvider call that would reject an escaping one, so a `../`-bearing
     * id would be an arbitrary write inside the app sandbox. Mochi's own server
     * cannot emit one — mochi.attachment.store gates a peer-supplied id against
     * ^[0-9a-z]{32}$ — but the user can point this app at any server, so the
     * client should not be relying on that.
     */
    private fun cacheName(attachment: Attachment): String {
        val safe = attachment.name.ifBlank { attachment.id }
            .replace(UNSAFE_FILENAME, "_")
            .takeLast(100)
        val id = attachment.id.replace(UNSAFE_FILENAME, "_").takeLast(64)
        return "${id}_$safe"
    }

    /**
     * The server MIME type when present, otherwise inferred from the filename —
     * either way reduced to something safe to view. Both branches are
     * attacker-controlled: the type comes from the peer unvalidated, and so
     * does the filename the extension is read from. See [coerceMimeType].
     */
    private fun mimeType(attachment: Attachment): String {
        val stated = attachment.type.ifBlank {
            val ext = attachment.name.substringAfterLast('.', "").lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext).orEmpty()
        }
        return coerceMimeType(stated)
    }
}
