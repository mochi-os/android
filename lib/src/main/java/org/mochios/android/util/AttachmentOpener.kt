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

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
        val mime = mimeType(attachment)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(view)
            OpenResult.OPENED
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can open ${attachment.name} ($mime)")
            OpenResult.NO_APP
        }
    }

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

    /** Stable per-attachment cache filename, prefixed with the id to avoid collisions. */
    private fun cacheName(attachment: Attachment): String {
        val safe = attachment.name.ifBlank { attachment.id }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeLast(100)
        return "${attachment.id}_$safe"
    }

    /** The server MIME type when present, otherwise inferred from the filename. */
    private fun mimeType(attachment: Attachment): String {
        if (attachment.type.isNotBlank()) return attachment.type
        val ext = attachment.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}
