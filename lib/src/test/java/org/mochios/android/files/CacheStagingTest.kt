// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.files

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * A picked document that cannot be read is answered with null, as the staging
 * calls promise, and leaves nothing in the cache: a provider says a document
 * is gone or no longer permitted by throwing, and a copy can fail part way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CacheStagingTest {
    private val context = RuntimeEnvironment.getApplication()
    private val store = FileStore(context)

    private fun document(name: String, open: () -> InputStream): Uri {
        val uri = Uri.parse("content://org.example.documents/$name")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { open() }
        return uri
    }

    private fun cached(): Set<String> = context.cacheDir.list().orEmpty().toSet()

    /** A stream that gives some bytes and then fails, as a provider cut off mid-read does. */
    private fun broken(): InputStream = object : InputStream() {
        private var given = 0

        override fun read(): Int {
            if (given++ < 10) return 'x'.code
            throw IOException("the provider went away")
        }
    }

    @Test
    fun `a document that is gone is answered with null`() {
        val uri = document("gone.pdf") { throw FileNotFoundException("gone") }
        val before = cached()
        assertNull(runBlocking { store.cacheFile(uri, "gone") })
        assertEquals(before, cached())
    }

    @Test
    fun `a document no longer permitted is answered with null`() {
        val uri = document("revoked.pdf") { throw SecurityException("permission revoked") }
        assertNull(runBlocking { store.cacheFile(uri, "revoked") })
    }

    @Test
    fun `a copy cut short leaves nothing behind`() {
        val uri = document("partial.pdf") { broken() }
        val before = cached()
        assertNull(runBlocking { store.cacheFile(uri, "partial") })
        assertEquals("the partial copy is deleted", before, cached())
    }

    @Test
    fun `a set with one unreadable document stages nothing and says so`() {
        val good = document("good.txt") { ByteArrayInputStream("hello".toByteArray()) }
        val gone = document("gone.txt") { throw FileNotFoundException("gone") }
        assertNotNull("the readable one alone stages", runBlocking { store.cacheFile(good, "good") }?.also { it.delete() })
        val before = cached()
        try {
            runBlocking { store.cacheFiles(listOf(good, gone)) }
            fail("an unreadable document must not be dropped silently")
        } catch (_: IOException) {
        }
        assertEquals("what was staged first is deleted", before, cached())
    }
}
