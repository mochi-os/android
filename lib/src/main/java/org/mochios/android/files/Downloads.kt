// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Mime type of a file whose kind the app does not name. */
const val MIME_BINARY = "application/octet-stream"

/**
 * Saves a file the user's server holds into a document the user chose, through
 * the module's own client, so its credential never leaves the app: the system's
 * download service is handed nothing. A transfer runs in the application's
 * scope, so leaving the screen that started it does not end it, and its outcome
 * is shown when it ends, wherever the user is in the app.
 */
@Singleton
class Downloads @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fileStore: FileStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _running = MutableStateFlow<Set<Uri>>(emptySet())

    /** The documents being written now. */
    val running: StateFlow<Set<Uri>> = _running.asStateFlow()

    /**
     * Fetches the body [fetch] answers into the cache, then writes it into
     * [destination], named [name] in what the user is told. The document is
     * written only once the whole file is in hand, so a failed fetch leaves a
     * document the user chose to replace as it was. A failure deletes only
     * the empty document the save dialog made, which would otherwise look
     * like the file.
     */
    fun save(destination: Uri, name: String, fetch: suspend () -> ResponseBody): Job {
        _running.update { documents -> documents + destination }
        return scope.launch {
            show(context.getString(R.string.downloads_started, name))
            val made = fileStore.empty(destination)
            val failure = try {
                transfer(destination, fetch)
            } catch (e: CancellationException) {
                if (made) discard(destination)
                throw e
            } finally {
                _running.update { documents -> documents - destination }
            }
            if (failure == null) {
                show(context.getString(R.string.downloads_saved, name))
            } else {
                if (made) discard(destination)
                show(context.getString(R.string.downloads_failed, name, failure.userMessage()))
            }
        }
    }

    /** Null when the whole body was fetched into the cache and written to [destination], else why not. */
    private suspend fun transfer(destination: Uri, fetch: suspend () -> ResponseBody): MochiError? {
        val body = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return e.toMochiError()
        }
        val source = Watched(body.byteStream())
        val staged = try {
            File.createTempFile("download", null, context.cacheDir)
        } catch (_: IOException) {
            body.close()
            return MochiError.Local(R.string.common_file_save_failed)
        }
        try {
            val fetched = body.use {
                source.use { stream ->
                    try {
                        staged.outputStream().use { output -> stream.copyTo(output) }
                        true
                    } catch (_: IOException) {
                        false
                    }
                }
            }
            if (!fetched) {
                // The connection broke while the bytes came in, not the cache.
                return source.failure?.let { failure -> MochiError.NetworkError(failure) }
                    ?: MochiError.Local(R.string.common_file_save_failed)
            }
            val written = staged.inputStream().use { stream -> fileStore.writeStream(destination, stream) }
            return if (written) null else MochiError.Local(R.string.common_file_save_failed)
        } catch (_: IOException) {
            return MochiError.Local(R.string.common_file_save_failed)
        } finally {
            staged.delete()
        }
    }

    private fun discard(destination: Uri) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, destination)
        } catch (_: Exception) {
            // Not every provider deletes, and a plain file is not a document.
            try {
                if (destination.scheme == "file") destination.path?.let { path -> java.io.File(path).delete() }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun show(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /** Remembers a failed read, which the copy it feeds reports only as false. */
    private class Watched(source: InputStream) : FilterInputStream(source) {
        var failure: IOException? = null
            private set

        override fun read(): Int = watch { super.read() }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            watch { super.read(buffer, offset, length) }

        private inline fun watch(step: () -> Int): Int = try {
            step()
        } catch (e: IOException) {
            failure = e
            throw e
        }
    }
}

/** A file the user is asked where to save: shown as [name], of [mimeType], and known to the caller by [key]. */
data class DownloadRequest(val key: String, val name: String, val mimeType: String = MIME_BINARY)

/**
 * Remembers a save dialog for one file at a time, whatever its type, and
 * answers the function that opens it. [onPicked] gets the request and the
 * document the user chose; backing out calls nothing. The request survives the
 * activity being recreated while the dialog is open, and the dialog is not
 * opened twice for one request.
 */
@Composable
fun rememberDownloadSaver(onPicked: (DownloadRequest, Uri) -> Unit): (DownloadRequest) -> Unit {
    var pending by rememberSaveable(stateSaver = PendingSaver) { mutableStateOf<Pending?>(null) }
    val picked by rememberUpdatedState(onPicked)
    val launcher = rememberFileSaveLauncher(pending?.request?.mimeType ?: MIME_BINARY) { uri ->
        val request = pending?.request
        pending = null
        if (uri != null && request != null) picked(request, uri)
    }
    LaunchedEffect(pending) {
        val waiting = pending
        if (waiting != null && !waiting.opened) {
            pending = waiting.copy(opened = true)
            launcher.launch(waiting.request.name)
        }
    }
    return { request -> pending = Pending(request, opened = false) }
}

private data class Pending(val request: DownloadRequest, val opened: Boolean)

private val PendingSaver = listSaver<Pending?, Any>(
    save = { pending ->
        if (pending == null) emptyList()
        else listOf(pending.request.key, pending.request.name, pending.request.mimeType, pending.opened)
    },
    restore = { values ->
        if (values.isEmpty()) null
        else Pending(DownloadRequest(values[0] as String, values[1] as String, values[2] as String), values[3] as Boolean)
    },
)
