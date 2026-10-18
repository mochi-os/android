// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An upload's progress as a screen sees it: files joining the upload as their
 * parts are built, their bytes counted as the request is written, and the
 * upload gone once the send ends.
 */
class UploadTest {

    private val type = "application/octet-stream".toMediaType()

    private fun bytes(size: Int) = ByteArray(size) { index -> (index % 251).toByte() }

    private fun counted(tracker: UploadTracker, name: String, data: ByteArray) =
        CountedBody(data.toRequestBody(type), tracker.add(name, data.size.toLong()))

    @Test
    fun `a tracked send shows each file and its bytes, then clears`() = runBlocking {
        val tracker = UploadTracker()
        val first = bytes(200_000)
        val second = bytes(5_000)
        tracker.track {
            assertTrue(tracker.state.value!!.preparing)
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("body", "hello")
                .addFormDataPart("files", "a.bin", counted(tracker, "a.bin", first))
                .addFormDataPart("files", "b.bin", counted(tracker, "b.bin", second))
                .build()
            val before = tracker.state.value!!
            assertEquals(listOf("a.bin", "b.bin"), before.files.map { file -> file.name })
            assertEquals(205_000L, before.total)
            assertEquals(0L, before.sent)
            assertFalse(before.processing)

            body.writeTo(Buffer())

            val after = tracker.state.value!!
            assertEquals(listOf(200_000L, 5_000L), after.files.map { file -> file.sent })
            assertTrue(after.processing)
            assertEquals(1f, after.fraction)
        }
        assertNull(tracker.state.value)
    }

    @Test
    fun `expected files count toward the total before their requests are built`() = runBlocking {
        val tracker = UploadTracker()
        val first = java.io.File.createTempFile("first", ".bin").apply { writeBytes(bytes(3_000)) }
        val second = java.io.File.createTempFile("second", ".bin").apply { writeBytes(bytes(7_000)) }
        try {
            tracker.track {
                tracker.expect(listOf(first, second))
                assertEquals(10_000L, tracker.state.value!!.total)
                counted(tracker, first.name, first.readBytes()).writeTo(Buffer())
                val upload = tracker.state.value!!
                assertEquals(2, upload.files.size)
                assertEquals(listOf(3_000L, 0L), upload.files.map { file -> file.sent })
                assertFalse(upload.processing)
            }
        } finally {
            first.delete()
            second.delete()
        }
    }

    @Test
    fun `the upload clears when the send fails`() = runBlocking {
        val tracker = UploadTracker()
        runCatching {
            tracker.track {
                counted(tracker, "a.bin", bytes(10))
                error("refused")
            }
        }
        assertNull(tracker.state.value)
    }

    @Test
    fun `a send with no files shows no upload`() = runBlocking {
        val tracker = UploadTracker()
        tracker.track(emptyList<Any>()) {
            assertNull(tracker.state.value)
        }
        tracker.track(listOf("picked")) {
            assertNotNull(tracker.state.value)
        }
        assertNull(tracker.state.value)
    }

    @Test
    fun `a part built outside a send is not shown`() {
        val tracker = UploadTracker()
        val body = counted(tracker, "a.bin", bytes(1_000))
        body.writeTo(Buffer())
        assertNull(tracker.state.value)
    }

    @Test
    fun `counting leaves the bytes sent unchanged`() {
        val data = bytes(150_000)
        val plain = Buffer().also { buffer -> data.toRequestBody(type).writeTo(buffer) }
        val counted = Buffer().also { buffer ->
            CountedBody(data.toRequestBody(type)) {}.writeTo(buffer)
        }
        assertArrayEquals(plain.readByteArray(), counted.readByteArray())
    }

    @Test
    fun `reports come every step and on the last byte`() {
        val data = bytes(200_000)
        val reports = mutableListOf<Long>()
        CountedBody(data.toRequestBody(type)) { sent -> reports += sent }.writeTo(Buffer())
        assertEquals(0L, reports.first())
        assertEquals(200_000L, reports.last())
        assertTrue(reports.zipWithNext().all { (a, b) -> b > a })
        assertTrue(reports.size <= 200_000 / CountedBody.STEP + 2)
    }

    @Test
    fun `a body written again starts back at zero`() {
        val data = bytes(100_000)
        val reports = mutableListOf<Long>()
        val body = CountedBody(data.toRequestBody(type)) { sent -> reports += sent }
        body.writeTo(Buffer())
        val first = reports.size
        body.writeTo(Buffer())
        assertEquals(0L, reports[first])
        assertEquals(100_000L, reports.last())
    }
}
