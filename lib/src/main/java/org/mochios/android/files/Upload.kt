// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/**
 * An upload in flight, as a screen draws it: each file's name and size, and
 * how many of its bytes have left the device. [files] is empty while the
 * picked documents are still being copied into the cache.
 */
data class Upload(val files: List<File> = emptyList()) {

    /** One file of the upload, in the order the request carries them. */
    data class File(val name: String, val size: Long, val sent: Long = 0) {
        val fraction: Float
            get() = if (size > 0) (sent.toFloat() / size).coerceIn(0f, 1f) else 1f
    }

    val sent: Long get() = files.sumOf { file -> file.sent }

    val total: Long get() = files.sumOf { file -> file.size }

    val fraction: Float
        get() = if (total > 0) (sent.toFloat() / total).coerceIn(0f, 1f) else 0f

    /** The files are still being read; no byte has been sent. */
    val preparing: Boolean get() = files.isEmpty()

    /** Every byte has left the device and the server has yet to answer. */
    val processing: Boolean get() = files.isNotEmpty() && sent >= total
}

/**
 * Publishes one screen's upload progress. A view model owns one, runs each
 * upload inside [track], and hands it to the repository, which passes it to
 * [FileStore.filePart] for every file the request carries; the screen draws
 * [state] with `UploadProgress`.
 */
class UploadTracker {
    private val flow = MutableStateFlow<Upload?>(null)
    private val depth = AtomicInteger(0)

    // Files shown by [expect] whose parts are not built yet, by name, to the
    // slot each holds.
    private val expected = ConcurrentHashMap<String, Int>()

    /** The upload in flight, or null when there is none. */
    val state: StateFlow<Upload?> = flow.asStateFlow()

    /** Shows an upload from now until [block] returns, however it ends. */
    suspend fun <T> track(block: suspend () -> T): T {
        if (depth.getAndIncrement() == 0) {
            flow.value = Upload()
        }
        try {
            return block()
        } finally {
            if (depth.decrementAndGet() == 0) {
                expected.clear()
                flow.value = null
            }
        }
    }

    /**
     * [track] when [files] holds anything; otherwise just runs [block], so a
     * comment sent without attachments shows no upload.
     */
    suspend fun <T> track(files: Collection<*>, block: suspend () -> T): T =
        if (files.isEmpty()) block() else track(block)

    /**
     * Shows [files] in the upload before their requests are built, so a screen
     * that sends them one request at a time has the right total from the first
     * byte. Each is claimed by the part later built from a file of its name,
     * which staged files are unique in. Outside [track] it does nothing.
     */
    fun expect(files: List<java.io.File>) {
        if (depth.get() == 0) return
        files.forEach { file -> expected[file.name] = append(file.name, file.length()) }
    }

    /**
     * Adds a file of [size] bytes to the upload in flight and returns what
     * reports its bytes sent. Outside [track] nothing is shown and the
     * reporter does nothing.
     */
    internal fun add(name: String, size: Long): (Long) -> Unit {
        if (depth.get() == 0) return {}
        val slot = expected.remove(name) ?: append(name, size)
        return { sent ->
            flow.update { current ->
                if (current == null || slot >= current.files.size) {
                    current
                } else {
                    current.copy(
                        files = current.files.mapIndexed { position, file ->
                            if (position == slot) file.copy(sent = sent) else file
                        }
                    )
                }
            }
        }
    }

    /** Appends a file to the upload in flight and returns its slot. */
    private fun append(name: String, size: Long): Int {
        var index = -1
        flow.update { current ->
            val upload = current ?: Upload()
            index = upload.files.size
            upload.copy(files = upload.files + Upload.File(name, size))
        }
        return index
    }
}

/**
 * [body] with its bytes counted as OkHttp writes them, reported to [report].
 * A body written again - a retried request - starts back at zero. Reports
 * come at most every [STEP] bytes and on the last byte, which is enough for a
 * bar and keeps a large file from copying the upload's state per segment.
 */
internal class CountedBody(
    private val body: RequestBody,
    private val report: (Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = body.contentType()

    override fun contentLength(): Long = body.contentLength()

    override fun isOneShot(): Boolean = body.isOneShot()

    override fun writeTo(sink: BufferedSink) {
        report(0)
        val counter = Counter(sink)
        val buffered = counter.buffer()
        body.writeTo(buffered)
        // Push what is left into the request without closing it: later parts
        // still have to follow.
        buffered.emit()
        counter.finish()
    }

    private inner class Counter(sink: BufferedSink) : ForwardingSink(sink) {
        private var sent = 0L
        private var reported = 0L

        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            sent += byteCount
            if (sent - reported >= STEP) {
                reported = sent
                report(sent)
            }
        }

        fun finish() {
            if (sent != reported) {
                reported = sent
                report(sent)
            }
        }
    }

    companion object {
        const val STEP = 64L * 1024
    }
}
