// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * What the opener keeps on the device: the files it downloads and stages are
 * bounded by age while an account stays signed in, gone when it signs out,
 * and a file the FileProvider will not serve is a failure to report, not a
 * crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AttachmentCacheTest {
    private val context = RuntimeEnvironment.getApplication()
    private val dir = File(context.cacheDir, "attachments")
    private val day = 24L * 60 * 60 * 1000

    private fun file(path: String, age: Long, now: Long): File =
        File(dir, path).apply {
            parentFile?.mkdirs()
            writeText("x")
            setLastModified(now - age)
        }

    @Test
    fun `files older than a week are pruned, newer ones kept`() {
        val now = System.currentTimeMillis()
        val old = file("old.pdf", 8 * day, now)
        val shared = file("shared/old.pdf", 8 * day, now)
        val fresh = file("fresh.pdf", day, now)
        AttachmentOpener.prune(dir, now)
        assertFalse(old.exists())
        assertFalse("the shared copies too", shared.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `forgetting deletes everything downloaded and staged`() {
        val now = System.currentTimeMillis()
        file("a.pdf", 0, now)
        file("shared/a.pdf", 0, now)
        AttachmentOpener.forget(context)
        assertFalse(dir.exists())
    }

    @Test
    fun `a file the provider refuses fails instead of crashing`() {
        val outside = File(context.filesDir, "outside.pdf").apply { writeText("x") }
        assertEquals(AttachmentOpener.OpenResult.FAILED, AttachmentOpener.launch(context, outside, "application/pdf"))
    }
}
